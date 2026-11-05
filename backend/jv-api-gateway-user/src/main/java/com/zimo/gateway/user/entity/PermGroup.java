package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户分组（表 tb_perm_group）
 * type=ORG 部门分组（树形，parent_id 组织层级）；type=PERM 权限分组（扁平，绑权限点）
 */
@Data
public class PermGroup {

    private Long id;

    private String name;

    private String code;

    /** ORG 部门分组 / PERM 权限分组 */
    private String type;

    private Long parentId;

    /** 数据范围：ALL / DEPT_AND_CHILD / DEPT / SELF */
    private String dataScope;

    private String leader;

    private String remark;

    /** 内置分组不可删除、不可改权限 */
    private Integer builtin;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
