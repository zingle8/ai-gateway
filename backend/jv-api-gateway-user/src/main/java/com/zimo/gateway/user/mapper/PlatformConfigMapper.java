package com.zimo.gateway.user.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 读取平台级配置（tb_sys_config，由 jv-api-gateway-config 维护）
 */
public interface PlatformConfigMapper {

    @Select("SELECT config_value FROM tb_sys_config WHERE config_key = #{key}")
    String selectValue(@Param("key") String key);
}
