package com.zimo.gateway.user.entity;

import lombok.Data;

/**
 * 权限字典的平台 / 菜单节点（表 tb_perm_menu，parent_id='' 为平台层）
 */
@Data
public class PermMenuEntity {

    /** 如 p-console、m-auth-user */
    private String id;

    private String name;

    private String parentId;

    private Integer sort;
}
