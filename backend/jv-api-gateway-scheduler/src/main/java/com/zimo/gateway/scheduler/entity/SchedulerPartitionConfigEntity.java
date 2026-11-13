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

    /** 分区列（RANGE 分区列） */
    private String partitionColumn;

    /** 分区边界风格：UNIX_TIMESTAMP（默认）/ TO_DAYS（按 TO_DAYS(date_col) 建表，如主机指标表） */
    private String boundaryStyle;

    /** 分区粒度：DAY */
    private String partitionType;

    /** 提前预建天数（默认 2） */
    private Integer advanceDays;

    /** 状态：1 启用 0 停用 */
    private Integer enabled;
}
