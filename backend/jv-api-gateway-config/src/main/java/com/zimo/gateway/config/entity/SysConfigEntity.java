package com.zimo.gateway.config.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台级配置项（元数据 + 值快照）
 *
 * <p>元数据始终在 MySQL；值按 configSource 存放在 DB / Redis / Nacos，
 * 非 DB 来源时这里的 configValue 是"最后写入快照"，用于远端不可达时兜底展示。
 */
@Data
public class SysConfigEntity {

    private Long id;

    /** 配置键，如 platform.auth.cache.mode */
    private String configKey;

    private String configValue;

    /** BOOLEAN / STRING / NUMBER */
    private String valueType;

    /** 存储来源：DB / REDIS / NACOS */
    private String configSource;

    /** 来源定位：Redis=Hash 键；Nacos=dataId|groupName */
    private String sourceRef;

    /** 所属平台 */
    private String platformName;

    /** 所属应用 */
    private String appName;

    /** 所属服务，* 表示平台级 */
    private String serviceName;

    private String remark;

    private LocalDateTime updateTime;
}
