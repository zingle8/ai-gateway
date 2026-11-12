package com.zimo.gateway.discovery.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 接入方主机运行指标（tb_gw_host_metrics，v0.2 第 10 项）。
 *
 * <p>SDK 每 60 秒采集一批，批量上报；无 GPU 环境下 gpuCount = 0、gpuUtil / gpuMemPct 为空。
 * 数值字段均可空：采集器能力不足时留空，平台不做插值补点。
 */
@Data
public class HostMetricsEntity {

    private Long id;
    private String hostname;
    private String appId;
    private String serviceName;
    private String osName;
    private String osVersion;

    private Integer cpuCores;
    private BigDecimal cpuUsage;
    private BigDecimal loadAvg;

    private Long memTotal;
    private BigDecimal memUsedPct;
    private Long procRss;
    private BigDecimal jvmHeapPct;
    private Long gcCount;
    private Long gcTimeMs;
    private Integer threadCount;
    private Integer fdCount;

    private Long diskTotal;
    private BigDecimal diskUsedPct;
    private Long diskReadBps;
    private Long diskWriteBps;
    private Integer diskIops;

    private Long netRxBps;
    private Long netTxBps;
    private Integer netConn;

    private Integer gpuCount;
    private BigDecimal gpuUtil;
    private BigDecimal gpuMemPct;

    private LocalDateTime reportTime;
    private LocalDateTime createTime;
}
