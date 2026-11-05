package com.zimo.gateway.config.service;

import com.zimo.gateway.config.model.ConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 平台配置读写
 */
public interface ConfigService {

    /** 全量配置（系统设置页） */
    List<ConfigVO> listAll();

    /** 对外公开的开关集合（免鉴权，供前端菜单健康标识等轮询） */
    Map<String, String> publicFlags();

    /** 修改单个配置值 */
    void update(String key, String value);
}
