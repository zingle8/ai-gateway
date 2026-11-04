package com.zimo.gateway.client.common.enums;

import lombok.Getter;

/**
 * 网关错误类型，与控制台「错误类型」筛选项保持一致
 */
@Getter
public enum ErrType {

    NONE("无"),
    AUTH_FAIL("鉴权失败"),
    ROUTE_FAIL("路由失败"),
    LIMIT_REJECT("限流拒绝"),
    PARAM_INVALID("参数校验失败"),
    UPSTREAM_TIMEOUT("上游响应超时"),
    UPSTREAM_ERROR("上游响应异常");

    private final String label;

    ErrType(String label) {
        this.label = label;
    }

    /**
     * 根据响应状态码推断错误类型
     */
    public static String resolve(Integer statusCode) {
        if (statusCode == null) {
            return NONE.getLabel();
        }
        if (statusCode == 401 || statusCode == 403) {
            return AUTH_FAIL.getLabel();
        }
        if (statusCode == 404) {
            return ROUTE_FAIL.getLabel();
        }
        if (statusCode == 400 || statusCode == 422) {
            return PARAM_INVALID.getLabel();
        }
        if (statusCode == 429) {
            return LIMIT_REJECT.getLabel();
        }
        if (statusCode == 504) {
            return UPSTREAM_TIMEOUT.getLabel();
        }
        if (statusCode >= 500) {
            return UPSTREAM_ERROR.getLabel();
        }
        return NONE.getLabel();
    }
}
