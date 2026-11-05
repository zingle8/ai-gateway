package com.zimo.gateway.user.entity;

import lombok.Data;

/**
 * 权限点（表 tb_perm_item）：code 为前后端共用的鉴权标识
 */
@Data
public class PermItemEntity {

    private Long id;

    /** 挂载的菜单 id（tb_perm_menu.id） */
    private String menuId;

    /** 页面内的功能区块名，如「用户列表」 */
    private String funcName;

    /** 如 auth.user.create，唯一 */
    private String code;

    private String name;

    /** BUTTON 可点操作 / DISPLAY 可见区块字段 / API 纯接口鉴权 */
    private String type;

    private String api;

    private Integer enabled;
}
