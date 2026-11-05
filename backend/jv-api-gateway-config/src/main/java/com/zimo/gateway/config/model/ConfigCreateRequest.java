package com.zimo.gateway.config.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增配置
 *
 * @param key          配置键（全局唯一，建议 应用.模块.项 的层级式命名）
 * @param value        初始值
 * @param valueType    BOOLEAN / STRING / NUMBER，默认 STRING
 * @param source       DB / REDIS / NACOS，默认 DB
 * @param sourceRef    Redis Hash 键 / Nacos dataId|groupName，非 DB 来源必填
 * @param platformName 所属平台，默认 API网关平台
 * @param appName      所属应用，默认 jv-api-gateway
 * @param serviceName  所属服务，* 表示平台级
 * @param remark       说明
 */
public record ConfigCreateRequest(
        @NotBlank(message = "配置键不能为空")
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,128}$", message = "配置键只允许字母、数字、点、下划线、中划线，长度 3~128")
        String key,

        @NotBlank(message = "配置值不能为空")
        @Size(max = 512, message = "配置值最长 512 字符")
        String value,

        String valueType,
        String source,
        String sourceRef,
        String platformName,
        String appName,
        String serviceName,
        String remark) {
}
