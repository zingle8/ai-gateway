package com.zimo.gateway.config.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.config.store.ConfigStore;
import com.zimo.gateway.config.store.NacosConfigStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础设施配置（v0.2 配置集中）。
 *
 * <p>把网关平台各服务本地化配置（DB / Redis / Nacos 连接、JWT secret、client-token、
 * 各服务 URL 等）集中到 Nacos dataId {@code gw-platform-infra.properties}（key=value 文本）。
 * 各服务通过 {@code spring.config.import: optional:nacos:gw-platform-infra.properties}
 * 拉取（本地 yml 保留同名默认值兜底），控制台修改发布后服务重启 / Nacos 刷新时生效。
 *
 * <p>client 采集模块不使用该配置（接入方自带配置），不受影响。
 */
@Slf4j
@RestController
@RequestMapping("/infra/config")
@RequiredArgsConstructor
public class InfraConfigController {

    public static final String INFRA_DATA_ID = "gw-platform-infra.properties";

    private final NacosConfigStore nacosStore;

    /** 基础设施配置全文（key=value 行），供「基础设施配置」页展示编辑 */
    @GetMapping
    public R<Map<String, Object>> detail() {
        String content = nacosStore.readAll(INFRA_DATA_ID);
        List<Map<String, Object>> entries = new ArrayList<>();
        if (content != null) {
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", trimmed.substring(0, eq).trim());
                row.put("value", trimmed.substring(eq + 1).trim());
                // 敏感项打码展示，编辑时原文保留
                row.put("sensitive", isSensitive(trimmed.substring(0, eq).trim()));
                entries.add(row);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dataId", INFRA_DATA_ID);
        data.put("content", content == null ? "" : content);
        data.put("entries", entries);
        data.put("updatedAt", LocalDateTime.now().withNano(0).toString());
        return R.ok(data);
    }

    /** 整份发布（覆盖 Nacos 上的 infra 配置文件） */
    @PutMapping
    public R<Map<String, Object>> save(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return R.fail(400, "配置内容不能为空");
        }
        // 基础校验：key=value 行
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.indexOf('=') <= 0) {
                return R.fail(400, "格式错误（应为 key=value）：" + trimmed);
            }
        }
        ConfigStore store = nacosStore;
        if (store == null) {
            return R.fail(500, "Nacos 存储不可用");
        }
        store.writeAll(content, INFRA_DATA_ID);
        log.warn("[基础设施配置] 已整份发布到 Nacos {}（{} 行），各服务重启或 Nacos 刷新时生效", INFRA_DATA_ID,
                content.split("\n").length);
        return R.ok(Map.of("saved", true, "dataId", INFRA_DATA_ID));
    }

    private static boolean isSensitive(String key) {
        String k = key.toLowerCase();
        return k.contains("password") || k.contains("secret") || k.contains("token");
    }
}
