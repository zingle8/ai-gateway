package com.zimo.gateway.client.web;

import com.zimo.gateway.client.common.constant.GatewayClientConstant;
import com.zimo.gateway.client.common.dto.SpanRecordDTO;
import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.client.common.enums.ErrType;
import com.zimo.gateway.client.properties.GatewayClientProperties;
import com.zimo.gateway.client.support.LocalIps;
import com.zimo.gateway.client.report.TraceReporter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * 调用日志采集过滤器。
 *
 * 采集内容：method / uri / query / 请求响应 Header / 请求响应报文体 /
 * 状态码 / 耗时 / 调用方与实例地址 / 错误类型。
 *
 * 采集完成后交给 {@link TraceReporter} 异步批量上报到网关平台，
 * 任何采集或上报异常都不会影响业务请求。
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayTraceFilter extends OncePerRequestFilter {

    private final GatewayClientProperties properties;
    private final TraceReporter reporter;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!shouldCollect(request)) {
            chain.doFilter(request, response);
            return;
        }

        LocalDateTime reqTime = LocalDateTime.now();
        long start = System.currentTimeMillis();

        // —— 链路上文：优先 traceparent（W3C），兜底旧 X-Gateway-Trace-Id，都没有则本服务是链路根
        TraceContextUtil.Parsed upstream = TraceContextUtil.parse(request.getHeader(GatewayClientConstant.HEADER_TRACEPARENT));
        if (upstream == null) {
            upstream = TraceContextUtil.fromLegacy(request.getHeader(GatewayClientConstant.HEADER_TRACE_ID));
        }
        String traceId = upstream != null ? upstream.traceId()
                : UUID.randomUUID().toString().replace("-", "");
        String parentSpanId = upstream == null ? null : upstream.spanId();
        String serverSpanId = TraceContextUtil.newSpanId();

        // 写入线程上下文：本服务向外发起 HTTP 调用时，拦截器据此生成 CLIENT span 并注入 header
        TraceContext.set(traceId, serverSpanId);

        CachedBodyRequestWrapper wrappedRequest = new CachedBodyRequestWrapper(request);
        CachedBodyResponseWrapper wrappedResponse = new CachedBodyResponseWrapper(response);

        Throwable error = null;
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            TraceContext.clear();
            try {
                reporter.offer(buildLog(wrappedRequest, wrappedResponse, reqTime, start, error, traceId));
                reporter.offerSpan(buildServerSpan(wrappedRequest, wrappedResponse, reqTime, start,
                        error, traceId, parentSpanId, serverSpanId));
            } catch (Exception e) {
                log.debug("采集网关调用日志失败：{}", e.getMessage());
            }
            // 只在正常结束时刷出缓冲。异常路径若提前 flush 会把响应提交成 200，
            // 容器随后就无法再写回 500，业务方的错误码会被吞掉。
            if (error == null) {
                try {
                    wrappedResponse.flushToClient();
                } catch (Exception e) {
                    log.debug("刷出响应缓冲失败：{}", e.getMessage());
                }
            }
        }
    }

    private boolean shouldCollect(HttpServletRequest request) {
        if (!properties.isEnabled() || !properties.getTrace().isEnabled()) {
            return false;
        }
        return !isExcluded(request.getRequestURI()) && sampled();
    }

    private boolean isExcluded(String uri) {
        List<String> excludes = properties.getTrace().getExcludePaths();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        for (String pattern : excludes) {
            if (pattern != null && !pattern.isEmpty() && pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private boolean sampled() {
        double rate = properties.getTrace().getSampleRate();
        return rate >= 1.0d || Math.random() < rate;
    }

    private TraceLogDTO buildLog(CachedBodyRequestWrapper request,
                                 CachedBodyResponseWrapper response,
                                 LocalDateTime reqTime, long start, Throwable error, String traceId) {
        TraceLogDTO dto = new TraceLogDTO();
        dto.setAppId(properties.getAppId());
        dto.setAppName(properties.getAppName());
        dto.setServiceName(properties.getServiceName());
        dto.setApi(request.getMethod() + " " + request.getRequestURI());
        dto.setReqParams(request.getQueryString());
        dto.setProtocol(request.getProtocol());
        dto.setReqFormat(resolveFormat(request.getContentType()));
        dto.setResFormat(resolveFormat(response.getContentType()));
        dto.setReqIp(request.getRemoteAddr());
        dto.setReqPort(request.getRemotePort());
        dto.setResIp(properties.getIp() != null && !properties.getIp().isEmpty()
                ? properties.getIp() : LocalIps.get());
        dto.setResPort(properties.getPort() == null ? request.getServerPort() : properties.getPort());
        // 异常抛出时容器还没执行 sendError，此刻 response.getStatus() 仍是 200，
        // 直接用会把错误调用记成正常调用，这里按异常兜底为 500
        int statusCode = response.getStatus();
        if (error != null && statusCode < 400) {
            statusCode = 500;
        }
        dto.setStatusCode(statusCode);
        dto.setErrType(resolveErrType(error, statusCode));
        dto.setReqTime(reqTime);
        dto.setResTime(LocalDateTime.now());
        dto.setCost((int) (System.currentTimeMillis() - start));
        dto.setReqHeader(headersToString(request));
        dto.setResHeader(headersToString(response));
        dto.setReqBody(readBody(request.getCachedBody(), request.getCharacterEncoding(), maxBody()));
        dto.setResBody(readBody(response.getCachedBody(), response.getCharacterEncoding(), maxBody()));
        dto.setReqBytes((long) request.getCachedBody().length);
        dto.setResBytes((long) response.getCachedBody().length);
        dto.setTraceId(traceId);
        return dto;
    }

    /**
     * 本服务 SERVER span：parent 为上游（网关 CLIENT span）传播来的 spanId，
     * 服务作为链路根（不经网关直接被调用 / 手动触发）时 parent 为 NULL。
     */
    private SpanRecordDTO buildServerSpan(CachedBodyRequestWrapper request,
                                          CachedBodyResponseWrapper response,
                                          LocalDateTime reqTime, long start, Throwable error,
                                          String traceId, String parentSpanId, String spanId) {
        int statusCode = response.getStatus();
        if (error != null && statusCode < 400) {
            statusCode = 500;
        }
        SpanRecordDTO span = new SpanRecordDTO();
        span.setTraceId(traceId);
        span.setSpanId(spanId);
        span.setParentSpanId(parentSpanId);
        span.setKind(SpanRecordDTO.KIND_SERVER);
        span.setAppId(properties.getAppId());
        span.setAppName(properties.getAppName());
        span.setServiceName(properties.getServiceName());
        span.setApi(request.getMethod() + " " + request.getRequestURI());
        span.setStatusCode(statusCode);
        span.setErrType(resolveErrType(error, statusCode));
        span.setCost((int) (System.currentTimeMillis() - start));
        span.setStartTime(reqTime);
        return span;
    }

    private int maxBody() {
        int max = properties.getTrace().getMaxBodySize();
        return max <= 0 ? GatewayClientConstant.DEFAULT_MAX_BODY_SIZE : max;
    }

    private String resolveErrType(Throwable error, int status) {
        if (error == null) {
            return ErrType.resolve(status);
        }
        Throwable cause = error;
        while (cause != null) {
            if (cause.getClass().getName().contains("Timeout")) {
                return ErrType.UPSTREAM_TIMEOUT.getLabel();
            }
            cause = cause.getCause();
        }
        return ErrType.UPSTREAM_ERROR.getLabel();
    }

    private static String resolveFormat(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return "-";
        }
        String ct = contentType.toLowerCase();
        if (ct.contains("json")) {
            return "JSON";
        }
        if (ct.contains("protobuf")) {
            return "Protobuf";
        }
        if (ct.contains("xml")) {
            return "XML";
        }
        if (ct.contains("x-www-form-urlencoded")) {
            return "form";
        }
        if (ct.contains("form-data")) {
            return "form-data";
        }
        if (ct.contains("text/")) {
            return "TEXT";
        }
        return ct.split(";")[0];
    }

    private static String headersToString(HttpServletRequest request) {
        List<String> lines = new ArrayList<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            lines.add(name + ": " + request.getHeader(name));
        }
        return String.join("\n", lines);
    }

    private static String headersToString(HttpServletResponse response) {
        List<String> lines = new ArrayList<>();
        for (String name : response.getHeaderNames()) {
            lines.add(name + ": " + response.getHeader(name));
        }
        return String.join("\n", lines);
    }

    private static String readBody(byte[] body, String encoding, int maxBodySize) {
        if (body == null || body.length == 0) {
            return "";
        }
        Charset charset = StandardCharsets.UTF_8;
        if (encoding != null && !encoding.isEmpty()) {
            try {
                charset = Charset.forName(encoding);
            } catch (Exception ignored) {
                // 回落 UTF-8
            }
        }
        String text = new String(body, charset);
        if (text.length() > maxBodySize) {
            return text.substring(0, maxBodySize) + "...[截断，共 " + text.length() + " 字符]";
        }
        return text;
    }
}
