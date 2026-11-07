package com.zimo.gateway.scheduler.entity;

import lombok.Data;

import java.util.Date;

/**
 * 任务执行记录（每次触发的实际执行结果）
 */
@Data
public class SchedulerRunLogEntity {

    private Long id;

    /** 所属任务键 */
    private String jobKey;

    /** 触发方式：SCHEDULED（定时）/ MANUAL（手动） */
    private String runType;

    /** 作用对象：表名（清理任务）/ 分区名（分区任务），可空表示整任务级 */
    private String target;

    private Date startTime;

    private Date endTime;

    /** 状态：SUCCESS / FAILED */
    private String status;

    /** 处理行数（清理：删除行数；分区：新建分区数） */
    private Long totalRows;

    /** 结果摘要 */
    private String detail;

    /** 失败原因 */
    private String error;

    /** 执行实例标识（host:port），多实例部署时定位执行者 */
    private String host;
}
