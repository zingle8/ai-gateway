package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.result.PageResult;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.User;
import com.zimo.gateway.user.model.UserQuery;
import com.zimo.gateway.user.model.UserSaveRequest;
import com.zimo.gateway.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户分页列表（支持关键字 / 角色 / 状态过滤）
     */
    @GetMapping
    public R<PageResult<User>> page(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String role,
                                    @RequestParam(required = false) Integer status,
                    @RequestParam(defaultValue = "1") long current,
                    @RequestParam(defaultValue = "10") long size) {
        UserQuery query = new UserQuery();
        query.setKeyword(keyword);
        query.setRole(role);
        query.setStatus(status);
        query.setCurrent(current);
        query.setSize(size);
        return R.ok(PageResult.of(userService.pageUsers(query)));
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    public R<User> detail(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return R.fail(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        user.setPassword(null);
        return R.ok(user);
    }

    /**
     * 新增用户
     */
    @PostMapping
    public R<Void> create(@Valid @RequestBody UserSaveRequest request) {
        userService.createUser(request);
        return R.ok();
    }

    /**
     * 修改用户
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.updateUser(id, request);
        return R.ok();
    }

    /**
     * 删除用户（逻辑删除）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok();
    }

    /**
     * 启用 / 禁用
     */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    /**
     * 解锁账号登录：清除连续失败计数。
     * 账号输错达上限被锁后，除等待自动过期外可由管理员手动解锁。
     */
    @PostMapping("/{id}/unlock")
    public R<Void> unlockLogin(@PathVariable Long id) {
        userService.unlockLogin(id);
        return R.ok();
    }
}
