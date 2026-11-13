package com.zimo.gateway.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务：风控的 API Key 调用巡检（每分钟一次）。
 *
 * <p>user 服务此前没有定时任务，这里只做开关；真正的调度中心（清理 / 分区预建）
 * 在独立模块 jv-api-gateway-scheduler，二者互不影响。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
