package com.zimo.gateway.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源已存在或状态冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;
}
