package com.zimo.gateway.discovery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 服务发现模块启动类
 */
@EnableScheduling
@MapperScan("com.zimo.gateway.discovery.mapper")
@SpringBootApplication(scanBasePackages = "com.zimo.gateway")
public class DiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryApplication.class, args);
    }
}
