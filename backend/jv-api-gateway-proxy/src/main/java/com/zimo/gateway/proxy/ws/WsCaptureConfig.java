package com.zimo.gateway.proxy.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;

/**
 * WebSocket 采集装配。
 *
 * <p>用 @Primary 自定义 WebSocketService 覆盖 SCG 默认的 HandshakeWebSocketService：
 * WebsocketRoutingFilter 注入本装饰器，握手/转发行为不变，仅增加旁路捕获。
 */
@Configuration
public class WsCaptureConfig {

    @Bean
    @Primary
    public WebSocketService wsCaptureWebSocketService(RequestUpgradeStrategy upgradeStrategy,
                                                      WsSessionSink sink,
                                                      WsFrameSink frameSink,
                                                      @Value("${gateway.trace.ws-sample-bytes:2048}") int sampleBytes,
                                                      @Value("${gateway.trace.ws-frame-log:true}") boolean frameLogEnabled,
                                                      @Value("${gateway.trace.ws-frame-sample-bytes:1024}") int frameSampleBytes,
                                                      @Value("${gateway.trace.ws-frame-max-per-session:200}") int maxFramesPerSession) {
        return new WsCaptureWebSocketService(new HandshakeWebSocketService(upgradeStrategy), sink, frameSink,
                sampleBytes, frameLogEnabled, frameSampleBytes, maxFramesPerSession);
    }
}
