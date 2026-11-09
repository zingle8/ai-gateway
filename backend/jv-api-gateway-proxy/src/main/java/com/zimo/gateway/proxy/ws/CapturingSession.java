package com.zimo.gateway.proxy.ws;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

/**
 * WebSocketSession 包装：只覆盖 receive()/send() 做旁路捕获，其余全部委托原会话。
 */
class CapturingSession implements WebSocketSession {

    private final WebSocketSession delegate;
    private final WsCaptureWebSocketService.Recorder recorder;

    CapturingSession(WebSocketSession delegate, WsCaptureWebSocketService.Recorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public Flux<WebSocketMessage> receive() {
        return delegate.receive().doOnNext(recorder::onInbound);
    }

    @Override
    public Mono<Void> send(org.reactivestreams.Publisher<WebSocketMessage> messages) {
        return delegate.send(Flux.from(messages).doOnNext(recorder::onOutbound));
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public HandshakeInfo getHandshakeInfo() {
        return delegate.getHandshakeInfo();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public WebSocketMessage textMessage(String payload) {
        return delegate.textMessage(payload);
    }

    @Override
    public WebSocketMessage binaryMessage(Function<DataBufferFactory, DataBuffer> factory) {
        return delegate.binaryMessage(factory);
    }

    @Override
    public WebSocketMessage pingMessage(Function<DataBufferFactory, DataBuffer> factory) {
        return delegate.pingMessage(factory);
    }

    @Override
    public WebSocketMessage pongMessage(Function<DataBufferFactory, DataBuffer> factory) {
        return delegate.pongMessage(factory);
    }

    @Override
    public Mono<Void> close(CloseStatus status) {
        return delegate.close(status);
    }

    @Override
    public Mono<CloseStatus> closeStatus() {
        return delegate.closeStatus();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public DataBufferFactory bufferFactory() {
        return delegate.bufferFactory();
    }
}
