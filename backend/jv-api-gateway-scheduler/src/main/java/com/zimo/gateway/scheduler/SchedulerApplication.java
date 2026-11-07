package com.zimo.gateway.scheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 分布式调度中心启动类
 *
 * 职责：历史数据清理（按调度时间每日一次）、分区表预建（提前 2 天）、
 * 调度计划可视化数据支撑。多实例部署时通过分布式锁保证任务单实例执行。
 */
@EnableScheduling
@MapperScan("com.zimo.gateway.scheduler.mapper")
@SpringBootApplication(scanBasePackages = "com.zimo.gateway")
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }
}
