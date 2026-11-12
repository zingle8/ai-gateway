package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.ApiKeyEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 数据访问
 */
@Mapper
public interface ApiKeyMapper {

    @Select("SELECT * FROM tb_gw_api_key ORDER BY id DESC")
    List<ApiKeyEntity> selectAll();

    @Select("SELECT * FROM tb_gw_api_key WHERE key_id = #{keyId}")
    ApiKeyEntity selectByKeyId(@Param("keyId") String keyId);

    @Select("SELECT * FROM tb_gw_api_key WHERE id = #{id}")
    ApiKeyEntity selectById(@Param("id") Long id);

    @Insert("INSERT INTO tb_gw_api_key (key_id, secret_hash, name, owner_user_id, status, expire_at, "
            + "allowed_ips, allowed_services, allowed_platforms, rate_limit_per_minute) "
            + "VALUES (#{keyId}, #{secretHash}, #{name}, #{ownerUserId}, #{status}, #{expireAt}, "
            + "#{allowedIps}, #{allowedServices}, #{allowedPlatforms}, #{rateLimitPerMinute})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApiKeyEntity key);

    /** 调用方约束：IP / 服务 / 平台白名单 + 每分钟限流（不含密钥，可随时调整） */
    @Update("UPDATE tb_gw_api_key SET allowed_ips = #{allowedIps}, allowed_services = #{allowedServices}, "
            + "allowed_platforms = #{allowedPlatforms}, rate_limit_per_minute = #{rateLimitPerMinute} "
            + "WHERE id = #{id}")
    int updateScope(@Param("id") Long id,
                    @Param("allowedIps") String allowedIps,
                    @Param("allowedServices") String allowedServices,
                    @Param("allowedPlatforms") String allowedPlatforms,
                    @Param("rateLimitPerMinute") Integer rateLimitPerMinute);

    @Update("UPDATE tb_gw_api_key SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 轮换：只换密钥，keyId 不变 */
    @Update("UPDATE tb_gw_api_key SET secret_hash = #{secretHash} WHERE id = #{id}")
    int updateSecret(@Param("id") Long id, @Param("secretHash") String secretHash);

    /** 续期：只改到期时间，密钥与 keyId 不变 */
    @Update("UPDATE tb_gw_api_key SET expire_at = #{expireAt} WHERE id = #{id}")
    int updateExpireAt(@Param("id") Long id, @Param("expireAt") LocalDateTime expireAt);

    @Delete("DELETE FROM tb_gw_api_key WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE tb_gw_api_key SET last_used_at = #{now}, last_used_ip = #{ip} WHERE key_id = #{keyId}")
    int touchLastUsed(@Param("keyId") String keyId, @Param("now") LocalDateTime now, @Param("ip") String ip);
}
