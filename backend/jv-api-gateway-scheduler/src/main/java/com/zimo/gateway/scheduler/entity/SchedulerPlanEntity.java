package com.zimo.gateway.scheduler.entity;

import lombok.Data;

import java.util.Date;

/**
 * 执行计划（分区预建计划等前瞻性记录）
 */
@Data
public class SchedulerPlanEntity {

    private Long id;

    /** 所属任务键 */
    private String jobKey;

    /** 计划类型：ADD_PARTITION（新增分区） */
    private String planType;

    /** 计划执行日期 */
    private Date planDate;

    /** 作用对象：表名 / 分区名 */
    private String target;

    /** 分区名，如 p20261110 */
    private String partitionName;

    /** 分区边界值（VALUES LESS THAN），如 2026-11-11 00:00:00 */
    private String partitionValue;

    /** 状态：PENDING / DONE / FAILED */
    private String status;

    /** 执行结果说明 */
    private String detail;

    private Date createTime;

    private Date doneTime;
}
