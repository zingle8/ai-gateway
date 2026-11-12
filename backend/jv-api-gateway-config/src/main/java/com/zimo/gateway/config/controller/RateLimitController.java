package com.zimo.gateway.config.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.config.entity.RateLimitRuleEntity;
import com.zimo.gateway.config.model.RateLimitRuleSaveRequest;
import com.zimo.gateway.config.service.RateLimitService;
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
import java.util.Map;

/**
 * 网关限流策略管理（控制台「接入中心 → 限流策略」）。
 *
 * <p>规则由网关 proxy 每 5 秒拉取一次并执行令牌桶，改动无需重启网关。
 */
@RestController
@RequestMapping("/rate-limits")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimitService rateLimitService;

    @GetMapping
    public R<List<RateLimitRuleEntity>> list() {
        return R.ok(rateLimitService.list());
    }

    @PostMapping
    public R<RateLimitRuleEntity> create(@Valid @RequestBody RateLimitRuleSaveRequest request) {
        return R.ok(rateLimitService.create(request));
    }

    @PutMapping("/{id}")
    public R<RateLimitRuleEntity> update(@PathVariable Long id,
                                         @Valid @RequestBody RateLimitRuleSaveRequest request) {
        return R.ok(rateLimitService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        rateLimitService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        rateLimitService.delete(id);
        return R.ok();
    }
}
