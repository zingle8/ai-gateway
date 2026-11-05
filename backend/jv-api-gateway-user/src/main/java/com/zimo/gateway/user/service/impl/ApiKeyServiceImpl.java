package com.zimo.gateway.user.service.impl;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.ApiKey;
import com.zimo.gateway.user.mapper.ApiKeyMapper;
import com.zimo.gateway.user.model.ApiKeyCreatedVO;
import com.zimo.gateway.user.model.ApiKeySaveRequest;
import com.zimo.gateway.user.model.ApiKeyVO;
import com.zimo.gateway.user.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
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
 */
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final String PREFIX = "gwak-";
    private static final String KEY_ID_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyMapper apiKeyMapper;

    @Override
    public List<ApiKeyVO> list() {
        return apiKeyMapper.selectAll().stream()
                .map(k -> new ApiKeyVO(k.getId(), k.getKeyId(), k.getName(), k.getStatus(),
                        k.getExpireAt(), k.getLastUsedAt(), k.getLastUsedIp(), k.getCreateTime()))
                .toList();
    }

    @Override
    public ApiKeyCreatedVO create(ApiKeySaveRequest request, Long ownerUserId) {
        ApiKey key = new ApiKey();
        key.setKeyId(randomKey(8));
        key.setName(request.name());
        key.setOwnerUserId(ownerUserId);
        key.setStatus(1);
        key.setExpireAt(expireAt(request.expireDays()));
        return issue(key);
    }

    private ApiKeyCreatedVO issue(ApiKey key) {
        String secret = randomKey(40);
        key.setSecretHash(sha256Hex(secret));
        apiKeyMapper.insert(key);
        return new ApiKeyCreatedVO(key.getId(), key.getKeyId(), key.getName(), secret,
                PREFIX + key.getKeyId() + "-" + secret, key.getExpireAt());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        requireKey(id);
        apiKeyMapper.updateStatus(id, status == null ? 1 : status);
    }

    @Override
    public ApiKeyCreatedVO rotate(Long id) {
        ApiKey key = requireKey(id);
        String secret = randomKey(40);
        apiKeyMapper.updateSecret(id, sha256Hex(secret));
        return new ApiKeyCreatedVO(key.getId(), key.getKeyId(), key.getName(), secret,
                PREFIX + key.getKeyId() + "-" + secret, key.getExpireAt());
    }

    @Override
    public void delete(Long id) {
        requireKey(id);
        apiKeyMapper.deleteById(id);
    }

    private ApiKey requireKey(Long id) {
        ApiKey key = id == null ? null : apiKeyMapper.selectById(id);
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
    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
