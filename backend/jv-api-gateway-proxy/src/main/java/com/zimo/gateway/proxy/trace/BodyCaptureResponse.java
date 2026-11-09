package com.zimo.gateway.proxy.trace;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 响应体旁路捕获：边转发边缓存前 N 字节，不改变原始数据流。
 *
 * <p>只缓存不重写，避免像 SDK 早期版本那样把响应体写两遍。
 */
public class BodyCaptureResponse extends ServerHttpResponseDecorator {

    private final int maxBytes;
    private final ByteArrayOutputStream cache = new ByteArrayOutputStream();
    private final Charset charset;
    private boolean truncated;
    private long totalBytes;

    public BodyCaptureResponse(ServerHttpResponse delegate, int maxBytes, Charset charset) {
        super(delegate);
        this.maxBytes = maxBytes;
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return super.writeWith(Flux.from(body).doOnNext(this::capture));
    }

    /**
     * 流式响应（SSE / text/event-stream / 分块流）走 writeAndFlushWith：每个 chunk 写完立即 flush，
     * 不经过 writeWith。同样做旁路捕获，否则 SSE 响应体在追踪日志里为空。
     */
    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return super.writeAndFlushWith(Flux.from(body)
                .map(inner -> Flux.from(inner).doOnNext(this::capture)));
    }

    private void capture(DataBuffer buffer) {
        int readable = buffer.readableByteCount();
        totalBytes += readable;
        if (cache.size() >= maxBytes) {
            truncated = true;
            return;
        }
        // toByteBuffer 返回副本，不消耗原缓冲区
        ByteBuffer copy = buffer.toByteBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        int allow = Math.min(bytes.length, maxBytes - cache.size());
        cache.write(bytes, 0, allow);
        if (allow < bytes.length) {
            truncated = true;
        }
    }

    /** 已捕获的响应体文本；超长会带截断标记 */
    public String capturedBody() {
        if (cache.size() == 0) {
            return null;
        }
        String text = cache.toString(charset);
        return truncated ? text + "...(已截断，共 " + totalBytes + " 字节)" : text;
    }

    public long totalBytes() {
        return totalBytes;
    }
}
