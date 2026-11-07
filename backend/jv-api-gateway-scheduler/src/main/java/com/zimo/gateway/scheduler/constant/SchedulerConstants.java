package com.zimo.gateway.scheduler.constant;

import java.time.format.DateTimeFormatter;

/**
 * 调度中心模块内常量（模块专属；跨模块约定见 common 模块 GwConfigKeys / GwRedisKeys）。
 */
public final class SchedulerConstants {

    /** 任务键：历史数据清理 */
    public static final String JOB_DATA_CLEAN = "data-clean";

    /** 任务键：分区表每日预建 */
    public static final String JOB_PARTITION_DAILY = "partition-daily";

    /** 执行状态 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";

    /** 计划类型：新增分区 */
    public static final String PLAN_ADD_PARTITION = "ADD_PARTITION";

    /** 触发方式 */
    public static final String RUN_SCHEDULED = "SCHEDULED";
    public static final String RUN_MANUAL = "MANUAL";

    /** 时间格式：HH:mm（调度时间） */
    public static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** 分区名日期格式：p + yyyyMMdd */
    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 分区边界值格式 */
    public static final DateTimeFormatter BOUNDARY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认调度时间 */
    public static final String DEFAULT_CLEAN_RUN_TIME = "02:00";

    /** 清理保留天数默认值 */
    public static final int DEFAULT_KEEP_DAYS = 7;

    /** 分区提前预建天数默认值 */
    public static final int DEFAULT_ADVANCE_DAYS = 2;

    private SchedulerConstants() {
        throw new UnsupportedOperationException("常量类禁止实例化");
    }
}
