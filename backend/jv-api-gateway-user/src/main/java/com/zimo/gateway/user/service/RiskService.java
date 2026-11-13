package com.zimo.gateway.user.service;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.user.entity.ApiKeyEntity;
import com.zimo.gateway.user.entity.ApiKeyLogEntity;
import com.zimo.gateway.user.entity.LoginLogEntity;
import com.zimo.gateway.user.entity.RiskEventEntity;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.mapper.ApiKeyLogMapper;
import com.zimo.gateway.user.mapper.ApiKeyMapper;
import com.zimo.gateway.user.mapper.LoginLogMapper;
import com.zimo.gateway.user.mapper.RiskEventMapper;
import com.zimo.gateway.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 鉴权风控（v0.2 第 13 项）。
 *
 * <pre>
 * 用户维度：登录成功 / 失败都落 tb_login_log，命中规则打风险标记并登记风控事件
 *           （陌生 IP / 非工作时段 / 连续失败），处置动作 = 禁用 / 启用 / 解锁 / 强制下线
 * API Key ：网关按分钟写 Redis 调用计数（gw:risk:key:{keyId}:{yyyyMMddHHmm} 的 calls / errors），
 *           本服务每分钟巡检：调用量突增或错误率异常 → 登记事件 + 自动停用（熔断式）；
 *           另有临期 / 长期未用预警清单（不自动处置，人工决定续期或吊销）
 * 事件处置：tb_gw_risk_event 原地更新 action / handler / handle_remark / handle_time 留痕
 * </pre>
 *
 * <p>边界：不引入设备指纹库与 IP 归属地服务，「陌生 IP」按该账号历史成功登录 IP 判定；
 * 「非工作时段」按 22:00–07:00 固定口径；熔断只停用 Key，不自动恢复（需人工启用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    /** 网关调用计数：gw:risk:key:{keyId}:{yyyyMMddHHmm} → hash{calls, errors} */
    public static final String KEY_STAT_PREFIX = "gw:risk:key:";
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 同类事件去重窗口（分钟） */
    private static final int DEDUPE_MINUTES = 10;
    /** 未被标记为风险的调用量阈值：单分钟调用 ≥ 20 才判定突增 */
    private static final int SPIKE_MIN_CALLS = 20;
    /** 突增倍数：超过基线均值的 3 倍 */
    private static final int SPIKE_TIMES = 3;
    /** 错误率熔断：单分钟调用 ≥ 10 且错误率 ≥ 30% */
    private static final int ERROR_MIN_CALLS = 10;
    private static final double ERROR_RATE_LIMIT = 0.3;
    /** 基线窗口：前 30 分钟 */
    private static final int BASELINE_MINUTES = 30;
    /** Key 临期预警阈值（天） */
    private static final int EXPIRING_DAYS = 7;
    /** Key 长期未用阈值（天） */
    private static final int UNUSED_DAYS = 30;

    private final LoginLogMapper loginLogMapper;
    private final RiskEventMapper riskEventMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyLogMapper apiKeyLogMapper;
    private final UserMapper userMapper;
    private final SessionService sessionService;
    private final LoginPolicyService loginPolicyService;
    private final IpBanService ipBanService;

    /** 延迟注入，避免 UserServiceImpl ←→ RiskService 构造器循环依赖 */
    private final ObjectProvider<UserService> userServiceProvider;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // ============================================================ 登录侧

    /**
     * IP 封禁校验（登录入口调用）：命中封禁清单即 403，并登记 IP 风控事件（10 分钟去重）。
     * 任何异常都不影响登录主流程（校验失败视为未封禁）。
     */
    public void enforceIpBan(String ip) {
        try {
            if (ip == null || ip.isBlank() || !ipBanService.isBanned(ip)) {
                return;
            }
            createEvent("IP", ip, ip, "IP_BAN_HIT", "HIGH", "被封禁 IP 尝试登录（已拒绝）");
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "该 IP 已被风控封禁，如有疑问请联系管理员");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[风控] IP 封禁校验异常，放行：{}", e.getMessage());
        }
    }

    /**
     * 记录一次登录（成功 / 失败）并做风险识别。任何异常都不允许影响登录主流程。
     *
     * @param failCount 失败时传入当次累计失败次数，成功传 0
     */
    public void recordLogin(String username, Long userId, String ip, String ua, String appId,
                            boolean success, String failReason, int failCount) {
        try {
            List<String> types = new ArrayList<>();
            List<String> reasons = new ArrayList<>();
            String level = null;

            // 连续失败达阈值（本次登录在密码校验处被拦或已锁定）
            if (!success && failCount >= loginPolicyService.lockThreshold()) {
                types.add("LOGIN_BRUTE_FORCE");
                reasons.add("连续失败 " + failCount + " 次，达锁定阈值 " + loginPolicyService.lockThreshold());
                level = "HIGH";
            }
            if (success) {
                // 陌生 IP：历史成功登录过，但本次 IP 从未出现过
                if (loginLogMapper.countSuccess(username) > 0
                        && loginLogMapper.countSuccessByIp(username, ip) == 0) {
                    types.add("LOGIN_NEW_IP");
                    reasons.add("从未在该 IP 成功登录过：" + ip);
                    level = level == null ? "MEDIUM" : level;
                }
                int hour = LocalDateTime.now().getHour();
                if (hour >= 22 || hour < 7) {
                    types.add("LOGIN_OFF_HOURS");
                    reasons.add("非工作时段登录（" + hour + " 时）");
                    if (level == null) {
                        level = "LOW";
                    }
                }
            }

            LoginLogEntity row = new LoginLogEntity();
            row.setUsername(username);
            row.setUserId(userId);
            row.setIp(ip);
            row.setUserAgent(truncate(ua, 255));
            row.setAppId(appId);
            row.setSuccess(success ? 1 : 0);
            row.setFailReason(truncate(failReason, 128));
            row.setFailCount(success ? 0 : failCount);
            row.setRiskType(types.isEmpty() ? null : String.join(",", types));
            row.setRiskLevel(level);
            row.setRiskReason(truncate(String.join("；", reasons), 255));
            row.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(row);

            // 每个风险类型单独登记一条事件（失败不影响登录）
            for (int i = 0; i < types.size(); i++) {
                createEvent("USER", String.valueOf(userId == null ? username : userId), username,
                        types.get(i), i == 0 ? level : levelOf(types.get(i)), reasons.get(i));
            }
        } catch (Exception e) {
            log.warn("[风控] 登录记录写入失败 username={}：{}", username, e.getMessage());
        }
    }

    private String levelOf(String type) {
        return switch (type) {
            case "LOGIN_BRUTE_FORCE", "KEY_SPIKE", "KEY_ERROR_RATE" -> "HIGH";
            case "LOGIN_NEW_IP" -> "MEDIUM";
            default -> "LOW";
        };
    }

    // ============================================================ 查询

    public Map<String, Object> summary() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayEvents", riskEventMapper.countSince(dayStart));
        result.put("openEvents", riskEventMapper.countOpen());
        result.put("highToday", riskEventMapper.countHighSince(dayStart));
        result.put("failLoginsToday", loginLogMapper.countFailSinceAll(dayStart));
        result.put("byType", riskEventMapper.countByType(LocalDateTime.now().minusDays(7)));
        result.put("keyWarnings", keyWarnings().size());
        return result;
    }

    public Map<String, Object> pageEvents(String subjectType, String status, String riskLevel,
                                          String keyword, int page, int size) {
        long offset = (long) (Math.max(page, 1) - 1) * Math.max(size, 1);
        List<RiskEventEntity> records = riskEventMapper.selectPage(subjectType, status, riskLevel, keyword,
                offset, size);
        long total = riskEventMapper.countPage(subjectType, status, riskLevel, keyword);
        return Map.of("records", records, "total", total, "page", page, "size", size);
    }

    public Map<String, Object> pageLogins(String username, Integer success, boolean riskOnly,
                                          int page, int size) {
        long offset = (long) (Math.max(page, 1) - 1) * Math.max(size, 1);
        List<LoginLogEntity> records = loginLogMapper.selectPage(username, success, riskOnly, offset, size);
        long total = loginLogMapper.countPage(username, success, riskOnly);
        return Map.of("records", records, "total", total, "page", page, "size", size);
    }

    /**
     * Key 预警清单：临期（≤7 天，含已过期）与长期未用（>30 天未调用，且创建超过 7 天）。
     * 只给建议不自动处置——续期 / 吊销由管理员决定。
     */
    public List<Map<String, Object>> keyWarnings() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiKeyEntity key : apiKeyMapper.selectAll()) {
            if (key.getStatus() == null || key.getStatus() != 1) {
                continue;
            }
            boolean expired = key.getExpireAt() != null && key.getExpireAt().isBefore(now);
            boolean expiring = key.getExpireAt() != null
                    && !expired && !key.getExpireAt().isAfter(now.plusDays(EXPIRING_DAYS));
            if (expired || expiring) {
                rows.add(warningRow(key, expired ? "EXPIRED" : "EXPIRING",
                        expired ? "已过期，调用将被网关拒绝" : "将在 " + EXPIRING_DAYS + " 天内到期",
                        key.getExpireAt() == null ? null
                                : java.time.Duration.between(now, key.getExpireAt()).toDays()));
                continue;
            }
            boolean createdLongAgo = key.getCreateTime() != null
                    && key.getCreateTime().isBefore(now.minusDays(7));
            boolean neverUsed = key.getLastUsedAt() == null;
            boolean longIdle = key.getLastUsedAt() != null && key.getLastUsedAt().isBefore(now.minusDays(UNUSED_DAYS));
            if (createdLongAgo && (neverUsed || longIdle)) {
                long idleDays = key.getLastUsedAt() == null
                        ? java.time.Duration.between(key.getCreateTime(), now).toDays()
                        : java.time.Duration.between(key.getLastUsedAt(), now).toDays();
                rows.add(warningRow(key, "UNUSED", neverUsed ? "签发后从未使用" : "超过 " + UNUSED_DAYS + " 天未调用", idleDays));
            }
        }
        return rows;
    }

    private Map<String, Object> warningRow(ApiKeyEntity key, String type, String suggestion, Long days) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", key.getId());
        row.put("keyId", key.getKeyId());
        row.put("name", key.getName());
        row.put("type", type);
        row.put("expireAt", key.getExpireAt());
        row.put("lastUsedAt", key.getLastUsedAt());
        row.put("lastUsedIp", key.getLastUsedIp());
        row.put("days", days);
        row.put("suggestion", suggestion);
        return row;
    }

    // ============================================================ 处置

    /**
     * 处置风险事件。
     *
     * @param action ACK（仅确认）/ IGNORE（忽略）/ DISABLE_USER / ENABLE_USER /
     *               UNLOCK_USER / KICK_USER / DISABLE_KEY / ENABLE_KEY
     */
    public RiskEventEntity handle(Long id, String action, String remark) {
        RiskEventEntity event = id == null ? null : riskEventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "风控事件不存在");
        }
        if (!"OPEN".equals(event.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该事件已处置，无需重复操作");
        }
        String act = action == null ? "ACK" : action.trim().toUpperCase();
        String operator = operator();

        switch (act) {
            case "ACK" -> { /* 仅确认 */ }
            case "IGNORE" -> { /* 忽略 */ }
            case "DISABLE_USER" -> updateUserStatus(event, 0);
            case "ENABLE_USER" -> updateUserStatus(event, 1);
            case "UNLOCK_USER" -> unlockUser(event);
            case "KICK_USER" -> kickUser(event, operator);
            case "DISABLE_KEY" -> updateKeyStatus(event, 0, operator);
            case "ENABLE_KEY" -> updateKeyStatus(event, 1, operator);
            case "BAN_IP" -> {
                String ip = requireIp(event);
                ipBanService.ban(ip, "风控处置封禁（事件 #" + event.getId() + "：" + event.getRiskType() + "）", operator);
            }
            case "UNBAN_IP" -> ipBanService.unban(requireIp(event), operator);
            default -> throw new BizException(ResultCode.BAD_REQUEST.getCode(), "不支持的处置动作：" + act);
        }

        String status = "IGNORE".equals(act) ? "IGNORED" : "HANDLED";
        riskEventMapper.handle(id, status, act, operator, truncate(remark, 255), LocalDateTime.now());
        log.info("[风控] 事件处置：id={} subject={}:{} action={} operator={}",
                id, event.getSubjectType(), event.getSubjectName(), act, operator);
        return riskEventMapper.selectById(id);
    }

    private void updateUserStatus(RiskEventEntity event, int status) {
        UserEntity user = requireUser(event);
        UserEntity update = new UserEntity();
        update.setId(user.getId());
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.update(update);
        if (status == 0) {
            // 禁用同时清掉在线会话，避免旧 token 继续用
            sessionService.kickAll(user.getId(), "风控处置：账号被禁用", operator());
        }
    }

    private void unlockUser(RiskEventEntity event) {
        UserEntity user = requireUser(event);
        userServiceProvider.getObject().unlockLogin(user.getId());
    }

    private void kickUser(RiskEventEntity event, String operator) {
        UserEntity user = requireUser(event);
        int affected = sessionService.kickAll(user.getId(), "风控处置：强制下线", operator);
        log.info("[风控] 强制下线：user={} 会话 {} 个", user.getUsername(), affected);
    }

    private void updateKeyStatus(RiskEventEntity event, int status, String operator) {
        ApiKeyEntity key = requireKey(event);
        apiKeyMapper.updateStatus(key.getId(), status);
        auditKey(key, status == 1 ? "ENABLE" : "DISABLE",
                "风控处置：" + (status == 1 ? "恢复启用" : "人工停用") + "（事件 #" + event.getId() + "，操作人 " + operator + "）");
    }

    private UserEntity requireUser(RiskEventEntity event) {
        if (!"USER".equals(event.getSubjectType())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该事件主体不是用户，无法执行该动作");
        }
        UserEntity user = userMapper.selectByUsername(event.getSubjectName());
        if (user == null && event.getSubjectId() != null && event.getSubjectId().matches("\\d+")) {
            user = userMapper.selectById(Long.valueOf(event.getSubjectId()));
        }
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "事件关联的用户不存在");
        }
        return user;
    }

    private ApiKeyEntity requireKey(RiskEventEntity event) {
        if (!"API_KEY".equals(event.getSubjectType())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该事件主体不是 API Key，无法执行该动作");
        }
        return apiKeyMapper.selectAll().stream()
                .filter(k -> k.getKeyId().equals(event.getSubjectId()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND.getCode(), "事件关联的 API Key 不存在"));
    }

    private String requireIp(RiskEventEntity event) {
        if (!"IP".equals(event.getSubjectType())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "该事件主体不是 IP（" + event.getSubjectType() + "），无法执行该动作");
        }
        return event.getSubjectId();
    }

    // ============================================================ Key 调用监控

    /**
     * 每分钟巡检上一分钟的 Key 调用计数：
     * ① 调用量突增（≥20 次且 > 前 30 分钟均值 3 倍）→ 登记事件 + 自动停用；
     * ② 错误率异常（≥10 次且错误率 ≥30%）→ 登记事件 + 自动停用。
     * Redis 不可用则跳过本轮（网关计数依赖 Redis，不阻塞其他功能）。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 75_000)
    public void monitorKeys() {
        if (redisTemplate == null) {
            return;
        }
        try {
            LocalDateTime target = LocalDateTime.now().minusMinutes(1);
            String minute = target.format(MINUTE);
            for (ApiKeyEntity key : apiKeyMapper.selectAll()) {
                if (key.getStatus() == null || key.getStatus() != 1) {
                    continue;
                }
                String base = KEY_STAT_PREFIX + key.getKeyId() + ":";
                long calls = counter(base + minute, "calls");
                if (calls <= 0) {
                    continue;
                }
                long errors = counter(base + minute, "errors");
                long sum = 0;
                int buckets = 0;
                for (int i = 2; i <= BASELINE_MINUTES + 1; i++) {
                    long v = counter(base + target.minusMinutes(i - 1).format(MINUTE), "calls");
                    if (v >= 0) {
                        sum += v;
                        buckets++;
                    }
                }
                double avg = buckets == 0 ? 0 : (double) sum / buckets;

                if (calls >= SPIKE_MIN_CALLS && calls > Math.max(10, avg * SPIKE_TIMES)) {
                    String detail = String.format("上一分钟调用 %d 次，前 %d 分钟均值 %.1f 次（超 %d 倍）",
                            calls, BASELINE_MINUTES, avg, SPIKE_TIMES);
                    if (createEvent("API_KEY", key.getKeyId(), key.getName(), "KEY_SPIKE", "HIGH", detail) != null) {
                        autoDisable(key, "调用量突增自动熔断：" + detail);
                    }
                } else if (calls >= ERROR_MIN_CALLS && errors * 1.0 / calls >= ERROR_RATE_LIMIT) {
                    String detail = String.format("上一分钟调用 %d 次、失败 %d 次（错误率 %.1f%%）",
                            calls, errors, errors * 100.0 / calls);
                    if (createEvent("API_KEY", key.getKeyId(), key.getName(), "KEY_ERROR_RATE", "HIGH", detail) != null) {
                        autoDisable(key, "错误率异常自动熔断：" + detail);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[风控] Key 调用监控巡检失败：{}", e.getMessage());
        }
    }

    /** 读哈希字段；键不存在返回 -1（用于区分「无数据」与「0 次」） */
    private long counter(String key, String field) {
        try {
            Object v = redisTemplate.opsForHash().get(key, field);
            return v == null ? -1 : Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return -1;
        }
    }

    private void autoDisable(ApiKeyEntity key, String reason) {
        apiKeyMapper.updateStatus(key.getId(), 0);
        auditKey(key, "AUTO_DISABLE", reason);
        log.warn("[风控] API Key 已自动停用：{}（{}）{}", key.getName(), key.getKeyId(), reason);
    }

    /** Key 审计：复用 tb_gw_api_key_log，写失败不影响主流程 */
    private void auditKey(ApiKeyEntity key, String action, String remark) {
        try {
            ApiKeyLogEntity row = new ApiKeyLogEntity();
            row.setKeyId(key.getKeyId());
            row.setKeyName(key.getName());
            row.setAction(action);
            row.setOperatorUserId(UserContext.getUserId());
            row.setOperatorName(operator());
            row.setRemark(truncate(remark, 255));
            apiKeyLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("[风控] Key 审计写入失败 action={} keyId={}：{}", action, key.getKeyId(), e.getMessage());
        }
    }

    // ============================================================ 工具

    /** 登记事件（同类事件 10 分钟内去重），返回 null 表示被去重 */
    private RiskEventEntity createEvent(String subjectType, String subjectId, String subjectName,
                                        String riskType, String level, String detail) {
        try {
            if (riskEventMapper.countRecent(subjectType, subjectId, riskType,
                    LocalDateTime.now().minusMinutes(DEDUPE_MINUTES)) > 0) {
                return null;
            }
            RiskEventEntity event = new RiskEventEntity();
            event.setSubjectType(subjectType);
            event.setSubjectId(subjectId);
            event.setSubjectName(subjectName);
            event.setRiskType(riskType);
            event.setRiskLevel(level == null ? "MEDIUM" : level);
            event.setDetail(truncate(detail, 500));
            event.setStatus("OPEN");
            event.setCreateTime(LocalDateTime.now());
            riskEventMapper.insert(event);
            return event;
        } catch (Exception e) {
            log.warn("[风控] 事件登记失败 type={} subject={}：{}", riskType, subjectName, e.getMessage());
            return null;
        }
    }

    private String operator() {
        return operatorName() == null ? "system" : operatorName();
    }

    private String operatorName() {
        String name = UserContext.getUsername();
        return name == null || name.isBlank() ? null : name;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
