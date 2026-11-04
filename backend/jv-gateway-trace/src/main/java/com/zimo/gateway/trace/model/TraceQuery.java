package com.zimo.gateway.trace.model;

import lombok.Data;

/**
 * 调用日志分页查询条件
 */
@Data
public class TraceQuery {

    private String traceId;

    /** 应用编码精确过滤（tb_gw_trace_log.app_id） */
    private String appId;

    private String service;

    private String api;

    /** 请求参数模糊检索（query string 或请求体） */
    private String params;

    /** HTTP 方法（GET / POST / PUT / DELETE ...），匹配 api 字段的前缀 */
    private String method;

    /** 状态码 */
    private Integer status;

    /** 网关错误类型 */
    private String errType;

    private String protocol;

    private String reqFormat;

    private String resFormat;

    /** 请求起始时间，格式 yyyy-MM-dd */
    private String startTime;

    /** 耗时下限（ms） */
    private Integer costMin;

    private long current = 1;

    private long size = 10;
}
