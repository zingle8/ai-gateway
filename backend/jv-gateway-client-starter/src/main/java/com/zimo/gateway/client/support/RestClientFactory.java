package com.zimo.gateway.client.support;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 网关 SDK 内部使用的 RestClient 构造器。
 *
 * <p>这里必须显式做两件事，否则接入会「假死」：</p>
 * <ol>
 *   <li><b>固定 HTTP/1.1</b>：JDK HttpClient 默认优先 HTTP/2，对 http:// 地址会先发 h2c 升级请求。
 *       遇到只处理 HTTP/1.1 的代理（前端 dev server、部分 Nginx 配置）时，升级请求得不到响应，
 *       整个调用会无限等待。</li>
 *   <li><b>设置连接与读取超时</b>：不设超时的话，平台不可达时注册会永久阻塞，
 *       把业务应用的启动一起拖住。</li>
 * </ol>
 */
public final class RestClientFactory {

    /** 连接超时：平台同机房，3 秒足够 */
    private static final long CONNECT_TIMEOUT_MS = 3000L;

    /** 默认读取超时 */
    private static final long DEFAULT_READ_TIMEOUT_MS = 5000L;

    private RestClientFactory() {
    }

    public static RestClient create(String baseUrl) {
        return create(baseUrl, DEFAULT_READ_TIMEOUT_MS);
    }

    public static RestClient create(String baseUrl, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(Math.max(1L, readTimeoutMs)));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
