package com.zimo.gateway.user.model;

import lombok.Data;

/**
 * 用户分页查询条件
 */
@Data
public class UserQuery {

    /** 关键字：账号 / 姓名模糊匹配 */
    private String keyword;

    /** 角色：ADMIN / OPERATOR / VIEWER */
    private String role;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    private long current = 1;

    private long size = 10;
}
