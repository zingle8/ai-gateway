package com.zimo.gateway.user.model;

/**
 * 分组视图：memberCount / permCount 由聚合查询带入
 */
public record GroupVO(Long id, String name, String code, String type, Long parentId,
                      String dataScope, String leader, String remark,
                      Integer builtin, Integer status, long memberCount, long permCount) {
}
