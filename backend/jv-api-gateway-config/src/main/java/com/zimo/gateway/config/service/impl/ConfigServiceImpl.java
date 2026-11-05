package com.zimo.gateway.config.service.impl;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.config.entity.SysConfigEntity;
import com.zimo.gateway.config.mapper.SysConfigMapper;
import com.zimo.gateway.config.model.ConfigCreateRequest;
import com.zimo.gateway.config.model.ConfigVO;
import com.zimo.gateway.config.service.ConfigService;
import com.zimo.gateway.config.store.ConfigStore;
import com.zimo.gateway.config.store.ConfigStoreException;
import com.zimo.gateway.config.store.ConfigStoreRegistry;
import com.zimo.gateway.config.store.DbConfigStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    /** 对前端公开、无需登录即可读取的开关 */
    private static final List<String> PUBLIC_KEYS = List.of(
            "platform.menu.health.enabled",
            "platform.auth.cache.mode",
            "platform.open.auth.enabled",
            "platform.ui.refresh.interval"
    );

    private static final String DEFAULT_PLATFORM = "API网关平台";
    private static final String DEFAULT_APP = "jv-api-gateway";
    private static final String DEFAULT_SERVICE = "*";

    private final SysConfigMapper mapper;
    private final ConfigStoreRegistry stores;

    @Override
    public List<ConfigVO> listAll() {
        return mapper.selectAll().stream().map(this::toVO).toList();
    }

    @Override
    public Map<String, String> publicFlags() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : PUBLIC_KEYS) {
            SysConfigEntity config = mapper.selectByKey(key);
            if (config != null) {
                out.put(key, resolve(config).value());
            }
        }
        return out;
    }

    @Override
    public ConfigVO create(ConfigCreateRequest request) {
        String key = request.key().trim();
        if (mapper.selectByKey(key) != null) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "配置键已存在：" + key);
        }
        String source = ConfigStoreRegistry.normalize(request.source());
        String valueType = valueType(request.valueType());

        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(request.value());
        entity.setValueType(valueType);
        entity.setConfigSource(source);
        entity.setSourceRef(blankToNull(request.sourceRef()));
        entity.setPlatformName(defaultIfBlank(request.platformName(), DEFAULT_PLATFORM));
        entity.setAppName(defaultIfBlank(request.appName(), DEFAULT_APP));
        entity.setServiceName(defaultIfBlank(request.serviceName(), DEFAULT_SERVICE));
        entity.setRemark(request.remark());

        ConfigStore store = stores.of(source);
        if (!DbConfigStore.SOURCE.equals(source)) {
            try {
                store.write(key, request.value(), entity.getSourceRef());
            } catch (ConfigStoreException e) {
                throw new BizException(ResultCode.ERROR.getCode(), "写入 " + source + " 失败：" + e.getMessage());
            }
        }
        try {
            mapper.insert(entity);
        } catch (Exception e) {
            // 元数据落库失败时尽量回滚远端，避免"孤儿配置"
            if (!DbConfigStore.SOURCE.equals(source)) {
                try {
                    store.delete(key, entity.getSourceRef());
                } catch (Exception ignore) {
                    log.warn("回滚 {} 配置失败：{}", source, key);
                }
            }
            throw new BizException(ResultCode.ERROR.getCode(), "配置保存失败：" + e.getMessage());
        }
        log.info("配置已创建：{} @ {}（{}）", key, source, store.label(entity.getSourceRef()));
        return toVO(mapper.selectByKey(key));
    }

    @Override
    public void update(String key, String value) {
        SysConfigEntity config = requireConfig(key);
        String source = ConfigStoreRegistry.normalize(config.getConfigSource());
        if (!DbConfigStore.SOURCE.equals(source)) {
            try {
                stores.of(source).write(key, value, config.getSourceRef());
            } catch (ConfigStoreException e) {
                throw new BizException(ResultCode.ERROR.getCode(), "写入 " + source + " 失败：" + e.getMessage());
            }
        }
        if (mapper.updateValue(key, value) == 0) {
            throw new BizException(ResultCode.ERROR.getCode(), "配置更新失败：" + key);
        }
        log.info("配置已更新：{} = {} @ {}", key, value, source);
    }

    @Override
    public void delete(String key) {
        SysConfigEntity config = requireConfig(key);
        String source = ConfigStoreRegistry.normalize(config.getConfigSource());
        if (!DbConfigStore.SOURCE.equals(source)) {
            try {
                stores.of(source).delete(key, config.getSourceRef());
            } catch (ConfigStoreException e) {
                throw new BizException(ResultCode.ERROR.getCode(), "清理 " + source + " 失败：" + e.getMessage());
            }
        }
        mapper.deleteByKey(key);
        log.info("配置已删除：{} @ {}", key, source);
    }

    // ------------------------------------------------------------------ 内部

    private ConfigVO toVO(SysConfigEntity config) {
        Resolved resolved = resolve(config);
        ConfigStore store = stores.of(config.getConfigSource());
        return ConfigVO.of(config, resolved.value(), store.label(config.getSourceRef()), resolved.state());
    }

    /** 按来源取值；远端不可达 / 不存在时回落 MySQL 快照并标记 STALE */
    private Resolved resolve(SysConfigEntity config) {
        String source = ConfigStoreRegistry.normalize(config.getConfigSource());
        String snapshot = config.getConfigValue() == null ? "" : config.getConfigValue();
        if (DbConfigStore.SOURCE.equals(source)) {
            return new Resolved(snapshot, "OK");
        }
        try {
            String remote = stores.of(source).read(config.getConfigKey(), config.getSourceRef());
            if (remote == null) {
                return new Resolved(snapshot, "STALE");
            }
            if (!remote.equals(snapshot)) {
                // 远端被外部改过：回写快照，保持"快照=最后已知值"
                mapper.updateValue(config.getConfigKey(), remote);
            }
            return new Resolved(remote, "OK");
        } catch (ConfigStoreException e) {
            log.warn("配置来源不可达 key={} source={}：{}", config.getConfigKey(), source, e.getMessage());
            return new Resolved(snapshot, "STALE");
        }
    }

    private SysConfigEntity requireConfig(String key) {
        SysConfigEntity config = mapper.selectByKey(key);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "配置项不存在：" + key);
        }
        return config;
    }

    private String valueType(String raw) {
        String t = raw == null ? "STRING" : raw.trim().toUpperCase();
        return switch (t) {
            case "BOOLEAN", "NUMBER", "STRING" -> t;
            default -> "STRING";
        };
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Resolved(String value, String state) {
    }
}
