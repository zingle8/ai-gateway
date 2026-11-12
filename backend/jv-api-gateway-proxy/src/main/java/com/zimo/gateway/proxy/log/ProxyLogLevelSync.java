package com.zimo.gateway.proxy.log;

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
 * 网关动态日志级别（v0.2 第 14 项）。
 *
 * <p>proxy 不依赖 common 模块（避免引入 Servlet 栈），因此本地保留一份实现：
 * 每 10 秒读取 {@code platform.log.levels}（JSON：logger → 级别），
 * 通过 {@link LoggingSystem} 在线调整 logback，免重启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyLogLevelSync {

    private static final String CONFIG_KEY = "platform.log.levels";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "proxy-log-level-sync");
        t.setDaemon(true);
        return t;
    });

    private volatile String appliedJson = "";

    private volatile Map<String, String> appliedLevels = Map.of();

    @PostConstruct
    public void start() {
        scheduler.scheduleWithFixedDelay(this::syncQuietly, 12, 10, TimeUnit.SECONDS);
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
            LoggingSystem system = LoggingSystem.get(getClass().getClassLoader());
            // 配置里删掉的 logger 回落 INFO
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
            appliedLevels = levels;
            appliedJson = json;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // 配置未初始化
        } catch (Exception e) {
            if (LogThrottle.allow("log.level.sync.error", 1)) {
                log.warn("[日志] 动态级别同步失败：{}", e.getMessage());
            }
        }
    }
}
