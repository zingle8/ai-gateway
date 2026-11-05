package com.zimo.gateway.user.model;

import java.util.List;

/**
 * 分组权限点全量替换请求
 */
public record PermCodesRequest(List<String> codes) {
}
