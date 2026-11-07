package com.zimo.gateway.scheduler.entity;

import lombok.Data;

import java.util.Date;

/**
 * 调度任务定义
 */
@Data
public class SchedulerJobEntity {

    /** 任务键：data-clean（历史数据清理）/ partition-daily（分区预建） */
    private String jobKey;

    /** 任务名称 */
    private String jobName;

    /** 调度类型：DAILY_TIME（每天固定时刻） */
    private String scheduleType;

    /** 每日执行时间（HH:mm），来源 tb_sys_config，双存储 Redis 优先 */
    private String runTime;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 上次执行时间 */
    private Date lastRunTime;

    /** 上次执行状态：SUCCESS / FAILED */
    private String lastRunStatus;

    /** 任务说明 */
    private String remark;
}
