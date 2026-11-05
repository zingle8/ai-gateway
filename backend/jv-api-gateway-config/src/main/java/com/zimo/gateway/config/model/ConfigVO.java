package com.zimo.gateway.config.model;

import com.zimo.gateway.config.entity.SysConfigEntity;

import java.time.LocalDateTime;

/**
 * 配置项视图
 */
public record ConfigVO(String key, String value, String valueType, String remark, LocalDateTime updateTime) {

    public static ConfigVO of(SysConfigEntity c) {
        return new ConfigVO(c.getConfigKey(), c.getConfigValue(), c.getValueType(), c.getRemark(), c.getUpdateTime());
    }
}
