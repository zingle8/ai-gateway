package com.zimo.gateway.user.service;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.OpenPlatformEntity;
import com.zimo.gateway.user.mapper.AccountPlatformMapper;
import com.zimo.gateway.user.mapper.OpenPlatformMapper;
import com.zimo.gateway.user.model.OpenPlatformVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 开放平台登记与账号-平台授权。
 *
 * <p>授权模型：账号授权到 N 个平台（appId 列表），开放认证换 token 时若带 appId，
 * 校验平台存在且启用、账号已授权，否则拒绝——「认证了 ≠ 能用」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenPlatformService {

    private final OpenPlatformMapper platformMapper;
    private final AccountPlatformMapper accountPlatformMapper;
    private final UserService userService;

    /** 平台清单（含停用，停用项在管理端置灰展示） */
    public List<OpenPlatformVO> list() {
        return platformMapper.selectAll().stream()
                .map(p -> new OpenPlatformVO(p.getId(), p.getAppId(), p.getAppName(),
                        p.getStatus(), p.getRemark(), false))
                .toList();
    }

    /** 账号已授权的平台 appId 列表 */
    public List<String> appIdsOfUser(Long userId) {
        return accountPlatformMapper.selectAppIdsByUser(userId);
    }

    /**
     * 保存账号的平台授权（全量覆盖）。
     *
     * <p>不存在的 appId 会被忽略（防止脏数据写入关系表），空数组表示清空授权。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> saveUserPlatforms(Long userId, List<String> appIds) {
        if (userService.getById(userId) == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "账号不存在：" + userId);
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String appId : appIds == null ? List.<String>of() : appIds) {
            if (appId == null || appId.isBlank()) {
                continue;
            }
            if (platformMapper.selectByAppId(appId.trim()) != null) {
                wanted.add(appId.trim());
            }
        }
        accountPlatformMapper.deleteByUser(userId);
        for (String appId : wanted) {
            accountPlatformMapper.insert(userId, appId);
        }
        log.info("[平台授权] 账号 {} 平台授权更新为 {} 个：{}", userId, wanted.size(), wanted);
        return List.copyOf(wanted);
    }

    /** 开放认证校验用：平台存在且启用 */
    public OpenPlatformEntity enabledPlatform(String appId) {
        OpenPlatformEntity platform = platformMapper.selectByAppId(appId);
        if (platform == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "目标平台不存在：" + appId);
        }
        if (platform.getStatus() == null || platform.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "目标平台已停用：" + appId);
        }
        return platform;
    }

    /** 开放认证校验用：账号是否已授权到该平台 */
    public boolean isAuthorized(Long userId, String appId) {
        return accountPlatformMapper.countByUserAndApp(userId, appId) > 0;
    }
}
