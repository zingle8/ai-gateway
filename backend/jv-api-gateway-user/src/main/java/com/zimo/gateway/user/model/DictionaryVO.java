package com.zimo.gateway.user.model;

import java.util.List;

/**
 * 权限字典：平台 / 菜单树 + 权限点全量
 */
public record DictionaryVO(List<MenuNode> menus, List<PermItemVO> items) {

    public record MenuNode(String id, String name, String parentId, Integer sort,
                           String type, List<MenuNode> children) {
    }

    public record PermItemVO(Long id, String menuId, String funcName, String code,
                             String name, String type, String api, Integer enabled) {
    }
}
