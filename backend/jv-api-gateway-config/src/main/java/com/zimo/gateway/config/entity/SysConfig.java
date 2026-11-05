package com.zimo.gateway.config.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台级配置项
 */
@Data
public class SysConfig {

    private Long id;

    /** 配置键，如 platform.auth.cache.mode */
    private String configKey;

    private String configValue;

    /** BOOLEAN / STRING / NUMBER */
    private String valueType;

    private String remark;

    private LocalDateTime updateTime;
}
