package com.zimo.gateway.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.mapper.UserMapper;
import com.zimo.gateway.user.model.SessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在线会话管理：登录登记、并发端数控制、四粒度强制下线。
 *
 * <pre>
 * Redis 结构
 *   gw:sess:{sid}         HASH   会话明细（userId/username/ip/device/ua/appId/loginTime/lastActive）
 *   gw:sess:user:{userId} ZSET   score=loginTime，member=sid（便于踢最早）
 *   gw:sess:revoked:{sid} STRING 已吊销标记（TTL=8h，网关鉴权时命中即 401）
 *   gw:sess:revoke:log    LIST   吊销记录（LPUSH，只留最近 200 条）
 * </pre>
 *
 * <p>多端策略来自 tb_sys_config：
 * <ul>
 *   <li>platform.session.mode = MULTI（默认，多端共存）/ SINGLE（单端互踢）</li>
 *   <li>platform.session.max = 5（MULTI 模式下的并发上限，超限踢最早）</li>
 * </ul>
 *
 * <p>Redis 不可用时降级为单实例内存存储，功能可用但不跨实例共享。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SID_KEY = "gw:sess:";
    private static final String USER_KEY = "gw:sess:user:";
    private static final String REVOKED_KEY = "gw:sess:revoked:";
    private static final String LOG_KEY = "gw:sess:revoke:log";
    private static final String CONFIG_MODE = "platform.session.mode";
    private static final String CONFIG_MAX = "platform.session.max";

    private static final Duration TTL = Duration.ofHours(8);
    private static final int LOG_KEEP = 200;
    private static final long REDIS_RETRY_MILLIS = 30_000L;

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final PlatformConfigReader configReader;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    /** 内存兜底（Redis 不可用） */
    private final Map<String, Map<String, String>> localSessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> localUserIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> localRevoked = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> localLogs = new CopyOnWriteArrayList<>();
    private final AtomicLong redisDownUntil = new AtomicLong(0);

    public String newSid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 登录登记会话：先按策略踢掉多余会话，再写入本次会话。
     *
     * @param appId 归属平台（控制台 console，开放认证为接入方 appId）
     */
    public void register(UserEntity user, String sid, String ip, String ua, String appId) {
        long now = System.currentTimeMillis();
        String mode = configReader.get(CONFIG_MODE, "MULTI");
        int max = parseMax(configReader.get(CONFIG_MAX, "5"));

        List<SessionVO> existing = listByUser(user.getId());
        if ("SINGLE".equalsIgnoreCase(mode)) {
            existing.forEach(s -> revokeInternal(s, "单端登录互踢（platform.session.mode=SINGLE）",
                    user.getUsername(), null));
        } else if (existing.size() >= max) {
            existing.stream()
                    .sorted(Comparator.comparingLong(SessionVO::loginTime))
                    .limit(existing.size() - max + 1L)
                    .forEach(s -> revokeInternal(s, "并发上限 " + max + " 个，自动踢最早会话",
                            user.getUsername(), null));
        }

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("userId", String.valueOf(user.getId()));
        meta.put("username", user.getUsername());
        meta.put("nickname", user.getNickname() == null ? "" : user.getNickname());
        meta.put("ip", ip == null ? "" : ip);
        meta.put("device", parseDevice(ua));
        meta.put("deviceType", parseDeviceType(ua));
        meta.put("ua", ua == null ? "" : ua);
        meta.put("appId", appId == null || appId.isBlank() ? "console" : appId);
        meta.put("loginTime", String.valueOf(now));
        meta.put("lastActive", String.valueOf(now));

        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.opsForHash().putAll(SID_KEY + sid, meta);
                redis.expire(SID_KEY + sid, TTL);
                redis.opsForZSet().add(USER_KEY + user.getId(), sid, now);
                redis.expire(USER_KEY + user.getId(), TTL);
                return;
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        localSessions.put(sid, meta);
        localUserIndex.computeIfAbsent(user.getId(), k -> ConcurrentHashMap.newKeySet()).add(sid);
    }

    /** 在线会话（全部账号，管理员视角） */
    public List<SessionVO> listAll() {
        List<SessionVO> list = new ArrayList<>();
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Set<String> sids = redis.keys(SID_KEY + "*");
                if (sids != null) {
                    for (String key : sids) {
                        String sid = key.substring(SID_KEY.length());
                        if (sid.contains(":")) {
                            continue; // 跳过 gw:sess:user:* / revoked / log
                        }
                        Map<Object, Object> raw = redis.opsForHash().entries(key);
                        SessionVO vo = toVO(sid, raw);
                        if (vo != null) {
                            list.add(vo);
                        }
                    }
                }
                return sorted(list);
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        localSessions.forEach((sid, meta) -> {
            SessionVO vo = toVO(sid, new LinkedHashMap<>(meta));
            if (vo != null) {
                list.add(vo);
            }
        });
        return sorted(list);
    }

    /** 某账号的在线会话 */
    public List<SessionVO> listByUser(Long userId) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Set<String> sids = redis.opsForZSet().range(USER_KEY + userId, 0, -1);
                List<SessionVO> list = new ArrayList<>();
                if (sids != null) {
                    for (String sid : sids) {
                        Map<Object, Object> raw = redis.opsForHash().entries(SID_KEY + sid);
                        SessionVO vo = toVO(sid, raw);
                        if (vo == null) {
                            redis.opsForZSet().remove(USER_KEY + userId, sid);
                        } else {
                            list.add(vo);
                        }
                    }
                }
                return sorted(list);
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        List<SessionVO> list = new ArrayList<>();
        for (String sid : localUserIndex.getOrDefault(userId, Set.of())) {
            SessionVO vo = toVO(sid, new LinkedHashMap<>(localSessions.getOrDefault(sid, Map.of())));
            if (vo != null) {
                list.add(vo);
            }
        }
        return sorted(list);
    }

    /**
     * 强制下线（四粒度）。
     *
     * @param scope    SESSION / PLATFORM / DEVICE / ALL
     * @param operator 操作人（记入吊销记录）
     * @return 实际下线的会话数
     */
    public int kick(String scope, String sid, Long userId, String appId, String device,
                    String deviceType, String reason, String operator) {
        String normalized = scope == null ? "" : scope.trim().toUpperCase();
        return switch (normalized) {
            case "SESSION" -> {
                SessionVO vo = find(sid);
                if (vo == null) {
                    throw new BizException(ResultCode.NOT_FOUND.getCode(), "会话不存在或已下线：" + sid);
                }
                revokeInternal(vo, reason == null ? "手动踢下线（单会话）" : reason, vo.username(), operator);
                yield 1;
            }
            case "PLATFORM" -> {
                requireUser(userId);
                List<SessionVO> targets = listByUser(userId).stream()
                        .filter(s -> appId != null && appId.equals(s.appId()))
                        .toList();
                targets.forEach(s -> revokeInternal(s,
                        reason == null ? "手动踢下线（平台 " + appId + "）" : reason, operator, operator));
                yield targets.size();
            }
            case "DEVICE" -> {
                requireUser(userId);
                List<SessionVO> targets = listByUser(userId).stream()
                        .filter(s -> device != null && (s.device().equals(device) || s.device().contains(device)))
                        .toList();
                targets.forEach(s -> revokeInternal(s,
                        reason == null ? "手动踢下线（端 " + device + "）" : reason, operator, operator));
                yield targets.size();
            }
            case "DEVICE_TYPE" -> {
                requireUser(userId);
                String type = deviceType == null ? "" : deviceType.trim().toUpperCase();
                if (!Set.of("PHONE", "TABLET", "PC", "SERVER").contains(type)) {
                    throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                            "不支持的设备类型：" + deviceType + "（可选 PHONE / TABLET / PC / SERVER）");
                }
                List<SessionVO> targets = listByUser(userId).stream()
                        .filter(s -> type.equals(s.deviceType()))
                        .toList();
                targets.forEach(s -> revokeInternal(s,
                        reason == null ? "手动踢下线（" + deviceTypeText(type) + "端全部会话）" : reason,
                        operator, operator));
                yield targets.size();
            }
            case "ALL" -> kickAll(userId, reason, operator);
            default -> throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "不支持的下线粒度：" + scope + "（可选 SESSION / PLATFORM / DEVICE / DEVICE_TYPE / ALL）");
        };
    }

    /** 全端踢：token_version +1（所有已签发令牌立即失效）+ 清空会话 */
    public int kickAll(Long userId, String reason, String operator) {
        requireUser(userId);
        List<SessionVO> targets = listByUser(userId);
        targets.forEach(s -> revokeInternal(s, reason == null ? "手动踢下线（全端）" : reason,
                operator, operator));
        UserEntity user = userMapper.selectById(userId);
        if (user != null) {
            UserEntity update = new UserEntity();
            update.setId(userId);
            update.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
            update.setUpdateTime(LocalDateTime.now());
            userMapper.update(update);
        }
        return targets.size();
    }

    /** 退出登录：清空该账号全部会话（token_version 由 UserService.logout 自增） */
    public void clearByUser(Long userId, String username) {
        listByUser(userId).forEach(s -> revokeInternal(s, "退出登录", username, username));
    }

    /** 会话是否已被吊销（供网关校验；Redis 不可用时返回 false，保持可用性） */
    public boolean isRevoked(String sid) {
        if (sid == null || sid.isBlank()) {
            return false;
        }
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                return Boolean.TRUE.equals(redis.hasKey(REVOKED_KEY + sid));
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        Long expireAt = localRevoked.get(sid);
        return expireAt != null && expireAt > System.currentTimeMillis();
    }

    /** 最近吊销记录（控制台「令牌吊销记录」页） */
    public List<Map<String, Object>> recentRevokes(int limit) {
        int size = Math.min(Math.max(limit, 1), LOG_KEEP);
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                List<String> raw = redis.opsForList().range(LOG_KEY, 0, size - 1L);
                List<Map<String, Object>> list = new ArrayList<>();
                if (raw != null) {
                    for (String json : raw) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> item = objectMapper.readValue(json, Map.class);
                            list.add(item);
                        } catch (Exception ignore) {
                            // 跳过脏数据
                        }
                    }
                }
                return list;
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        return localLogs.stream().limit(size).toList();
    }

    // ---------------------------------------------------------------- 内部

    private SessionVO find(String sid) {
        if (sid == null || sid.isBlank()) {
            return null;
        }
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                Map<Object, Object> raw = redis.opsForHash().entries(SID_KEY + sid);
                SessionVO vo = toVO(sid, raw);
                if (vo != null) {
                    return vo;
                }
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        Map<String, String> meta = localSessions.get(sid);
        return meta == null ? null : toVO(sid, new LinkedHashMap<>(meta));
    }

    private void revokeInternal(SessionVO session, String reason, String username, String operator) {
        String sid = session.sid();
        long expireAt = System.currentTimeMillis() + TTL.toMillis();
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.delete(SID_KEY + sid);
                redis.opsForZSet().remove(USER_KEY + session.userId(), sid);
                redis.opsForValue().set(REVOKED_KEY + sid, "1", TTL);
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        localSessions.remove(sid);
        Set<String> sids = localUserIndex.get(session.userId());
        if (sids != null) {
            sids.remove(sid);
        }
        localRevoked.put(sid, expireAt);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sid", sid);
        entry.put("userId", session.userId());
        entry.put("username", session.username());
        entry.put("ip", session.ip());
        entry.put("device", session.device());
        entry.put("appId", session.appId());
        entry.put("loginTime", session.loginTime());
        entry.put("revokeTime", System.currentTimeMillis());
        entry.put("reason", reason);
        entry.put("operator", operator == null ? "system" : operator);
        recordRevoke(entry);
        log.info("[会话] 强制下线 {}（{}，{}）：{}", username == null ? session.username() : username,
                sid, session.device(), reason);
    }

    private void recordRevoke(Map<String, Object> log) {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                redis.opsForList().leftPush(LOG_KEY, objectMapper.writeValueAsString(log));
                redis.opsForList().trim(LOG_KEY, 0, LOG_KEEP - 1L);
                return;
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        localLogs.add(0, log);
        while (localLogs.size() > LOG_KEEP) {
            localLogs.remove(localLogs.size() - 1);
        }
    }

    private SessionVO toVO(String sid, Map<?, ?> raw) {
        if (raw == null || raw.isEmpty() || raw.get("userId") == null) {
            return null;
        }
        long loginTime = parseLong(raw.get("loginTime"));
        long lastActive = parseLong(raw.get("lastActive"));
        long now = System.currentTimeMillis();
        return new SessionVO(sid,
                parseLong(raw.get("userId")),
                str(raw.get("username")),
                str(raw.get("nickname")),
                str(raw.get("ip")),
                str(raw.get("device")),
                // 历史会话无 deviceType 字段：按 UA 现算兜底
                raw.get("deviceType") == null || String.valueOf(raw.get("deviceType")).isBlank()
                        ? parseDeviceType(str(raw.get("ua"))) : str(raw.get("deviceType")),
                str(raw.get("ua")),
                str(raw.get("appId")),
                loginTime, lastActive, Math.max(0, (now - loginTime) / 1000));
    }

    private List<SessionVO> sorted(List<SessionVO> list) {
        list.sort(Comparator.comparingLong(SessionVO::loginTime).reversed());
        return list;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "请指定账号");
        }
    }

    private StringRedisTemplate redis() {
        if (System.currentTimeMillis() < redisDownUntil.get()) {
            return null;
        }
        return redisProvider.getIfAvailable();
    }

    private void markRedisDown(Exception e) {
        redisDownUntil.set(System.currentTimeMillis() + REDIS_RETRY_MILLIS);
        log.warn("[会话] Redis 不可用，降级为本地内存存储（30 秒后重试）：{}", e.getMessage());
    }

    private static int parseMax(String value) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return 5;
        }
    }

    private static long parseLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * UA → 设备指纹（浏览器 / 操作系统），用于「按端踢」与列表展示。
     */
    public static String parseDevice(String ua) {
        if (ua == null || ua.isBlank()) {
            return "未知设备";
        }
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Safari/") ? "Safari"
                : ua.startsWith("curl") ? "curl"
                : ua.contains("Postman") ? "Postman"
                : "其他";
        String os = ua.contains("Mac OS X") || ua.contains("Macintosh") ? "macOS"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Linux") ? "Linux"
                : "未知系统";
        return browser + " / " + os;
    }

    /** 设备端类型文案（踢下线提示用） */
    public static String deviceTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "PHONE" -> "手机";
            case "TABLET" -> "平板";
            case "PC" -> "电脑";
            case "SERVER" -> "服务端";
            default -> "未知";
        };
    }

    /**
     * UA → 设备端类型（v0.2 多端增强）：手机 / 平板 / 电脑 / 服务端（curl、Postman 等 API 调用）。
     * 平板优先于手机判断（iPad 的 UA 同时含 Macintosh 与移动标识）。
     */
    public static String parseDeviceType(String ua) {
        if (ua == null || ua.isBlank()) {
            return "SERVER";
        }
        String upper = ua.toUpperCase();
        if (upper.contains("IPAD") || upper.contains("TABLET")) {
            return "TABLET";
        }
        if (upper.contains("MOBILE") || upper.contains("IPHONE") || upper.contains("ANDROID")
                || upper.contains("HARMONY") || upper.contains("MICROMESSENGER")) {
            return "PHONE";
        }
        if (ua.startsWith("curl") || ua.contains("Postman") || ua.contains("Java/")
                || ua.contains("okhttp") || ua.contains("Python-requests") || ua.contains("axios")) {
            return "SERVER";
        }
        return "PC";
    }
}
