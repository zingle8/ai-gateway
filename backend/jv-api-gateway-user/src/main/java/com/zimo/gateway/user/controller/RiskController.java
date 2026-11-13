package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.user.entity.RiskEventEntity;
import com.zimo.gateway.user.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 鉴权风控中心（v0.2 第 13 项）。
 *
 * <pre>
 * GET  /risk/summary            概览：今日事件 / 待处置 / 高风险 / 今日失败登录 / 近 7 天类型分布 / Key 预警数
 * GET  /risk/events             风控事件列表（主体类型 / 状态 / 等级 / 关键字 + 分页）
 * POST /risk/events/{id}/handle 处置事件（确认 / 忽略 / 禁用用户 / 启用用户 / 解锁 / 强制下线 / 停用 Key / 恢复 Key）
 * GET  /risk/logins             登录历史（账号 / 成败 / 仅风险 + 分页）
 * GET  /risk/keys/warnings      Key 预警清单（临期 / 已过期 / 长期未用）
 * </pre>
 */
@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;
    private final com.zimo.gateway.user.service.IpBanService ipBanService;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(riskService.summary());
    }

    @GetMapping("/events")
    public R<Map<String, Object>> events(@RequestParam(required = false) String subjectType,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String riskLevel,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return R.ok(riskService.pageEvents(subjectType, status, riskLevel, keyword,
                Math.max(page, 1), Math.min(Math.max(size, 1), 200)));
    }

    @PostMapping("/events/{id}/handle")
    public R<RiskEventEntity> handle(@PathVariable Long id,
                                     @RequestBody Map<String, String> body) {
        return R.ok(riskService.handle(id, body.get("action"), body.get("remark")));
    }

    @GetMapping("/logins")
    public R<Map<String, Object>> logins(@RequestParam(required = false) String username,
                                         @RequestParam(required = false) Integer success,
                                         @RequestParam(defaultValue = "false") boolean riskOnly,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return R.ok(riskService.pageLogins(username, success, riskOnly,
                Math.max(page, 1), Math.min(Math.max(size, 1), 200)));
    }

    @GetMapping("/keys/warnings")
    public R<List<Map<String, Object>>> keyWarnings() {
        return R.ok(riskService.keyWarnings());
    }

    // ---------------------------------------------------------- IP 封禁（v0.2 增强）

    /** 封禁清单（含已解封历史，前端标注状态） */
    @GetMapping("/ip-bans")
    public R<List<com.zimo.gateway.user.entity.IpBanEntity>> ipBans() {
        return R.ok(ipBanService.list());
    }

    /** 手动封禁 IP（支持前缀通配 10.0.*），登录与网关两层同时生效 */
    @PostMapping("/ip-bans")
    public R<com.zimo.gateway.user.entity.IpBanEntity> banIp(@RequestBody Map<String, String> body) {
        String operator = com.zimo.gateway.common.context.UserContext.getUsername();
        return R.ok(ipBanService.ban(body.get("ip"), body.get("reason"), operator));
    }

    /** 解封 IP */
    @DeleteMapping("/ip-bans/{ip}")
    public R<Map<String, Object>> unbanIp(@PathVariable String ip) {
        String operator = com.zimo.gateway.common.context.UserContext.getUsername();
        int rows = ipBanService.unban(ip, operator);
        return R.ok(Map.of("affected", rows));
    }
}
