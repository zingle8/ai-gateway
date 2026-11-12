package com.zimo.gateway.config.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.config.entity.AuthExemptEntity;
import com.zimo.gateway.config.model.AuthExemptSaveRequest;
import com.zimo.gateway.config.service.AuthExemptService;
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
 * 鉴权精细豁免（控制台「认证与安全 → 鉴权豁免」）。
 *
 * <p>高危配置：放行路径直接绕过登录/限流，控制台操作需二次确认。
 */
@RestController
@RequestMapping("/auth-exempts")
@RequiredArgsConstructor
public class AuthExemptController {

    private final AuthExemptService authExemptService;

    @GetMapping
    public R<List<AuthExemptEntity>> list() {
        return R.ok(authExemptService.list());
    }

    @PostMapping
    public R<AuthExemptEntity> create(@Valid @RequestBody AuthExemptSaveRequest request) {
        return R.ok(authExemptService.create(request));
    }

    @PutMapping("/{id}")
    public R<AuthExemptEntity> update(@PathVariable Long id,
                                      @Valid @RequestBody AuthExemptSaveRequest request) {
        return R.ok(authExemptService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        authExemptService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        authExemptService.delete(id);
        return R.ok();
    }
}
