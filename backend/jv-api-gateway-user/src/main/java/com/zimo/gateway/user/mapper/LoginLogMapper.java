package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.LoginLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录历史（tb_login_log，v0.2 第 13 项）。
 *
 * <p>登录成功 / 失败各落一条；查询侧按账号、成败、时间窗过滤分页，
 * 「陌生 IP」判定复用同一张表（该账号历史上是否成功登录过该 IP）。
 */
@Mapper
public interface LoginLogMapper {

    @Insert("""
            INSERT INTO tb_login_log
                (username, user_id, ip, user_agent, app_id, success, fail_reason,
                 fail_count, risk_type, risk_level, risk_reason, login_time)
            VALUES
                (#{username}, #{userId}, #{ip}, #{userAgent}, #{appId}, #{success}, #{failReason},
                 #{failCount}, #{riskType}, #{riskLevel}, #{riskReason}, #{loginTime})
            """)
    int insert(LoginLogEntity log);

    /** 该账号历史上是否成功登录过该 IP（用于陌生 IP 识别） */
    @Select("""
            SELECT COUNT(*) FROM tb_login_log
            WHERE username = #{username} AND success = 1 AND ip = #{ip}
            """)
    long countSuccessByIp(@Param("username") String username, @Param("ip") String ip);

    /** 该账号历史成功登录次数（首次登录不报陌生 IP） */
    @Select("SELECT COUNT(*) FROM tb_login_log WHERE username = #{username} AND success = 1")
    long countSuccess(@Param("username") String username);

    /** 窗口内该账号的失败次数 */
    @Select("""
            SELECT COUNT(*) FROM tb_login_log
            WHERE username = #{username} AND success = 0 AND login_time >= #{since}
            """)
    long countFailSince(@Param("username") String username, @Param("since") LocalDateTime since);

    @Select("""
            <script>
            SELECT * FROM tb_login_log
            <where>
              <if test="username != null and username != ''">AND username = #{username}</if>
              <if test="success != null">AND success = #{success}</if>
              <if test="riskOnly != null and riskOnly">AND risk_level IS NOT NULL</if>
            </where>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<LoginLogEntity> selectPage(@Param("username") String username,
                                    @Param("success") Integer success,
                                    @Param("riskOnly") Boolean riskOnly,
                                    @Param("offset") long offset,
                                    @Param("size") long size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM tb_login_log
            <where>
              <if test="username != null and username != ''">AND username = #{username}</if>
              <if test="success != null">AND success = #{success}</if>
              <if test="riskOnly != null and riskOnly">AND risk_level IS NOT NULL</if>
            </where>
            </script>
            """)
    long countPage(@Param("username") String username,
                   @Param("success") Integer success,
                   @Param("riskOnly") Boolean riskOnly);

    /** 今日失败登录次数（风控概览） */
    @Select("SELECT COUNT(*) FROM tb_login_log WHERE success = 0 AND login_time >= #{since}")
    long countFailSinceAll(@Param("since") LocalDateTime since);
}
