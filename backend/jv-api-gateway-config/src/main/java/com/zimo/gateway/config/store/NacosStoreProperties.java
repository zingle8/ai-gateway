package com.zimo.gateway.config.store;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nacos 配置中心连接参数（网关平台配置的 NACOS 来源用）。
 *
 * <p>只用于"平台配置托管在 Nacos"这一场景，与各服务自身的
 * spring.cloud.nacos.config 互不影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.config-store.nacos")
public class NacosStoreProperties {

    /** 服务地址，如 127.0.0.1:8848（注意不是控制台 8080） */
    private String serverAddr = "127.0.0.1:8848";

    private String username = "nacos";

    private String password = "nacos";

    /** 命名空间 */
    private String namespace = "public";

    /** 默认分组 */
    private String group = "DEFAULT_GROUP";

    /** 默认 dataId（properties 文本，一个文件存多个 key） */
    private String dataId = "gw-platform-config.properties";

    /** 读超时（毫秒） */
    private int timeoutMs = 3000;
}
