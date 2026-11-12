package com.zimo.gateway.config.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 豁免规则新增 / 修改请求
 *
 * @param name        规则名称
 * @param pathPattern Ant 风格路径（必须以 / 开头）
 * @param methods     方法（逗号分隔，* 表示全部）
 * @param exemptType  LOGIN / RATE / ALL
 * @param status      1 启用 / 0 停用
 * @param expireAt    到期时间（ISO-8601，可空 = 永久）
 * @param remark      备注
 */
public record AuthExemptSaveRequest(@NotBlank(message = "规则名称不能为空") String name,
                                    @NotBlank(message = "路径不能为空") String pathPattern,
                                    String methods,
                                    @NotBlank(message = "豁免类型不能为空") String exemptType,
                                    Integer status,
                                    String expireAt,
                                    String remark) {
}
