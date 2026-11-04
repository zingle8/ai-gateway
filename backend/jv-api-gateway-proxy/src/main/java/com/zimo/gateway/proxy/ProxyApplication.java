package com.zimo.gateway.proxy;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 流量代理启动类。
 *
 * <p>与其余 4 个微服务不同，这里跑的是 WebFlux（响应式）栈，
 * 因此进程内没有 Servlet 容器，也不能依赖 jv-api-gateway-common。
 */
@EnableScheduling
@MapperScan("com.zimo.gateway.proxy.mapper")
@SpringBootApplication
public class ProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProxyApplication.class, args);
    }
}
