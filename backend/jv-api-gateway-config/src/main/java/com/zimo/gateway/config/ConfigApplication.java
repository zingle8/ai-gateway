package com.zimo.gateway.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统配置服务：平台级配置的统一存储与实时下发。
 *
 * <p>其他模块（proxy 鉴权缓存、前端菜单健康标识、开放认证开关）以短周期轮询
 * 本服务的配置表实现"改完即生效"，避免引入重量级配置中心。
 */
@SpringBootApplication(scanBasePackages = "com.zimo.gateway")
@MapperScan("com.zimo.gateway.config.mapper")
public class ConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigApplication.class, args);
    }
}
