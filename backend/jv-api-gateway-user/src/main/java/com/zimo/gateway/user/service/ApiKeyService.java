package com.zimo.gateway.user.service;

import com.zimo.gateway.user.model.ApiKeyCreatedVO;
import com.zimo.gateway.user.model.ApiKeySaveRequest;
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

    /** 轮换：keyId 不变、换新密钥，旧 Key 立即失效 */
    ApiKeyCreatedVO rotate(Long id);

    void delete(Long id);
}
