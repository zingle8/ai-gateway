package com.zimo.gateway.proxy.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 上报通道 ACL 配置读取（tb_sys_config）。
 * proxy 只配了 MyBatis（无 JdbcTemplate），故用 Mapper 读取。
 */
@Mapper
public interface ReportAclMapper {

    @Select("SELECT config_value FROM tb_sys_config WHERE config_key = #{key} LIMIT 1")
    String selectValue(@Param("key") String key);
}
