package com.zimo.gateway.user.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.util.IpUtil;
import com.zimo.gateway.user.service.SsoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 单点登录 SSO（OIDC Provider，v0.2 第 15 项）。
 *
 * <pre>
 * 管理端（需登录，走 /sso 前缀）：
 *   GET    /sso/clients                 接入方列表
 *   POST   /sso/clients                 签发接入方（返回一次 client_secret）
 *   PUT    /sso/clients/{id}            修改（名称 / appId / 回调 / 状态）
 *   POST   /sso/clients/{id}/rotate     轮换 client_secret（旧值立即失效）
 *   DELETE /sso/clients/{id}            删除接入方
 *   GET    /sso/logs                    SSO 登录审计
 *
 * OIDC 协议端点（匿名，gw_token Cookie 即 IdP 会话）：
 *   GET  /oauth/authorize              授权码发放（302 回 SP 回调；未登录 302 回控制台登录页）
 *   POST /oauth/token                  code 换 id_token / access_token（client_secret 校验 + 授权关系校验）
 *   GET  /oauth/userinfo               access_token 实时换身份（token_version 失配 401 = 单点登出生效点）
 *   GET  /.well-known/openid-configuration  元数据发现
 *   GET  /oauth/jwks                   公钥发布（HS256 对称密钥不发布，返回空键集）
 * </pre>
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class SsoController {

    private final SsoService ssoService;

    // ------------------------------------------------------ 管理端

    @GetMapping("/sso/clients")
    public R<List<Map<String, Object>>> clients() {
        return R.ok(ssoService.listClients());
    }

    @PostMapping("/sso/clients")
    public R<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String name = requireField(body, "clientName", "接入方名称必填");
        String redirectUri = requireField(body, "redirectUri", "回调地址必填");
        return R.ok(ssoService.createClient(name, body.get("appId"), redirectUri));
    }

    @PutMapping("/sso/clients/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = body.get("clientName") == null ? null : String.valueOf(body.get("clientName"));
        String appId = body.get("appId") == null ? null : String.valueOf(body.get("appId"));
        String redirectUri = body.get("redirectUri") == null ? null : String.valueOf(body.get("redirectUri"));
        Integer status = body.get("status") == null ? null : Integer.valueOf(String.valueOf(body.get("status")));
        ssoService.updateClient(id, name, appId, redirectUri, status);
        return R.ok(null);
    }

    @PostMapping("/sso/clients/{id}/rotate")
    public R<Map<String, Object>> rotate(@PathVariable Long id) {
        return R.ok(ssoService.rotateSecret(id));
    }

    @DeleteMapping("/sso/clients/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ssoService.deleteClient(id);
        return R.ok(null);
    }

    @GetMapping("/sso/logs")
    public R<Map<String, Object>> logs(@RequestParam(required = false) String clientId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return R.ok(ssoService.pageLogs(clientId, action,
                Math.max(page, 1), Math.min(Math.max(size, 1), 200)));
    }

    // ------------------------------------------------------ OIDC 协议端点

    @GetMapping("/oauth/authorize")
    public org.springframework.http.ResponseEntity<Void> authorize(@RequestParam("client_id") String clientId,
                                                                   @RequestParam("redirect_uri") String redirectUri,
                                                                   @RequestParam(value = "response_type", defaultValue = "code") String responseType,
                                                                   @RequestParam(value = "state", required = false) String state,
                                                                   @RequestParam(value = "scope", required = false) String scope,
                                                                   HttpServletRequest request) {
        String target = ssoService.authorize(clientId, redirectUri, responseType, state,
                cookieToken(request), IpUtil.get(request));
        // 标准 OIDC 行为：302 回 SP 回调（code / error）或 302 回控制台登录页（未登录）
        return org.springframework.http.ResponseEntity.status(302)
                .location(java.net.URI.create(target))
                .build();
    }

    @GetMapping("/oauth/userinfo")
    public Map<String, Object> userinfo(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        HttpServletRequest request) {
        return ssoService.userinfo(authorization, IpUtil.get(request));
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> discovery() {
        return ssoService.discovery();
    }

    @GetMapping("/oauth/jwks")
    public Map<String, Object> jwks() {
        return ssoService.jwks();
    }

    /**
     * token 端点兼容三种编码：query 串、form 表单（OIDC 标准）、JSON body。
     * 实测本环境 Tomcat 对 POST 表单体解析结果为空（参数与 body 均原样可读），
     * 故 query 取不到时手动解析请求体兜底，两条路都能走到协议语义。
     */
    @PostMapping("/oauth/token")
    public Map<String, Object> token(HttpServletRequest request) throws Exception {
        Map<String, String> p = new java.util.LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                p.put(k, v[0]);
            }
        });
        if (!p.containsKey("client_id") || !p.containsKey("code")) {
            StringBuilder body = new StringBuilder();
            try (var reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            String raw = body.toString().trim();
            if (!raw.isEmpty()) {
                if (raw.startsWith("{")) {
                    Map<String, Object> json = ssoService.parseJson(raw);
                    json.forEach((k, v) -> p.putIfAbsent(k, String.valueOf(v)));
                } else {
                    for (String pair : raw.split("&")) {
                        int idx = pair.indexOf('=');
                        if (idx > 0) {
                            p.putIfAbsent(java.net.URLDecoder.decode(pair.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8),
                                    java.net.URLDecoder.decode(pair.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        }
        String clientId = p.get("client_id");
        String clientSecret = p.get("client_secret");
        String code = p.get("code");
        String redirectUri = p.get("redirect_uri");
        String grantType = p.getOrDefault("grant_type", "authorization_code");
        if (clientId == null || clientSecret == null || code == null || redirectUri == null) {
            throw new com.zimo.gateway.common.exception.BizException(
                    com.zimo.gateway.common.result.ResultCode.BAD_REQUEST.getCode(),
                    "缺少必填参数：client_id / client_secret / code / redirect_uri");
        }
        return ssoService.token(clientId, clientSecret, code, redirectUri, grantType, IpUtil.get(request));
    }

    // ------------------------------------------------------ 工具

    /**
     * IdP 会话 = gw_token Cookie（登录页写入，与控制台同一令牌）。
     * 兼容 Authorization: Bearer —— 便于用 curl 直接模拟已登录浏览器调试授权端点。
     */
    private String cookieToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("gw_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : null;
    }

    private String requireField(Map<String, String> body, String key, String message) {
        String value = body == null ? null : body.get(key);
        if (value == null || value.isBlank()) {
            throw new com.zimo.gateway.common.exception.BizException(
                    com.zimo.gateway.common.result.ResultCode.BAD_REQUEST.getCode(), message);
        }
        return value;
    }
}
