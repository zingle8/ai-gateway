package com.zimo.gateway.proxy.trace;

import io.netty.handler.codec.http.HttpVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 调用采集：记录每一次经网关转发到上游的请求与响应。
 *
 * <p>三个约束：
 * <ul>
 *   <li>不能改变数据流（只旁路复制，不重写响应体）</li>
 *   <li>不能阻塞 Netty 线程（写库交给 TraceSink 异步批量）</li>
 *   <li>报文按 max-body-size 截断，避免大文件把内存吃满</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceGlobalFilter implements GlobalFilter, Ordered {

    /** 需早于 NettyRoutingFilter（Integer.MAX_VALUE），才能拿到完整的转发结果 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /** reactor-netty 原生对象的 version() 方法缓存（该类非 public，只能反射） */
    private static final AtomicReference<Method> VERSION_METHOD = new AtomicReference<>();

    private final TraceSink traceSink;

    @Value("${gateway.proxy.max-body-size:8192}")
    private int maxBodySize;

    /** 网关自身在链路中的服务名（SERVER/CLIENT span 的 serviceName） */
    @Value("${spring.application.name:jv-gateway-proxy}")
    private String gatewayServiceName;

    /**
     * SDK 上报链路自身不采集：否则心跳/批量上报会产生
     * 「记录上报的记录」的自引用噪音，且无限滚动。
     */
    private static final java.util.List<String> SKIP_PATHS = java.util.List.of(
            "/api/trace/logs/batch",
            "/api/trace/spans/batch",
            "/api/discovery/services/register",
            "/api/discovery/services/heartbeat",
            "/api/discovery/services/deregister");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (SKIP_PATHS.contains(path)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        LocalDateTime reqTime = LocalDateTime.now();
        long start = System.currentTimeMillis();

        // —— 链路上下文：优先继承上游（W3C traceparent），兼容旧 X-Gateway-Trace-Id；都没有则本请求是链路根
        TraceContextUtil.Parsed upstream = TraceContextUtil.parse(request.getHeaders().getFirst("traceparent"));
        String legacyTraceId = request.getHeaders().getFirst("X-Gateway-Trace-Id");
        String traceId = upstream != null ? upstream.traceId()
                : (legacyTraceId == null || legacyTraceId.isEmpty()
                        ? UUID.randomUUID().toString().replace("-", "") : legacyTraceId);
        String parentSpanId = upstream == null ? null : upstream.spanId();
        String rootSpanId = TraceContextUtil.newSpanId();      // 网关 SERVER span
        String clientSpanId = TraceContextUtil.newSpanId();    // 网关 → 下游的 CLIENT span

        String reqContentType = request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        boolean textualRequest = isTextual(reqContentType);

        // 请求体旁路复制；字节数按真实流经的 buffer 累计（非文本请求也要记）
        StringBuilder reqBody = new StringBuilder();
        AtomicLong reqBytes = new AtomicLong();
        ServerHttpRequestDecorator requestDecorator = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(buffer -> {
                    reqBytes.addAndGet(buffer.readableByteCount());
                    if (textualRequest) {
                        append(buffer, reqBody);
                    }
                });
            }
        };

        Charset respCharset = charsetOf(exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        BodyCaptureResponse responseDecorator =
                new BodyCaptureResponse(exchange.getResponse(), maxBodySize, respCharset);

        // 转发请求携带链路上文：traceparent（W3C 标准，供新 SDK / OTel 兼容）+ 平台自有 header（供旧 SDK）
        // X-Gateway-Served-By：下游 SDK 的 CLIENT span 据此把 peerService 记成服务名（而非 IP:port），拓扑图节点才能与服务发现对齐
        ServerHttpRequest propagated = requestDecorator.mutate()
                .header("traceparent", TraceContextUtil.format(traceId, clientSpanId))
                .header("X-Gateway-Trace-Id", traceId)
                .header("X-Gateway-Served-By", gatewayServiceName)
                .build();

        ServerWebExchange mutated = exchange.mutate()
                .request(propagated)
                .response(responseDecorator)
                .build();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        return chain.filter(mutated)
                .doOnError(errorRef::set)
                .doFinally(signal -> {
                    try {
                        TraceRecord record = buildRecord(mutated, responseDecorator, traceId,
                                reqTime, start, reqBody.toString(), reqBytes.get(),
                                errorRef.get(), signal);
                        traceSink.offer(record);
                        // 一跳两个 span：SERVER（调用方→网关，parent=上游 span）+ CLIENT（网关→下游服务）
                        traceSink.offerSpan(buildServerSpan(record, traceId, parentSpanId, rootSpanId, reqTime));
                        traceSink.offerSpan(buildClientSpan(record, traceId, rootSpanId, clientSpanId, reqTime));
                    } catch (Exception e) {
                        // 采集失败绝不能影响转发结果
                        log.debug("[采集] 构造记录失败：{}", e.getMessage());
                    }
                });
    }

    private TraceRecord buildRecord(ServerWebExchange exchange,
                                    BodyCaptureResponse response,
                                    String traceId,
                                    LocalDateTime reqTime,
                                    long start,
                                    String reqBody,
                                    long reqBodyBytes,
                                    Throwable error,
                                    SignalType signal) {
        ServerHttpRequest request = exchange.getRequest();
        URI upstream = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        Map<String, Object> metadata = route == null ? Map.of() : route.getMetadata();

        HttpStatusCode statusCode = response.getStatusCode();
        int code;
        if (statusCode != null) {
            code = statusCode.value();
        } else if (error != null || signal == SignalType.ON_ERROR) {
            code = 502;  // 上游不可达
        } else {
            code = 0;
        }

        String errType = error != null ? "上游不可达" : code >= 500 ? "上游响应异常" : code >= 400 ? "客户端错误" : "无";

        String respContentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        String resBody = isTextual(respContentType) ? response.capturedBody() : null;

        InetSocketAddress remote = request.getRemoteAddress();

        return TraceRecord.builder()
                .appId(str(metadata.get("appId")))
                .appName(str(metadata.get("appName")))
                .serviceName(str(metadata.get("serviceName")))
                .api(request.getMethod().name() + " " + request.getURI().getRawPath())
                .reqParams(truncate(request.getURI().getRawQuery()))
                .traceId(traceId)
                .protocol(resolveProtocol(request))
                .reqFormat(formatOf(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)))
                .resFormat(formatOf(respContentType))
                .reqIp(remote == null ? null : remote.getAddress().getHostAddress())
                .reqPort(remote == null ? null : remote.getPort())
                .resIp(upstream == null ? null : upstream.getHost())
                .resPort(upstream == null || upstream.getPort() < 0 ? null : upstream.getPort())
                .statusCode(code)
                .errType(errType)
                .cost(System.currentTimeMillis() - start)
                .reqTime(reqTime)
                .resTime(LocalDateTime.now())
                .reqHeader(serializeHeaders(request.getHeaders()))
                .resHeader(serializeHeaders(response.getHeaders()))
                .reqBody(truncate(reqBody))
                .resBody(truncate(resBody))
                .reqBytes(reqBodyBytes)
                .resBytes(response.totalBytes())
                .build();
    }

    // ------------------------------------------------------------------ 工具方法

    /** 网关 SERVER span：调用方 → 网关，parent 为上游传播来的 span（链路根为 NULL） */
    private SpanRecord buildServerSpan(TraceRecord record, String traceId,
                                       String parentSpanId, String spanId, LocalDateTime startTime) {
        return SpanRecord.builder()
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId(parentSpanId)
                .kind(SpanRecord.KIND_SERVER)
                .appId(record.getAppId())
                .appName(record.getAppName())
                .serviceName(gatewayServiceName)
                .api(record.getApi())
                .statusCode(record.getStatusCode())
                .errType(record.getErrType())
                .cost(record.getCost() == null ? null : record.getCost().intValue())
                .startTime(startTime)
                .build();
    }

    /** 网关 CLIENT span：网关 → 下游服务，peerService 是服务拓扑（调用对）的聚合依据 */
    private SpanRecord buildClientSpan(TraceRecord record, String traceId,
                                       String parentSpanId, String spanId, LocalDateTime startTime) {
        return SpanRecord.builder()
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId(parentSpanId)
                .kind(SpanRecord.KIND_CLIENT)
                .appId(record.getAppId())
                .appName(record.getAppName())
                .serviceName(gatewayServiceName)
                .api(record.getApi())
                .peerService(record.getServiceName())
                .statusCode(record.getStatusCode())
                .errType(record.getErrType())
                .cost(record.getCost() == null ? null : record.getCost().intValue())
                .startTime(startTime)
                .build();
    }

    /**
     * 取客户端到网关的真实协议版本，如 HTTP/1.1、HTTP/2。
     *
     * <p>WebFlux 的 {@link ServerHttpRequest} 不暴露协议版本，需一路解到 reactor-netty 的原生对象
     * （{@code getNativeRequest()} 返回 {@code HttpServerOperations}，它不是 public 类），
     * 再反射读其 {@code version()}。注意链路中请求被 {@link ServerHttpRequestDecorator} 包裹过
     * （网关改写路径、本类读请求体），必须先沿 delegate 链解包，否则拿不到底层对象。
     *
     * <p>取不到时回退 HTTP/1.1，保证采集永不失败。
     */
    private String resolveProtocol(ServerHttpRequest request) {
        try {
            ServerHttpRequest underlying = request;
            while (underlying instanceof ServerHttpRequestDecorator decorator) {
                underlying = decorator.getDelegate();
            }
            if (underlying instanceof AbstractServerHttpRequest nativeRequest) {
                Object operations = nativeRequest.getNativeRequest();
                if (operations != null) {
                    Object version = versionMethodOf(operations).invoke(operations);
                    if (version instanceof HttpVersion nettyVersion) {
                        String text = nettyVersion.text();
                        // Netty 返回 HTTP/2.0，采集侧统一记 HTTP/2
                        return text.startsWith("HTTP/2") ? "HTTP/2" : text;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[采集] 协议版本获取失败：{}", e.getMessage());
        }
        return "HTTP/1.1";
    }

    /** 反射结果缓存：version() 每类只查一次，避免每个请求都做方法查找 */
    private static Method versionMethodOf(Object operations) throws NoSuchMethodException {
        Method cached = VERSION_METHOD.get();
        if (cached == null) {
            cached = operations.getClass().getMethod("version");
            cached.setAccessible(true);
            VERSION_METHOD.set(cached);
        }
        return cached;
    }

    private void append(DataBuffer buffer, StringBuilder target) {
        if (target.length() >= maxBodySize) {
            return;
        }
        ByteBuffer copy = buffer.toByteBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        int allow = Math.min(bytes.length, maxBodySize - target.length());
        target.append(new String(bytes, 0, allow, StandardCharsets.UTF_8));
    }

    private String truncate(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return text.length() <= maxBodySize ? text : text.substring(0, maxBodySize) + "...(已截断)";
    }

    private String serializeHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(String.join(",", entry.getValue())).append('\n');
            if (sb.length() > 4096) {
                sb.append("...(已截断)");
                break;
            }
        }
        return sb.toString();
    }

    private boolean isTextual(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return lower.contains("json") || lower.contains("xml") || lower.contains("text")
                || lower.contains("form-urlencoded") || lower.contains("javascript");
    }

    /** 把 Content-Type 归成 JSON / XML / 表单 / 文本 / 二进制 / 无 */
    private String formatOf(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "无";
        }
        String lower = contentType.toLowerCase();
        if (lower.contains("json")) {
            return "JSON";
        }
        if (lower.contains("xml")) {
            return "XML";
        }
        if (lower.contains("form")) {
            return "表单";
        }
        if (lower.contains("text")) {
            return "文本";
        }
        return "二进制";
    }

    private Charset charsetOf(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String trimmed = part.trim();
                if (trimmed.toLowerCase().startsWith("charset=")) {
                    try {
                        return Charset.forName(trimmed.substring("charset=".length()));
                    } catch (Exception ignored) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
