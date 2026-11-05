package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.ApiKeyLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * API Key 操作审计与统计
 */
@Mapper
public interface ApiKeyLogMapper {

    @Insert("INSERT INTO tb_gw_api_key_log (key_id, key_name, action, operator_user_id, operator_name, remark) "
            + "VALUES (#{keyId}, #{keyName}, #{action}, #{operatorUserId}, #{operatorName}, #{remark})")
    int insert(ApiKeyLogEntity log);

    /**
     * 看板统计：一次查询取全部计数。
     * 「今日」按数据库当前日期（CURDATE）划分。
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM tb_gw_api_key)                                               AS total,
              (SELECT COUNT(*) FROM tb_gw_api_key WHERE status = 1)                              AS enabled,
              (SELECT COUNT(*) FROM tb_gw_api_key WHERE status = 0)                              AS disabled,
              (SELECT COUNT(*) FROM tb_gw_api_key_log WHERE action = 'CREATE' AND create_time >= CURDATE())  AS todayCreated,
              (SELECT COUNT(*) FROM tb_gw_api_key_log WHERE action = 'ROTATE' AND create_time >= CURDATE())  AS todayRotated,
              (SELECT COUNT(*) FROM tb_gw_api_key_log WHERE action = 'DISABLE' AND create_time >= CURDATE()) AS todayRevoked,
              (SELECT COUNT(*) FROM tb_gw_api_key_log WHERE action = 'DELETE' AND create_time >= CURDATE())  AS todayDeleted,
              (SELECT COUNT(*) FROM tb_gw_api_key_log WHERE action = 'ENABLE' AND create_time >= CURDATE())  AS todayEnabled,
              (SELECT COUNT(*) FROM tb_gw_api_key WHERE status = 1 AND expire_at IS NOT NULL
                 AND expire_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY))                 AS expiringIn7Days
            """)
    Map<String, Object> selectStats();

    /** 最近操作记录（看板明细） */
    @Select("SELECT id, key_id, key_name, action, operator_user_id, operator_name, remark, create_time "
            + "FROM tb_gw_api_key_log ORDER BY id DESC LIMIT #{limit}")
    List<ApiKeyLogEntity> selectRecent(@Param("limit") int limit);
}
