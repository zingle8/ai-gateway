package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.user.model.DictionaryVO;
import com.zimo.gateway.user.model.GroupSaveRequest;
import com.zimo.gateway.user.model.GroupVO;
import com.zimo.gateway.user.model.MemberIdsRequest;
import com.zimo.gateway.user.model.MemberVO;
import com.zimo.gateway.user.model.PermCodesRequest;
import com.zimo.gateway.user.model.PermItemSaveRequest;
import com.zimo.gateway.user.service.PermService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分组 / 组织 / 权限点字典接口。
 * ORG 与 PERM 共用 /perm/groups，前端按 type 区分呈现（组织树 / 权限分组列表）。
 */
@RestController
@RequestMapping("/perm")
@RequiredArgsConstructor
public class PermController {

    private final PermService permService;

    // ---------- 分组（含部门组织） ----------

    @GetMapping("/groups")
    public R<List<GroupVO>> groups() {
        return R.ok(permService.listGroups());
    }

    @PostMapping("/groups")
    public R<GroupVO> createGroup(@Valid @RequestBody GroupSaveRequest request) {
        return R.ok(permService.createGroup(request));
    }

    @PutMapping("/groups/{id}")
    public R<Void> updateGroup(@PathVariable Long id, @Valid @RequestBody GroupSaveRequest request) {
        permService.updateGroup(id, request);
        return R.ok();
    }

    @DeleteMapping("/groups/{id}")
    public R<Void> deleteGroup(@PathVariable Long id) {
        permService.deleteGroup(id);
        return R.ok();
    }

    @GetMapping("/groups/{id}/members")
    public R<List<MemberVO>> members(@PathVariable Long id) {
        return R.ok(permService.listMembers(id));
    }

    @PutMapping("/groups/{id}/members")
    public R<Void> updateMembers(@PathVariable Long id, @RequestBody MemberIdsRequest request) {
        permService.updateMembers(id, request.userIds());
        return R.ok();
    }

    @GetMapping("/groups/{id}/perms")
    public R<List<String>> groupPerms(@PathVariable Long id) {
        return R.ok(permService.listGroupPerms(id));
    }

    @PutMapping("/groups/{id}/perms")
    public R<Void> updateGroupPerms(@PathVariable Long id, @RequestBody PermCodesRequest request) {
        permService.updateGroupPerms(id, request.codes());
        return R.ok();
    }

    // ---------- 权限字典 ----------

    @GetMapping("/dictionary")
    public R<DictionaryVO> dictionary() {
        return R.ok(permService.dictionary());
    }

    @PostMapping("/items")
    public R<Void> createItem(@Valid @RequestBody PermItemSaveRequest request) {
        permService.createPermItem(request);
        return R.ok();
    }

    @PutMapping("/items/{id}")
    public R<Void> updateItem(@PathVariable Long id, @Valid @RequestBody PermItemSaveRequest request) {
        permService.updatePermItem(id, request);
        return R.ok();
    }

    @DeleteMapping("/items/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        permService.deletePermItem(id);
        return R.ok();
    }
}
