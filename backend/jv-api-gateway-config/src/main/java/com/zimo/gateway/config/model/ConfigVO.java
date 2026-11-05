package com.zimo.gateway.config.model;

import com.zimo.gateway.config.entity.SysConfigEntity;

import java.time.LocalDateTime;

/**
 * 配置项视图（含来源与归属，控制台据此显示"这个配置在哪、属于谁"）
 *
 * @param key          配置键
 * @param value        当前值（来源不可达时为最后写入快照）
 * @param valueType    BOOLEAN / STRING / NUMBER
 * @param source       DB / REDIS / NACOS
 * @param sourceRef    来源定位（Redis Hash 键、Nacos dataId|groupName）
 * @param sourceLabel  人类可读的来源描述，如 "Redis · 127.0.0.1:6379/0 · gw:config:platform"
 * @param platformName 所属平台
 * @param appName      所属应用
 * @param serviceName  所属服务
 * @param remark       说明
 * @param valueState   OK / STALE（远端不可达，展示快照）
 * @param updateTime   最后写入时间
 */
public record ConfigVO(String key, String value, String valueType,
                       String source, String sourceRef, String sourceLabel,
                       String platformName, String appName, String serviceName,
                       String remark, String valueState, LocalDateTime updateTime) {

    public static ConfigVO of(SysConfigEntity c, String value, String sourceLabel, String valueState) {
        return new ConfigVO(c.getConfigKey(), value, c.getValueType(),
                c.getConfigSource(), c.getSourceRef(), sourceLabel,
                c.getPlatformName(), c.getAppName(), c.getServiceName(),
                c.getRemark(), valueState, c.getUpdateTime());
    }
}
