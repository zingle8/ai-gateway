package com.zimo.gateway.config.store;

import org.springframework.stereotype.Component;

/**
 * DB 来源：值就在 tb_sys_config.config_value，读写由 Service 直接落表。
 */
@Component
public class DbConfigStore implements ConfigStore {

    public static final String SOURCE = "DB";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String label(String sourceRef) {
        return "MySQL · api_gateway.tb_sys_config";
    }

    @Override
    public String read(String key, String sourceRef) {
        // 值由 Service 从元数据行直接取，这里不重复查库
        return null;
    }

    @Override
    public void write(String key, String value, String sourceRef) {
        // 由 Service 的 mapper.updateValue 完成
    }

    @Override
    public void delete(String key, String sourceRef) {
        // 由 Service 的 mapper.deleteByKey 完成
    }
}
