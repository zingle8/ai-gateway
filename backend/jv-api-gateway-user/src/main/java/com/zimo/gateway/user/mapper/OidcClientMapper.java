package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.OidcClientEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * SSO 接入方登记（tb_gw_oidc_client，v0.2 第 15 项）。
 */
@Mapper
public interface OidcClientMapper {

    @Select("SELECT * FROM tb_gw_oidc_client ORDER BY id DESC")
    List<OidcClientEntity> selectAll();

    @Select("SELECT * FROM tb_gw_oidc_client WHERE client_id = #{clientId}")
    OidcClientEntity selectByClientId(@Param("clientId") String clientId);

    @Select("SELECT * FROM tb_gw_oidc_client WHERE id = #{id}")
    OidcClientEntity selectById(@Param("id") Long id);

    @Insert("""
            INSERT INTO tb_gw_oidc_client
                (client_id, client_secret, client_name, app_id, redirect_uri, scopes, status, create_time)
            VALUES
                (#{clientId}, #{clientSecret}, #{clientName}, #{appId}, #{redirectUri}, #{scopes}, #{status}, #{createTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OidcClientEntity client);

    @Update("""
            UPDATE tb_gw_oidc_client
            SET client_name = #{clientName}, app_id = #{appId},
                redirect_uri = #{redirectUri}, status = #{status}
            WHERE id = #{id}
            """)
    int update(OidcClientEntity client);

    @Update("UPDATE tb_gw_oidc_client SET client_secret = #{secretHash} WHERE id = #{id}")
    int updateSecret(@Param("id") Long id, @Param("secretHash") String secretHash);

    @Update("UPDATE tb_gw_oidc_client SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Delete("DELETE FROM tb_gw_oidc_client WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
