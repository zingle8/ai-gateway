package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.user.entity.ApiKeyLogEntity;
import com.zimo.gateway.user.model.ApiKeyCreatedVO;
import com.zimo.gateway.user.model.ApiKeyRenewRequest;
import com.zimo.gateway.user.model.ApiKeySaveRequest;
import com.zimo.gateway.user.model.ApiKeyStatsVO;
import com.zimo.gateway.user.model.ApiKeyVO;
import com.zimo.gateway.user.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API Key 管理：签发 / 列表 / 启停 / 轮换 / 续期 / 吊销
 */
@RestController
@RequestMapping("/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public R<List<ApiKeyVO>> list() {
        return R.ok(apiKeyService.list());
    }

    /** 统计看板：总数 / 今日新增·轮换·吊销·删除·启用 / 7 日内到期 */
    @GetMapping("/stats")
    public R<ApiKeyStatsVO> stats() {
        return R.ok(apiKeyService.stats());
    }

    /** 最近操作记录（审计明细） */
    @GetMapping("/logs")
    public R<List<ApiKeyLogEntity>> logs(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(apiKeyService.recentLogs(limit));
    }

    @PostMapping
    public R<ApiKeyCreatedVO> create(@Valid @RequestBody ApiKeySaveRequest request) {
        return R.ok(apiKeyService.create(request, UserContext.getUserId()));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody ApiKeyVO body) {
        apiKeyService.updateStatus(id, body.status());
        return R.ok();
    }

    @PostMapping("/{id}/rotate")
    public R<ApiKeyCreatedVO> rotate(@PathVariable Long id) {
        return R.ok(apiKeyService.rotate(id));
    }

    /** 续期：只延长有效期，密钥与 keyId 不变 */
    @PostMapping("/{id}/renew")
    public R<ApiKeyVO> renew(@PathVariable Long id, @Valid @RequestBody ApiKeyRenewRequest request) {
        return R.ok(apiKeyService.renew(id, request.renewDays()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        apiKeyService.delete(id);
        return R.ok();
    }
}
