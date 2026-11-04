package com.zimo.gateway.trace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 日志追踪模块启动类
 *
 * <p>@EnableScheduling：PartitionMaintainer（分区滚动）与 EdgeAggregator（拓扑聚合）依赖定时调度。
 */
@MapperScan("com.zimo.gateway.trace.mapper")
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.zimo.gateway")
public class TraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceApplication.class, args);
    }
}
