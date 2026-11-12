package com.zimo.gateway.discovery.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 变更审计写入（v0.2 第 8 项）：路由变更与 config 服务共用 tb_gw_change_log 表，
 * 查询接口在 config 服务（GET /change-logs），本模块只负责写。
 */
@Mapper
public interface ChangeLogMapper {

    @Insert("INSERT INTO tb_gw_change_log (biz_type, biz_key, action, before_value, after_value, "
            + "operator, operator_id, request_ip) VALUES (#{bizType}, #{bizKey}, #{action}, "
            + "#{beforeValue}, #{afterValue}, #{operator}, #{operatorId}, #{requestIp})")
    int insert(@Param("bizType") String bizType,
               @Param("bizKey") String bizKey,
               @Param("action") String action,
               @Param("beforeValue") String beforeValue,
               @Param("afterValue") String afterValue,
               @Param("operator") String operator,
               @Param("operatorId") Long operatorId,
               @Param("requestIp") String requestIp);
}
