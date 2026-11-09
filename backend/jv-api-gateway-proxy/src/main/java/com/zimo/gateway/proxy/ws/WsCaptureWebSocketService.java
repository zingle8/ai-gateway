package com.zimo.gateway.proxy.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * WebSocket 转发服务装饰器：包装 Spring Cloud Gateway 默认的 HandshakeWebSocketService。
 *
 * <p>SCG 的 WebsocketRoutingFilter 把「客户端↔网关」入站会话交给 ProxyWebSocketHandler
 * 做双向消息泵。本类在交给它之前用 {@link CapturingSession} 包装会话：
 * <ul>
 *   <li>receive()（客户端→网关→下游）：旁路统计消息数/字节数并保存首帧样本</li>
 *   <li>send()（下游→网关→客户端）：同样旁路捕获</li>
 *   <li>连接结束：会话级记录（起止时间/时长/关闭状态）经 {@link WsSessionSink} 异步落库</li>
 * </ul>
 * 纯旁路观察，不修改、不缓冲消息本身，转发行为与原网关完全一致。
 */
@Slf4j
public class WsCaptureWebSocketService implements WebSocketService {

    private final WebSocketService delegate;
    private final WsSessionSink sink;
    private final int sampleBytes;

    public WsCaptureWebSocketService(WebSocketService delegate, WsSessionSink sink, int sampleBytes) {
        this.delegate = delegate;
        this.sink = sink;
        this.sampleBytes = sampleBytes;
    }

    @Override
    public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
        ServerHttpRequest request = exchange.getRequest();
        LocalDateTime start = LocalDateTime.now();

        // traceId：TraceGlobalFilter 已把 X-Gateway-Trace-Id 注入转发请求头
        String traceId = request.getHeaders().getFirst("X-Gateway-Trace-Id");
        // 上游地址：WebsocketRoutingFilter 已把 GATEWAY_REQUEST_URL_ATTR 改写为 ws://
        URI upstream = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        InetSocketAddress remote = request.getRemoteAddress();

        WsSessionRecord.WsSessionRecordBuilder builder = WsSessionRecord.builder()
                .traceId(traceId)
                .requestPath(String.valueOf(request.getURI()))
                .upstreamUrl(upstream == null ? null : String.valueOf(upstream))
                .clientIp(remote == null ? null : remote.getAddress().getHostAddress())
                .connStart(start);

        WebSocketHandler capturing = session -> {
            Recorder recorder = new Recorder(builder);
            return handler.handle(new CapturingSession(session, recorder))
                    .doFinally(signal -> sink.offer(recorder.finish(signal, start)));
        };
        return delegate.handleRequest(exchange, capturing);
    }

    /** 双向计数与首帧样本累积 */
    class Recorder {
        private final WsSessionRecord.WsSessionRecordBuilder builder;
        private final AtomicLong inMsg = new AtomicLong();
        private final AtomicLong inBytes = new AtomicLong();
        private final AtomicLong outMsg = new AtomicLong();
        private final AtomicLong outBytes = new AtomicLong();
        private volatile String inSample;
        private volatile String outSample;

        Recorder(WsSessionRecord.WsSessionRecordBuilder builder) {
            this.builder = builder;
        }

        void onInbound(WebSocketMessage message) {
            inMsg.incrementAndGet();
            inBytes.addAndGet(message.getPayload().readableByteCount());
            if (inSample == null) {
                inSample = sample(message);
            }
        }

        void onOutbound(WebSocketMessage message) {
            outMsg.incrementAndGet();
            outBytes.addAndGet(message.getPayload().readableByteCount());
            if (outSample == null) {
                outSample = sample(message);
            }
        }

        /** 首帧样本：文本帧按 UTF-8 取前 N 字节，二进制帧标记 [binary n bytes] */
        private String sample(WebSocketMessage message) {
            try {
                if (message.getType() != WebSocketMessage.Type.TEXT) {
                    return "[binary " + message.getPayload().readableByteCount() + " bytes]";
                }
                byte[] bytes = new byte[Math.min(message.getPayload().readableByteCount(), sampleBytes)];
                message.getPayload().toByteBuffer().get(bytes);
                String text = new String(bytes, StandardCharsets.UTF_8);
                return bytes.length >= sampleBytes ? text + "...(已截断)" : text;
            } catch (Exception e) {
                return "[样本读取失败: " + e.getMessage() + "]";
            }
        }

        WsSessionRecord finish(SignalType signal, LocalDateTime start) {
            LocalDateTime end = LocalDateTime.now();
            boolean normal = signal == SignalType.ON_COMPLETE;
            return builder
                    .connEnd(end)
                    .durationMs(Duration.between(start, end).toMillis())
                    .inMsgCount((int) inMsg.get())
                    .inBytes(inBytes.get())
                    .outMsgCount((int) outMsg.get())
                    .outBytes(outBytes.get())
                    .inSample(truncate(inSample))
                    .outSample(truncate(outSample))
                    .closeStatus(normal ? "NORMAL" : "ABNORMAL(" + signal + ")")
                    .build();
        }

        private String truncate(String s) {
            if (s == null) {
                return null;
            }
            return s.length() <= 8000 ? s : s.substring(0, 8000);
        }
    }
}
