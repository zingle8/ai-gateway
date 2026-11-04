package com.zimo.gateway.discovery.model;

import lombok.Data;

/**
 * 服务分页查询条件
 */
@Data
public class ServiceQuery {

    /** 关键字：应用ID / 应用名 / 服务名 */
    private String keyword;

    /** 应用编码 */
    private String appId;

    /** 服务名 */
    private String serviceName;

    /** 服务状态：健康 / 亚健康 / 异常 */
    private String status;

    /** 语言类型 */
    private String language;

    private long current = 1;

    private long size = 10;
}
