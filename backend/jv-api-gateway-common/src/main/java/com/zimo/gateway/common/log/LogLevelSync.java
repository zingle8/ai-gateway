package com.zimo.gateway.common.log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 动态日志级别（v0.2 第 14 项）：各服务每 10 秒读取平台配置
 * {@code platform.log.levels}（tb_sys_config，JSON：logger → 级别），
 * 通过 Spring Boot {@link LoggingSystem} 在线调整 logback 级别，免重启。
 *
 * <pre>
 * {"com.zimo.gateway.proxy":"DEBUG","com.zimo.gateway.trace":"WARN","root":"INFO"}
 * </pre>
 *
 * <p>约束：只接受 TRACE / DEBUG / INFO / WARN / ERROR / OFF；配置表未创建或
 * 读取失败时保持现状（不打扰业务）；改动通过比较 JSON 文本生效，相同内容不重复应用。
 * 应用范围为本服务进程，集群多实例各自 10 秒内对齐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogLevelSync {

    private static final String CONFIG_KEY = "platform.log.levels";
    private static final long INTERVAL_SECONDS = 10L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "log-level-sync");
        t.setDaemon(true);
        return t;
    });

    private volatile String appliedJson = "";

    /** 上一次应用的配置，用于把「被移除的 logger」回落为 INFO */
    private volatile Map<String, String> appliedLevels = Map.of();

    @PostConstruct
    public void start() {
        scheduler.scheduleWithFixedDelay(this::syncQuietly, 12, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    private void syncQuietly() {
        try {
            String json = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM tb_sys_config WHERE config_key = ?", String.class, CONFIG_KEY);
            if (json == null || json.isBlank() || json.equals(appliedJson)) {
                return;
            }
            Map<String, String> levels = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
            apply(levels);
            appliedLevels = levels;
            appliedJson = json;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // 配置项未初始化：保持现状
        } catch (Exception e) {
            if (LogThrottle.allow("log.level.sync.error", 1)) {
                log.warn("[日志] 动态级别同步失败：{}", e.getMessage());
            }
        }
    }

    private void apply(Map<String, String> levels) {
        LoggingSystem system = LoggingSystem.get(getClass().getClassLoader());
        // 配置里删掉的 logger 回落 INFO，避免调成 DEBUG 后忘记回滚
        appliedLevels.keySet().stream()
                .filter(old -> !levels.containsKey(old))
                .forEach(old -> {
                    system.setLogLevel(old, LogLevel.INFO);
                    log.info("[日志] 动态级别已回落：{} → INFO", old);
                });
        levels.forEach((loggerName, rawLevel) -> {
            if (loggerName == null || loggerName.isBlank() || rawLevel == null) {
                return;
            }
            try {
                LogLevel level = LogLevel.valueOf(rawLevel.trim().toUpperCase());
                system.setLogLevel(loggerName.trim(), level);
                log.info("[日志] 动态级别已应用：{} → {}", loggerName.trim(), level);
            } catch (IllegalArgumentException e) {
                log.warn("[日志] 忽略非法级别配置：{}={}", loggerName, rawLevel);
            }
        });
    }
}
