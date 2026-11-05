package com.zimo.gateway.proxy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 鉴权所需的最小数据查询（共享库 tb_gw_api_key / tb_sys_user）。
 * 每次校验先走 Caffeine 缓存，这里只承接缓存未命中的回源。
 */
@Mapper
public interface AuthQueryMapper {

    @Select("SELECT key_id, secret_hash, status, expire_at FROM tb_gw_api_key WHERE key_id = #{keyId}")
    Map<String, Object> selectApiKey(@Param("keyId") String keyId);

    @Select("SELECT token_version, status FROM tb_sys_user WHERE id = #{userId} AND deleted = 0")
    Map<String, Object> selectUserState(@Param("userId") Long userId);

    @Update("UPDATE tb_gw_api_key SET last_used_at = #{now} WHERE key_id = #{keyId}")
    int touchLastUsed(@Param("keyId") String keyId, @Param("now") LocalDateTime now);

    /** 平台配置（tb_sys_config，由 jv-api-gateway-config 维护）：缓存模式实时读取 */
    @Select("SELECT config_value FROM tb_sys_config WHERE config_key = #{key}")
    String selectConfigValue(@Param("key") String key);
}
