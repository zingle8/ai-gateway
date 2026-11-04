package com.zimo.gateway.trace.model;

import lombok.Data;

/**
 * 筛选项字典行：应用下拉用 appId + appName，服务下拉用 appId + serviceName。
 * 使用轻量 DTO 而非 resultType="map"，避免列别名大小写在不同驱动下的差异。
 */
@Data
public class FilterOption {

    /** 应用编码，如 app-gateway */
    private String appId;

    /** 应用名称，如 API网关平台 */
    private String appName;

    /** 服务名，如 jv-gateway-user */
    private String serviceName;
}
