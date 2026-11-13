package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.RiskEventEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风控事件（tb_gw_risk_event，v0.2 第 13 项）。
 *
 * <p>写入侧带「同类事件短窗口去重」（countRecent），避免每分钟巡检刷出重复事件；
 * 处置通过 {@link #handle} 原地更新，形成处置留痕。
 */
@Mapper
public interface RiskEventMapper {

    @Insert("""
            INSERT INTO tb_gw_risk_event
                (subject_type, subject_id, subject_name, risk_type, risk_level, detail,
                 status, create_time)
            VALUES
                (#{subjectType}, #{subjectId}, #{subjectName}, #{riskType}, #{riskLevel}, #{detail},
                 #{status}, #{createTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RiskEventEntity event);

    /** 同类事件在窗口内是否已存在（去重用） */
    @Select("""
            SELECT COUNT(*) FROM tb_gw_risk_event
            WHERE subject_type = #{subjectType} AND subject_id = #{subjectId}
              AND risk_type = #{riskType} AND create_time >= #{since}
            """)
    long countRecent(@Param("subjectType") String subjectType,
                     @Param("subjectId") String subjectId,
                     @Param("riskType") String riskType,
                     @Param("since") LocalDateTime since);

    @Select("SELECT * FROM tb_gw_risk_event WHERE id = #{id}")
    RiskEventEntity selectById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT * FROM tb_gw_risk_event
            <where>
              <if test="subjectType != null and subjectType != ''">AND subject_type = #{subjectType}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
              <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
              <if test="keyword != null and keyword != ''">
                AND (subject_name LIKE CONCAT('%', #{keyword}, '%')
                     OR subject_id LIKE CONCAT('%', #{keyword}, '%')
                     OR risk_type LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </where>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<RiskEventEntity> selectPage(@Param("subjectType") String subjectType,
                                     @Param("status") String status,
                                     @Param("riskLevel") String riskLevel,
                                     @Param("keyword") String keyword,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM tb_gw_risk_event
            <where>
              <if test="subjectType != null and subjectType != ''">AND subject_type = #{subjectType}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
              <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
              <if test="keyword != null and keyword != ''">
                AND (subject_name LIKE CONCAT('%', #{keyword}, '%')
                     OR subject_id LIKE CONCAT('%', #{keyword}, '%')
                     OR risk_type LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long countPage(@Param("subjectType") String subjectType,
                   @Param("status") String status,
                   @Param("riskLevel") String riskLevel,
                   @Param("keyword") String keyword);

    @Update("""
            UPDATE tb_gw_risk_event
            SET status = #{status}, action = #{action}, handler = #{handler},
                handle_remark = #{remark}, handle_time = #{handleTime}
            WHERE id = #{id}
            """)
    int handle(@Param("id") Long id,
               @Param("status") String status,
               @Param("action") String action,
               @Param("handler") String handler,
               @Param("remark") String remark,
               @Param("handleTime") LocalDateTime handleTime);

    @Select("SELECT COUNT(*) FROM tb_gw_risk_event WHERE create_time >= #{since}")
    long countSince(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM tb_gw_risk_event WHERE status = 'OPEN'")
    long countOpen();

    @Select("SELECT COUNT(*) FROM tb_gw_risk_event WHERE risk_level = 'HIGH' AND create_time >= #{since}")
    long countHighSince(@Param("since") LocalDateTime since);

    /** 近 N 天按类型聚合（概览用） */
    @Select("""
            SELECT risk_type AS riskType, COUNT(*) AS cnt
            FROM tb_gw_risk_event
            WHERE create_time >= #{since}
            GROUP BY risk_type
            ORDER BY cnt DESC
            """)
    List<java.util.Map<String, Object>> countByType(@Param("since") LocalDateTime since);
}
