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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final WsFrameSink frameSink;
    private final int sampleBytes;
    /** 帧明细采集开关 */
    private final boolean frameLogEnabled;
    /** 每帧 payload 样本最大字节数 */
    private final int frameSampleBytes;
    /** 每会话最多记录帧数（超出丢弃，防超长连接刷库） */
    private final int maxFramesPerSession;

    public WsCaptureWebSocketService(WebSocketService delegate, WsSessionSink sink, WsFrameSink frameSink,
                                     int sampleBytes, boolean frameLogEnabled,
                                     int frameSampleBytes, int maxFramesPerSession) {
        this.delegate = delegate;
        this.sink = sink;
        this.frameSink = frameSink;
        this.sampleBytes = sampleBytes;
        this.frameLogEnabled = frameLogEnabled;
        this.frameSampleBytes = frameSampleBytes;
        this.maxFramesPerSession = maxFramesPerSession;
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

        String sessionKey = UUID.randomUUID().toString().replace("-", "");
        builder.sessionKey(sessionKey);

        WebSocketHandler capturing = session -> {
            Recorder recorder = new Recorder(builder, sessionKey, traceId);
            return handler.handle(new CapturingSession(session, recorder))
                    .doFinally(signal -> sink.offer(recorder.finish(signal, start)));
        };
        return delegate.handleRequest(exchange, capturing);
    }

    /** 双向计数、首帧样本与帧明细累积 */
    class Recorder {
        private final WsSessionRecord.WsSessionRecordBuilder builder;
        private final String sessionKey;
        private final String traceId;
        private final AtomicLong inMsg = new AtomicLong();
        private final AtomicLong inBytes = new AtomicLong();
        private final AtomicLong outMsg = new AtomicLong();
        private final AtomicLong outBytes = new AtomicLong();
        /** 已记录帧数（IN+OUT 合计，达到 maxFramesPerSession 后不再记录） */
        private final AtomicInteger frameCount = new AtomicInteger();
        private volatile String inSample;
        private volatile String outSample;

        Recorder(WsSessionRecord.WsSessionRecordBuilder builder, String sessionKey, String traceId) {
            this.builder = builder;
            this.sessionKey = sessionKey;
            this.traceId = traceId;
        }

        void onInbound(WebSocketMessage message) {
            inMsg.incrementAndGet();
            inBytes.addAndGet(message.getPayload().readableByteCount());
            if (inSample == null) {
                inSample = sample(message, sampleBytes, false);
            }
            if (frameLogEnabled) {
                captureFrame("IN", message);
            }
        }

        void onOutbound(WebSocketMessage message) {
            outMsg.incrementAndGet();
            outBytes.addAndGet(message.getPayload().readableByteCount());
            if (outSample == null) {
                outSample = sample(message, sampleBytes, false);
            }
            if (frameLogEnabled) {
                captureFrame("OUT", message);
            }
        }

        /** 帧明细旁路记录：达到每会话上限后丢弃 */
        private void captureFrame(String direction, WebSocketMessage message) {
            if (frameCount.incrementAndGet() > maxFramesPerSession) {
                return;
            }
            boolean binary = message.getType() != WebSocketMessage.Type.TEXT;
            frameSink.offer(WsFrameRecord.builder()
                    .sessionKey(sessionKey)
                    .traceId(traceId)
                    .direction(direction)
                    .msgType(binary ? "BINARY" : "TEXT")
                    .payload(sample(message, frameSampleBytes, binary))
                    .length(message.getPayload().readableByteCount())
                    .recvTime(LocalDateTime.now())
                    .build());
        }

        /**
         * payload 样本。文本帧取前 N 字节 UTF-8；二进制帧：binaryHex=true 时取 hex（帧明细用），
         * 否则（会话首帧样本）仅标记长度。
         */
        private String sample(WebSocketMessage message, int limit, boolean binaryHex) {
            try {
                int n = message.getPayload().readableByteCount();
                if (message.getType() != WebSocketMessage.Type.TEXT && !binaryHex) {
                    return "[binary " + n + " bytes]";
                }
                byte[] bytes = new byte[Math.min(n, limit)];
                message.getPayload().toByteBuffer().get(bytes);
                if (message.getType() != WebSocketMessage.Type.TEXT) {
                    StringBuilder hex = new StringBuilder(bytes.length * 2);
                    for (byte b : bytes) {
                        hex.append(String.format("%02x", b));
                    }
                    return n > limit ? hex + "...(已截断)" : hex.toString();
                }
                String text = new String(bytes, StandardCharsets.UTF_8);
                return n > limit ? text + "...(已截断)" : text;
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
