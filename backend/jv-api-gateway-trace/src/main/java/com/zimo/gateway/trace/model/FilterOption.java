package com.zimo.gateway.trace.model;

import lombok.Data;

/**
 * 筛选项字典行：应用下拉用 appId + appName，服务下拉用 appId + serviceId + serviceName。
 * 使用轻量 DTO 而非 resultType="map"，避免列别名大小写在不同驱动下的差异。
 */
@Data
public class FilterOption {

    /** 应用编码，如 app-gateway */
    private String appId;

    /** 应用名称，如 API网关平台 */
    private String appName;

    /** 服务ID（英文唯一标识，trace 表存的值，下拉的 value），如 jv-api-gateway-user */
    private String serviceId;

    /** 服务名（中文显示名，联表 tb_gw_service 取，下拉的 label），如 网关用户 */
    private String serviceName;
}
