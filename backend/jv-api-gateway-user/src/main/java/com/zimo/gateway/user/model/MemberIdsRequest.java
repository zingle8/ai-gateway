package com.zimo.gateway.user.model;

import java.util.List;

/**
 * 分组成员全量替换请求
 */
public record MemberIdsRequest(List<Long> userIds) {
}
