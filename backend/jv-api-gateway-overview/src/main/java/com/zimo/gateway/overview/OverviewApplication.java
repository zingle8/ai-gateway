package com.zimo.gateway.overview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关总览模块启动类
 */
@MapperScan("com.zimo.gateway.overview.mapper")
@SpringBootApplication(scanBasePackages = "com.zimo.gateway")
public class OverviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(OverviewApplication.class, args);
    }
}
