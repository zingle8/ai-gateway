package com.zimo.gateway.user.service;

import com.zimo.gateway.user.entity.ApiKeyLogEntity;
import com.zimo.gateway.user.model.ApiKeyCreatedVO;
import com.zimo.gateway.user.model.ApiKeySaveRequest;
import com.zimo.gateway.user.model.ApiKeyScopeRequest;
import com.zimo.gateway.user.model.ApiKeyStatsVO;
import com.zimo.gateway.user.model.ApiKeyVO;

import java.util.List;

/**
 * 网关 API Key 管理：明文只出现一次，服务端只存 SHA-256
 */
public interface ApiKeyService {

    List<ApiKeyVO> list();

    /** 签发，返回一次性明文 */
    ApiKeyCreatedVO create(ApiKeySaveRequest request, Long ownerUserId);

    void updateStatus(Long id, Integer status);

    /**
     * 保存调用方约束（v0.2 第 6 项）：IP / 服务 / 平台白名单 + 每分钟限流，全量覆盖。
     * 网关侧缓存 30 秒，保存后最迟 30 秒生效；变更写一条审计（action = SCOPE）。
     */
    ApiKeyVO updateScope(Long id, ApiKeyScopeRequest request);

    /** 轮换：keyId 不变、换新密钥，旧 Key 立即失效 */
    ApiKeyCreatedVO rotate(Long id);

    /**
     * 续期：延长有效期，密钥不变。
     * 未过期在原到期时间上顺延；已过期从当前时间起算（续期后立即恢复可用）；永久 Key 不允许续期。
     */
    ApiKeyVO renew(Long id, int renewDays);

    void delete(Long id);

    /** 统计看板（总数 / 今日新增·轮换·吊销·删除·启用 / 7 日内到期） */
    ApiKeyStatsVO stats();

    /** 最近操作记录 */
    List<ApiKeyLogEntity> recentLogs(int limit);
}
