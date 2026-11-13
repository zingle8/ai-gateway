package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.SsoLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * SSO 登录审计（tb_gw_sso_log，v0.2 第 15 项）。
 */
@Mapper
public interface SsoLogMapper {

    @Insert("""
            INSERT INTO tb_gw_sso_log
                (user_id, username, client_id, app_id, action, result, ip, detail, create_time)
            VALUES
                (#{userId}, #{username}, #{clientId}, #{appId}, #{action}, #{result}, #{ip}, #{detail}, #{createTime})
            """)
    int insert(SsoLogEntity log);

    @Select("""
            <script>
            SELECT * FROM tb_gw_sso_log
            <where>
              <if test="clientId != null and clientId != ''">AND client_id = #{clientId}</if>
              <if test="action != null and action != ''">AND action = #{action}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<SsoLogEntity> selectPage(@Param("clientId") String clientId,
                                  @Param("action") String action,
                                  @Param("offset") long offset,
                                  @Param("size") long size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM tb_gw_sso_log
            <where>
              <if test="clientId != null and clientId != ''">AND client_id = #{clientId}</if>
              <if test="action != null and action != ''">AND action = #{action}</if>
            </where>
            </script>
            """)
    long countPage(@Param("clientId") String clientId,
                   @Param("action") String action);
}
