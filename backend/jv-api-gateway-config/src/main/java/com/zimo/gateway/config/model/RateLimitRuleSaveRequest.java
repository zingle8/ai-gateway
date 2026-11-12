package com.zimo.gateway.config.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 限流规则新增 / 修改请求
 *
 * @param name            规则名称
 * @param dimension       维度：GLOBAL / ROUTE / API_KEY / IP
 * @param target          匹配目标（ROUTE 为 Ant 路径，API_KEY 为 keyId，IP 为具体 IP，其余可为 *）
 * @param replenishRate   每秒补充令牌数（= 稳态 QPS）
 * @param burstCapacity   桶容量（突发上限）
 * @param requestedTokens 每次请求消耗令牌数
 * @param status          1 启用 / 0 停用
 * @param remark          备注
 */
public record RateLimitRuleSaveRequest(@NotBlank(message = "规则名称不能为空") String name,
                                       @NotBlank(message = "限流维度不能为空") String dimension,
                                       String target,
                                       @NotNull(message = "每秒令牌数不能为空")
                                       @Min(value = 1, message = "每秒令牌数至少为 1") Integer replenishRate,
                                       @NotNull(message = "桶容量不能为空")
                                       @Min(value = 1, message = "桶容量至少为 1") Integer burstCapacity,
                                       Integer requestedTokens,
                                       Integer status,
                                       String remark) {
}
