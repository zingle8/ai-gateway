package com.zimo.gateway.trace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 日志追踪模块启动类
 */
@MapperScan("com.zimo.gateway.trace.mapper")
@SpringBootApplication(scanBasePackages = "com.zimo.gateway")
public class TraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceApplication.class, args);
    }
}
