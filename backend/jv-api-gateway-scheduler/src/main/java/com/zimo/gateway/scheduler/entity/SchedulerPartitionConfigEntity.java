package com.zimo.gateway.scheduler.entity;

import lombok.Data;

/**
 * 分区表配置（分区粒度 / 提前预建天数）
 */
@Data
public class SchedulerPartitionConfigEntity {

    private Long id;

    /** 分区表名 */
    private String tableName;

    /** 分区列（RANGE UNIX_TIMESTAMP 列） */
    private String partitionColumn;

    /** 分区粒度：DAY */
    private String partitionType;

    /** 提前预建天数（默认 2） */
    private Integer advanceDays;

    /** 状态：1 启用 0 停用 */
    private Integer enabled;
}
