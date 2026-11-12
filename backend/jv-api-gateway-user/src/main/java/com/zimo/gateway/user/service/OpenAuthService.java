package com.zimo.gateway.user.service;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.JwtUtil;
import com.zimo.gateway.user.entity.ApiKeyEntity;
import com.zimo.gateway.user.entity.OpenPlatformEntity;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.mapper.ApiKeyMapper;
import com.zimo.gateway.user.model.LoginRequest;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.model.OpenVerifyVO;
import com.zimo.gateway.user.model.RegisterRequest;
import com.zimo.gateway.user.service.impl.ApiKeyServiceImpl;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 开放认证：注册 / 登录 / 退出 / 令牌校验，供第三方平台对接。
 *
 * <p>总开关由系统设置 platform.open.auth.enabled 控制（10 秒内生效）。
 * 与平台自身登录共用同一套账号体系与 JWT 密钥：第三方拿到的 token
 * 与平台 token 等价，可在网关继续使用。
 *
 * <p>令牌校验支持两类：
 * <ul>
 *   <li>USER —— 用户 JWT：签名 / 过期 / 账号状态 / token_version</li>
 *   <li>API_KEY —— 应用级 Key（gwak-keyId-secret）：状态 / 有效期 / 密钥哈希</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAuthService {

    private static final String API_KEY_PREFIX = "gwak-";

    private final UserService userService;
    private final PermService permService;
    private final PlatformConfigReader configReader;
    private final JwtUtil jwtUtil;
    private final ApiKeyMapper apiKeyMapper;
    private final OpenPlatformService openPlatformService;

    public LoginVO register(RegisterRequest request, String ip) {
        requireEnabled();
        return userService.register(request, ip);
    }

    public LoginVO login(LoginRequest request, String ip) {
        requireEnabled();
        return userService.login(request.getUsername(), request.getPassword(), ip);
    }

    /**
     * 退出登录：自增 token_version，使该账号此前签发的所有 JWT 立即失效。
     * API Key 没有会话概念，退出对其无意义（停用请走 Key 管理）。
     */
    public void logout(String token) {
        requireEnabled();
        if (token == null || token.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "token 不能为空");
        }
        if (token.startsWith(API_KEY_PREFIX)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "API Key 无会话概念，无法退出；如需失效请在 Key 管理中停用或删除");
        }
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "令牌无效或已过期");
        }
        Long userId = claims.get("userId") == null ? null : Long.valueOf(String.valueOf(claims.get("userId")));
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "令牌缺少用户标识");
        }
        userService.logout(userId);
        log.info("[开放认证] 用户 {} 已退出登录，其历史令牌全部失效", userId);
    }

    /**
     * 令牌校验：按 type 或前缀自动识别用户 JWT / API Key。
     * 返回 valid=false 而不是抛 401，方便第三方按业务自行处理。
     *
     * @param appId 目标平台标识（可选）：传入时校验账号-平台授权，
     *              未授权返回 valid=false（reason 指明未授权平台）
     */
    public OpenVerifyVO verify(String token, String type, String appId) {
        requireEnabled();
        if (token == null || token.isBlank()) {
            return OpenVerifyVO.invalid(resolveType(token, type), "token 为空");
        }
        String tokenType = resolveType(token, type);
        return "API_KEY".equals(tokenType) ? verifyApiKey(token) : verifyUserToken(token, appId);
    }

    private String resolveType(String token, String type) {
        String wanted = type == null ? "" : type.trim().toUpperCase();
        if ("USER".equals(wanted) || "API_KEY".equals(wanted)) {
            return wanted;
        }
        return token != null && token.startsWith(API_KEY_PREFIX) ? "API_KEY" : "USER";
    }

    private OpenVerifyVO verifyUserToken(String token, String appId) {
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            return OpenVerifyVO.invalid("USER", "令牌无效或已过期");
        }
        Long userId = claims.get("userId") == null ? null : Long.valueOf(String.valueOf(claims.get("userId")));
        UserEntity user = userId == null ? null : userService.getById(userId);
        if (user == null) {
            return OpenVerifyVO.invalid("USER", "账号不存在");
        }
        if (Objects.equals(user.getStatus(), 0)) {
            return OpenVerifyVO.invalid("USER", "账号已禁用");
        }
        Integer tokenVersion = jwtUtil.getTokenVersion(token);
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion == null || tokenVersion != current) {
            return OpenVerifyVO.invalid("USER", "账号已退出登录，令牌已失效");
        }
        // 账号-平台授权校验：认证了 ≠ 能用（指定 appId 时生效）
        if (appId != null && !appId.isBlank()) {
            OpenPlatformEntity platform = openPlatformService.enabledPlatform(appId.trim());
            if (!openPlatformService.isAuthorized(userId, platform.getAppId())) {
                log.info("[开放认证] 账号 {} 未授权平台 {}，拒绝校验", userId, platform.getAppId());
                return OpenVerifyVO.invalid("USER", "账号未授权访问平台「" + platform.getAppName() + "」");
            }
        }
        UserContext.clear();
        return OpenVerifyVO.validUser(user, claims.getExpiration(), permService.permsOfUser(userId));
    }

    private OpenVerifyVO verifyApiKey(String raw) {
        if (!raw.startsWith(API_KEY_PREFIX)) {
            return OpenVerifyVO.invalid("API_KEY", "API Key 格式不正确，应为 gwak-<keyId>-<secret>");
        }
        String rest = raw.substring(API_KEY_PREFIX.length());
        int split = rest.indexOf('-');
        if (split <= 0 || split == rest.length() - 1) {
            return OpenVerifyVO.invalid("API_KEY", "API Key 格式不正确，应为 gwak-<keyId>-<secret>");
        }
        String keyId = rest.substring(0, split);
        String secret = rest.substring(split + 1);
        ApiKeyEntity key = apiKeyMapper.selectByKeyId(keyId);
        if (key == null) {
            return OpenVerifyVO.invalid("API_KEY", "无效的 API Key");
        }
        if (key.getStatus() == null || key.getStatus() == 0) {
            return OpenVerifyVO.invalid("API_KEY", "API Key 已停用");
        }
        if (key.getExpireAt() != null && key.getExpireAt().isBefore(LocalDateTime.now())) {
            return OpenVerifyVO.invalid("API_KEY", "API Key 已过期");
        }
        if (!ApiKeyServiceImpl.sha256Hex(secret).equals(key.getSecretHash())) {
            return OpenVerifyVO.invalid("API_KEY", "API Key 校验失败");
        }
        return OpenVerifyVO.validKey(key);
    }

    private void requireEnabled() {
        if (!configReader.openAuthEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "开放认证接口已关闭（系统设置 → platform.open.auth.enabled）");
        }
    }
}
