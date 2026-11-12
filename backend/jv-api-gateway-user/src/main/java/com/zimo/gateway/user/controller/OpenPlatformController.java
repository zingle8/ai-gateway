package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.user.model.AccountPlatformSaveRequest;
import com.zimo.gateway.user.model.OpenPlatformVO;
import com.zimo.gateway.user.service.OpenPlatformService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号-平台授权管理：平台清单、某账号的授权读取 / 保存（全量覆盖）。
 *
 * <p>授权结果供开放认证 /open/auth/token/verify 校验使用（带 appId 时生效）。
 */
@RestController
@RequestMapping("/open")
@RequiredArgsConstructor
public class OpenPlatformController {

    private final OpenPlatformService openPlatformService;

    /** 平台清单（含停用，前端置灰展示） */
    @GetMapping("/platforms")
    public R<List<OpenPlatformVO>> platforms() {
        return R.ok(openPlatformService.list());
    }

    /** 某账号已授权的平台 appId 列表 */
    @GetMapping("/accounts/{userId}/platforms")
    public R<List<String>> userPlatforms(@PathVariable Long userId) {
        return R.ok(openPlatformService.appIdsOfUser(userId));
    }

    /** 保存某账号的平台授权（全量覆盖，空数组 = 清空） */
    @PutMapping("/accounts/{userId}/platforms")
    public R<List<String>> saveUserPlatforms(@PathVariable Long userId,
                                             @Valid @RequestBody AccountPlatformSaveRequest request) {
        return R.ok(openPlatformService.saveUserPlatforms(userId, request.appIds()));
    }
}
