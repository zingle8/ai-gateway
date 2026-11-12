package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.user.model.SessionKickRequest;
import com.zimo.gateway.user.model.SessionVO;
import com.zimo.gateway.user.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 在线会话与令牌吊销记录（控制台「令牌与会话」页）。
 *
 * <p>会话在登录时登记到 Redis；强制下线支持四种粒度：
 * 单会话（sid）/ 按平台（appId）/ 按端（device）/ 全端（token_version 自增）。
 */
@RestController
@RequestMapping("/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /** 在线会话列表（全部账号） */
    @GetMapping
    public R<List<SessionVO>> list() {
        return R.ok(sessionService.listAll());
    }

    /** 当前登录账号的会话 */
    @GetMapping("/mine")
    public R<List<SessionVO>> mine() {
        return R.ok(sessionService.listByUser(UserContext.getUserId()));
    }

    /** 最近吊销记录（默认 50 条，最多 200 条） */
    @GetMapping("/revokes")
    public R<List<Map<String, Object>>> revokes(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(sessionService.recentRevokes(limit));
    }

    /** 强制下线（四粒度），返回实际下线会话数 */
    @PostMapping("/kick")
    public R<Map<String, Object>> kick(@Valid @RequestBody SessionKickRequest request) {
        int affected = sessionService.kick(request.scope(), request.sid(), request.userId(),
                request.appId(), request.device(), request.reason(), UserContext.getUsername());
        return R.ok(Map.of("affected", affected));
    }
}
