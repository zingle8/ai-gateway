package com.zimo.gateway.user.service;

import com.zimo.gateway.user.model.DictionaryVO;
import com.zimo.gateway.user.model.GroupSaveRequest;
import com.zimo.gateway.user.model.GroupVO;
import com.zimo.gateway.user.model.MemberVO;
import com.zimo.gateway.user.model.PermItemSaveRequest;

import java.util.List;

/**
 * 分组 / 组织 / 权限点字典
 */
public interface PermService {

    /** 全量分组（扁平，含成员数与权限点数；前端把 ORG 组成树） */
    List<GroupVO> listGroups();

    GroupVO createGroup(GroupSaveRequest request);

    void updateGroup(Long id, GroupSaveRequest request);

    void deleteGroup(Long id);

    List<MemberVO> listMembers(Long groupId);

    /** 成员全量替换 */
    void updateMembers(Long groupId, List<Long> userIds);

    List<String> listGroupPerms(Long groupId);

    /** 权限点全量替换（内置分组拒绝修改） */
    void updateGroupPerms(Long groupId, List<String> codes);

    /** 权限字典：平台/菜单树 + 权限点全量 */
    DictionaryVO dictionary();

    void createPermItem(PermItemSaveRequest request);

    void updatePermItem(Long id, PermItemSaveRequest request);

    void deletePermItem(Long id);

    /** 用户有效权限点：所属全部分组权限并集 */
    List<String> permsOfUser(Long userId);
}
