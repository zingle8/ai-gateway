package com.zimo.gateway.config.service.impl;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.config.mapper.SysConfigMapper;
import com.zimo.gateway.config.model.ConfigVO;
import com.zimo.gateway.config.service.ConfigService;
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
            "platform.open.auth.enabled"
    );

    private final SysConfigMapper mapper;

    @Override
    public List<ConfigVO> listAll() {
        return mapper.selectAll().stream().map(ConfigVO::of).toList();
    }

    @Override
    public Map<String, String> publicFlags() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : PUBLIC_KEYS) {
            var config = mapper.selectByKey(key);
            if (config != null) {
                out.put(key, config.getConfigValue());
            }
        }
        return out;
    }

    @Override
    public void update(String key, String value) {
        var config = mapper.selectByKey(key);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "配置项不存在：" + key);
        }
        if (mapper.updateValue(key, value) == 0) {
            throw new BizException(ResultCode.ERROR.getCode(), "配置更新失败：" + key);
        }
        log.info("配置已更新：{} = {}", key, value);
    }
}
