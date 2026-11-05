package com.zimo.gateway.config.service;

import com.zimo.gateway.config.model.ConfigCreateRequest;
import com.zimo.gateway.config.model.ConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 平台配置读写。
 *
 * <p>元数据（key / 来源 / 归属 / 类型）在 MySQL；值按来源落在 DB / Redis / Nacos。
 */
public interface ConfigService {

    /** 全量配置（系统设置页），值实时取自对应来源 */
    List<ConfigVO> listAll();

    /** 对外公开的开关集合（免鉴权，供前端菜单健康标识等轮询） */
    Map<String, String> publicFlags();

    /** 新增配置：写目标存储 + 落元数据 */
    ConfigVO create(ConfigCreateRequest request);

    /** 修改单个配置值：写目标存储 + 刷新快照 */
    void update(String key, String value);

    /** 删除配置：清理目标存储 + 删元数据 */
    void delete(String key);
}
