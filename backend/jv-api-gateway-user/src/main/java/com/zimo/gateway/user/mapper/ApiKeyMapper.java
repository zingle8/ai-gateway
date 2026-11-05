package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.ApiKey;
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
    List<ApiKey> selectAll();

    @Select("SELECT * FROM tb_gw_api_key WHERE key_id = #{keyId}")
    ApiKey selectByKeyId(@Param("keyId") String keyId);

    @Select("SELECT * FROM tb_gw_api_key WHERE id = #{id}")
    ApiKey selectById(@Param("id") Long id);

    @Insert("INSERT INTO tb_gw_api_key (key_id, secret_hash, name, owner_user_id, status, expire_at) "
            + "VALUES (#{keyId}, #{secretHash}, #{name}, #{ownerUserId}, #{status}, #{expireAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApiKey key);

    @Update("UPDATE tb_gw_api_key SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 轮换：只换密钥，keyId 不变 */
    @Update("UPDATE tb_gw_api_key SET secret_hash = #{secretHash} WHERE id = #{id}")
    int updateSecret(@Param("id") Long id, @Param("secretHash") String secretHash);

    @Delete("DELETE FROM tb_gw_api_key WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE tb_gw_api_key SET last_used_at = #{now}, last_used_ip = #{ip} WHERE key_id = #{keyId}")
    int touchLastUsed(@Param("keyId") String keyId, @Param("now") LocalDateTime now, @Param("ip") String ip);
}
