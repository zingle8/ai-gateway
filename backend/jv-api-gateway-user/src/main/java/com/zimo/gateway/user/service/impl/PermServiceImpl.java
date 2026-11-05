package com.zimo.gateway.user.service.impl;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.PermGroup;
import com.zimo.gateway.user.mapper.PermGroupMapper;
import com.zimo.gateway.user.mapper.PermItemMapper;
import com.zimo.gateway.user.mapper.PermMenuMapper;
import com.zimo.gateway.user.model.DictionaryVO;
import com.zimo.gateway.user.model.GroupSaveRequest;
import com.zimo.gateway.user.model.GroupVO;
import com.zimo.gateway.user.model.MemberVO;
import com.zimo.gateway.user.model.PermItemSaveRequest;
import com.zimo.gateway.user.service.PermService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分组 / 组织 / 权限点字典实现。
 * ORG（部门）与 PERM（权限分组）共用一张表，接口按 type 由前端区分呈现。
 */
@Service
@RequiredArgsConstructor
public class PermServiceImpl implements PermService {

    private final PermGroupMapper groupMapper;
    private final PermMenuMapper menuMapper;
    private final PermItemMapper itemMapper;

    // ---------- 分组 ----------

    @Override
    public List<GroupVO> listGroups() {
        Map<Long, Long> memberCounts = toCountMap(groupMapper.countMembers());
        Map<Long, Long> permCounts = toCountMap(groupMapper.countPerms());
        return groupMapper.selectAll().stream()
                .map(g -> new GroupVO(g.getId(), g.getName(), g.getCode(), g.getType(), g.getParentId(),
                        g.getDataScope(), g.getLeader(), g.getRemark(), g.getBuiltin(), g.getStatus(),
                        memberCounts.getOrDefault(g.getId(), 0L), permCounts.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    private static Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> map = new HashMap<>();
        rows.forEach(r -> map.put(((Number) r.get("group_id")).longValue(), ((Number) r.get("cnt")).longValue()));
        return map;
    }

    @Override
    public GroupVO createGroup(GroupSaveRequest request) {
        if (groupMapper.countByCode(request.code()) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "分组编码已存在：" + request.code());
        }
        PermGroup group = new PermGroup();
        group.setName(request.name());
        group.setCode(request.code());
        group.setType(request.type() == null ? "PERM" : request.type());
        group.setParentId(request.parentId() == null ? 0 : request.parentId());
        group.setDataScope(request.dataScope() == null ? "SELF" : request.dataScope());
        group.setLeader(request.leader());
        group.setRemark(request.remark());
        group.setBuiltin(0);
        group.setStatus(request.status() == null ? 1 : request.status());
        groupMapper.insert(group);
        return listGroups().stream().filter(g -> g.id().equals(group.getId())).findFirst().orElseThrow();
    }

    @Override
    public void updateGroup(Long id, GroupSaveRequest request) {
        PermGroup exists = requireGroup(id);
        if (exists.getBuiltin() != null && exists.getBuiltin() == 1 && !exists.getCode().equals(request.code())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "内置分组不可修改编码");
        }
        exists.setName(request.name());
        exists.setDataScope(request.dataScope() == null ? exists.getDataScope() : request.dataScope());
        exists.setLeader(request.leader());
        exists.setRemark(request.remark());
        if (request.status() != null) {
            exists.setStatus(request.status());
        }
        groupMapper.update(exists);
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        PermGroup exists = requireGroup(id);
        if (exists.getBuiltin() != null && exists.getBuiltin() == 1) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "内置分组不可删除");
        }
        if (groupMapper.countChildren(id) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "存在子部门，请先删除或移动子部门");
        }
        groupMapper.clearMembers(id);
        groupMapper.clearPerms(id);
        groupMapper.deleteById(id);
    }

    private PermGroup requireGroup(Long id) {
        PermGroup group = id == null ? null : groupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "分组不存在");
        }
        return group;
    }

    // ---------- 成员 ----------

    @Override
    public List<MemberVO> listMembers(Long groupId) {
        requireGroup(groupId);
        return groupMapper.selectMembers(groupId).stream()
                .map(m -> new MemberVO(((Number) m.get("id")).longValue(),
                        String.valueOf(m.get("username")),
                        String.valueOf(m.get("nickname")),
                        String.valueOf(m.get("role"))))
                .toList();
    }

    @Override
    @Transactional
    public void updateMembers(Long groupId, List<Long> userIds) {
        requireGroup(groupId);
        groupMapper.clearMembers(groupId);
        if (userIds != null) {
            userIds.forEach(uid -> groupMapper.addMember(groupId, uid));
        }
    }

    // ---------- 权限点 ----------

    @Override
    public List<String> listGroupPerms(Long groupId) {
        requireGroup(groupId);
        return groupMapper.selectPermCodes(groupId);
    }

    @Override
    @Transactional
    public void updateGroupPerms(Long groupId, List<String> codes) {
        PermGroup group = requireGroup(groupId);
        if (group.getBuiltin() != null && group.getBuiltin() == 1) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "内置分组默认拥有全部权限，无需分配");
        }
        groupMapper.clearPerms(groupId);
        if (codes != null) {
            codes.forEach(code -> groupMapper.addPerm(groupId, code));
        }
    }

    @Override
    public DictionaryVO dictionary() {
        List<DictionaryVO.MenuNode> menuTree = buildMenuTree(menuMapper.selectAll());
        List<DictionaryVO.PermItemVO> items = itemMapper.selectAll().stream()
                .map(i -> new DictionaryVO.PermItemVO(i.getId(), i.getMenuId(), i.getFuncName(),
                        i.getCode(), i.getName(), i.getType(), i.getApi(), i.getEnabled()))
                .toList();
        return new DictionaryVO(menuTree, items);
    }

    private List<DictionaryVO.MenuNode> buildMenuTree(List<com.zimo.gateway.user.entity.PermMenu> menus) {
        Map<String, DictionaryVO.MenuNode> nodes = new HashMap<>();
        menus.forEach(m -> nodes.put(m.getId(), new DictionaryVO.MenuNode(
                m.getId(), m.getName(), m.getParentId(), m.getSort(),
                m.getParentId().isEmpty() ? "PLATFORM" : "MENU", new ArrayList<>())));
        List<DictionaryVO.MenuNode> roots = new ArrayList<>();
        nodes.values().forEach(n -> {
            DictionaryVO.MenuNode parent = nodes.get(n.parentId());
            if (parent != null) {
                parent.children().add(n);
            } else {
                roots.add(n);
            }
        });
        Comparator<DictionaryVO.MenuNode> bySort = Comparator.comparing(DictionaryVO.MenuNode::sort,
                Comparator.nullsLast(Comparator.naturalOrder()));
        roots.sort(bySort);
        roots.forEach(r -> r.children().sort(bySort));
        return roots;
    }

    @Override
    public void createPermItem(PermItemSaveRequest request) {
        if (itemMapper.countByCode(request.code(), null) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "权限 code 已存在：" + request.code());
        }
        com.zimo.gateway.user.entity.PermItem item = new com.zimo.gateway.user.entity.PermItem();
        applyItem(item, request);
        itemMapper.insert(item);
    }

    @Override
    public void updatePermItem(Long id, PermItemSaveRequest request) {
        com.zimo.gateway.user.entity.PermItem item = itemMapper.selectAll().stream()
                .filter(i -> i.getId().equals(id)).findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND.getCode(), "权限点不存在"));
        if (itemMapper.countByCode(request.code(), id) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "权限 code 已存在：" + request.code());
        }
        applyItem(item, request);
        itemMapper.update(item);
    }

    private void applyItem(com.zimo.gateway.user.entity.PermItem item, PermItemSaveRequest request) {
        item.setMenuId(request.menuId());
        item.setFuncName(request.funcName());
        item.setCode(request.code());
        item.setName(request.name());
        item.setType(request.type() == null ? "BUTTON" : request.type());
        item.setApi(request.api() == null || request.api().isBlank() ? "—" : request.api());
        item.setEnabled(request.enabled() == null ? 1 : request.enabled());
    }

    @Override
    public void deletePermItem(Long id) {
        itemMapper.deleteById(id);
    }

    @Override
    public List<String> permsOfUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<PermGroup> myGroups = groupMapper.selectGroupsByUser(userId);
        if (myGroups.isEmpty()) {
            return List.of();
        }
        // 内置分组（如超级管理员）语义为「全部权限」：直接授予字典里所有启用的权限点
        if (myGroups.stream().anyMatch(g -> g.getBuiltin() != null && g.getBuiltin() == 1)) {
            return itemMapper.selectAll().stream()
                    .filter(i -> i.getEnabled() != null && i.getEnabled() == 1)
                    .map(com.zimo.gateway.user.entity.PermItem::getCode)
                    .toList();
        }
        return groupMapper.selectPermCodesByUser(userId);
    }
}
