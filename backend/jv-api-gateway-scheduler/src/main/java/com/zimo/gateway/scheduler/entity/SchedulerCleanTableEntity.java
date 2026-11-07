package com.zimo.gateway.scheduler.entity;

import lombok.Data;

/**
 * 清理表配置（清理清单配置化：新增采集表只需加一行配置）
 */
@Data
public class SchedulerCleanTableEntity {

    private Long id;

    /** 物理表名 */
    private String tableName;

    /** 时间列名（按该列判定数据归属日期） */
    private String timeColumn;

    /** 保留天数：删除该天数之前的数据（默认 7） */
    private Integer keepDays;

    /** 状态：1 启用 0 停用 */
    private Integer enabled;

    private String remark;
}
