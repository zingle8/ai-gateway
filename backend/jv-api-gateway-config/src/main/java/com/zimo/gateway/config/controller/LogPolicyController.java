package com.zimo.gateway.config.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.config.entity.SysConfigEntity;
import com.zimo.gateway.config.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志管控（v0.2 第 14 项）：
 *
 * <ul>
 *   <li>{@code GET|PUT /policies/log-levels} —— 动态日志级别（JSON：logger → 级别），
 *       各服务 10 秒内自动应用，免重启；</li>
 *   <li>{@code GET /policies/log-files} —— 日志文件量与滚动策略概览（按模块聚合）。</li>
 * </ul>
 *
 * <p>日志目录由 {@code gateway.log.home} 指定（默认 logs/app，与 logback 配置一致），
 * 仅统计该目录下的平铺文件，不递归子目录。
 */
@Slf4j
@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class LogPolicyController {

    public static final String LEVELS_KEY = "platform.log.levels";
    private static final Set<String> LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF");
    /** 文件名：2026-11-13--00--api-gateway--jv-api-gateway-user--运行--INFO.txt */
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "--api-gateway--([^-]+(?:-[^-]+)*?)--(运行|采集上报)--(INFO|WARN|ERROR)\\.txt$");

    private final SysConfigMapper mapper;
    private final ObjectMapper objectMapper;

    @Value("${gateway.log.home:logs/app}")
    private String logHome;

    @GetMapping("/log-levels")
    public R<Map<String, String>> logLevels() {
        SysConfigEntity entity = mapper.selectByKey(LEVELS_KEY);
        if (entity == null || entity.getConfigValue() == null || entity.getConfigValue().isBlank()) {
            return R.ok(Map.of());
        }
        try {
            return R.ok(objectMapper.readValue(entity.getConfigValue(), new TypeReference<LinkedHashMap<String, String>>() {
            }));
        } catch (Exception e) {
            log.warn("[日志] 级别配置解析失败，按空处理：{}", e.getMessage());
            return R.ok(Map.of());
        }
    }

    /** 全量覆盖：非法级别整单拒绝，保存后各服务 10 秒内应用 */
    @PutMapping("/log-levels")
    public R<Map<String, String>> saveLogLevels(@RequestBody Map<String, String> body) {
        Map<String, String> clean = new LinkedHashMap<>();
        body.forEach((logger, level) -> {
            if (logger == null || logger.isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "logger 名称不能为空");
            }
            String upper = level == null ? "" : level.trim().toUpperCase();
            if (!LEVELS.contains(upper)) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                        "级别只能是 TRACE / DEBUG / INFO / WARN / ERROR / OFF：" + logger + "=" + level);
            }
            clean.put(logger.trim(), upper);
        });
        String json;
        try {
            json = objectMapper.writeValueAsString(clean);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "级别配置序列化失败");
        }
        SysConfigEntity entity = mapper.selectByKey(LEVELS_KEY);
        if (entity == null) {
            entity = new SysConfigEntity();
            entity.setConfigKey(LEVELS_KEY);
            entity.setConfigValue(json);
            entity.setValueType("JSON");
            entity.setConfigSource("DB");
            entity.setPlatformName("API网关平台");
            entity.setAppName("jv-api-gateway");
            entity.setServiceName("*");
            entity.setRemark("动态日志级别（logger → 级别，各服务 10 秒内应用）");
            mapper.insert(entity);
        } else {
            mapper.updateValue(LEVELS_KEY, json);
        }
        log.info("[日志] 动态级别配置已更新：{}", json);
        return R.ok(clean);
    }

    /** 日志文件概览：按模块聚合大小 / 文件数 / ERROR 文件，附带滚动策略说明 */
    @GetMapping("/log-files")
    public R<Map<String, Object>> logFiles() {
        File dir = new File(logHome);
        Map<String, ModuleStat> stats = new LinkedHashMap<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    Matcher m = FILE_PATTERN.matcher(file.getName());
                    if (!m.find()) {
                        continue;
                    }
                    ModuleStat stat = stats.computeIfAbsent(m.group(1), ModuleStat::new);
                    stat.add(file, m.group(2), m.group(3));
                }
            }
        }
        List<Map<String, Object>> modules = new ArrayList<>();
        stats.values().stream()
                .sorted(Comparator.comparingLong((ModuleStat s) -> s.bytes).reversed())
                .forEach(s -> modules.add(s.toMap()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logHome", dir.getAbsolutePath());
        result.put("exists", dir.isDirectory());
        result.put("modules", modules);
        result.put("retention", Map.of(
                "runInfoHistoryHours", 168,
                "runErrorHistoryHours", 336,
                "infoSizeCap", "2GB",
                "warnSizeCap", "1GB",
                "errorSizeCap", "1GB"));
        return R.ok(result);
    }

    /** 单模块日志统计 */
    private static class ModuleStat {
        private final String module;
        private long bytes;
        private long errorBytes;
        private int files;
        private String latest = "";

        ModuleStat(String module) {
            this.module = module;
        }

        void add(File file, String type, String level) {
            bytes += file.length();
            files++;
            if ("ERROR".equals(level)) {
                errorBytes += file.length();
            }
            String name = file.getName();
            int idx = name.indexOf("--api-gateway--");
            String stamp = idx > 0 ? name.substring(0, idx) : name;
            if (stamp.compareTo(latest) > 0) {
                latest = stamp;
            }
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("module", module);
            map.put("files", files);
            map.put("sizeMb", Math.round(bytes / 1024.0 / 1024.0 * 100) / 100.0);
            map.put("errorMb", Math.round(errorBytes / 1024.0 / 1024.0 * 100) / 100.0);
            map.put("latest", latest);
            return map;
        }
    }
}
