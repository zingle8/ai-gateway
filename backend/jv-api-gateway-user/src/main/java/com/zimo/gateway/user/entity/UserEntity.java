package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台用户（表 tb_sys_user，字段蛇形命名由 MyBatis 驼峰映射自动对应）
 */
@Data
public class UserEntity {

    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录密码（BCrypt） */
    private String password;

    /** 用户姓名 */
    private String nickname;

    private String email;

    private String phone;

    /** 角色：ADMIN / OPERATOR / VIEWER */
    private String role;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    /** 令牌版本：退出登录时自增，用于让已签发的 JWT 立即失效 */
    private Integer tokenVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0 正常，1 已删除（SQL 里显式过滤） */
    private Integer deleted;
}
