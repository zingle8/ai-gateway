package com.zimo.gateway.user.service.impl;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.ApiKeyEntity;
import com.zimo.gateway.user.entity.ApiKeyLogEntity;
import com.zimo.gateway.user.mapper.ApiKeyLogMapper;
import com.zimo.gateway.user.mapper.ApiKeyMapper;
import com.zimo.gateway.user.model.ApiKeyCreatedVO;
import com.zimo.gateway.user.model.ApiKeySaveRequest;
import com.zimo.gateway.user.model.ApiKeyStatsVO;
import com.zimo.gateway.user.model.ApiKeyVO;
import com.zimo.gateway.user.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * API Key 实现。
 * 明文格式 gwak-&lt;keyId&gt;-&lt;secret&gt;（secret 为 40 位十六进制），
 * 服务端仅存 SHA-256(secret)；网关用 X-Api-Key 头携带明文，比对哈希后放行。
 *
 * <p>每次变更写一条审计（tb_gw_api_key_log），统计看板据此汇总今日新增 /
 * 轮换 / 吊销 / 删除；主表只保留当前状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final String PREFIX = "gwak-";
    private static final String KEY_ID_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyLogMapper apiKeyLogMapper;

    @Override
    public List<ApiKeyVO> list() {
        return apiKeyMapper.selectAll().stream()
                .map(k -> new ApiKeyVO(k.getId(), k.getKeyId(), k.getName(), k.getStatus(),
                        k.getExpireAt(), k.getLastUsedAt(), k.getLastUsedIp(), k.getCreateTime()))
                .toList();
    }

    @Override
    public ApiKeyCreatedVO create(ApiKeySaveRequest request, Long ownerUserId) {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setKeyId(randomKey(8));
        key.setName(request.name());
        key.setOwnerUserId(ownerUserId);
        key.setStatus(1);
        key.setExpireAt(expireAt(request.expireDays()));
        ApiKeyCreatedVO created = issue(key);
        audit(key, "CREATE", "签发");
        return created;
    }

    private ApiKeyCreatedVO issue(ApiKeyEntity key) {
        String secret = randomKey(40);
        key.setSecretHash(sha256Hex(secret));
        apiKeyMapper.insert(key);
        return new ApiKeyCreatedVO(key.getId(), key.getKeyId(), key.getName(), secret,
                PREFIX + key.getKeyId() + "-" + secret, key.getExpireAt());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        ApiKeyEntity key = requireKey(id);
        int target = status == null ? 1 : status;
        apiKeyMapper.updateStatus(id, target);
        audit(key, target == 1 ? "ENABLE" : "DISABLE", target == 1 ? "启用" : "吊销（停用）");
    }

    @Override
    public ApiKeyCreatedVO rotate(Long id) {
        ApiKeyEntity key = requireKey(id);
        String secret = randomKey(40);
        apiKeyMapper.updateSecret(id, sha256Hex(secret));
        audit(key, "ROTATE", "轮换密钥");
        return new ApiKeyCreatedVO(key.getId(), key.getKeyId(), key.getName(), secret,
                PREFIX + key.getKeyId() + "-" + secret, key.getExpireAt());
    }

    @Override
    public void delete(Long id) {
        ApiKeyEntity key = requireKey(id);
        apiKeyMapper.deleteById(id);
        audit(key, "DELETE", "删除");
    }

    @Override
    public ApiKeyStatsVO stats() {
        return ApiKeyStatsVO.from(apiKeyLogMapper.selectStats());
    }

    @Override
    public List<ApiKeyLogEntity> recentLogs(int limit) {
        return apiKeyLogMapper.selectRecent(Math.min(Math.max(limit, 1), 100));
    }

    /** 写审计：统计依赖它，失败不能让主流程报错 */
    private void audit(ApiKeyEntity key, String action, String remark) {
        try {
            ApiKeyLogEntity logRow = new ApiKeyLogEntity();
            logRow.setKeyId(key.getKeyId());
            logRow.setKeyName(key.getName());
            logRow.setAction(action);
            logRow.setOperatorUserId(UserContext.getUserId());
            logRow.setOperatorName(UserContext.getUsername());
            logRow.setRemark(remark);
            apiKeyLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("[API Key] 审计写入失败 action={} keyId={}：{}", action, key.getKeyId(), e.getMessage());
        }
    }

    private ApiKeyEntity requireKey(Long id) {
        ApiKeyEntity key = id == null ? null : apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "API Key 不存在");
        }
        return key;
    }

    private LocalDateTime expireAt(Integer expireDays) {
        return expireDays == null || expireDays <= 0 ? null : LocalDateTime.now().plusDays(expireDays);
    }

    private String randomKey(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(KEY_ID_CHARS.charAt(RANDOM.nextInt(KEY_ID_CHARS.length())));
        }
        return sb.toString();
    }

    /** SHA-256 十六进制；密钥哈希不涉及用户口令，可放心使用快速哈希 */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
