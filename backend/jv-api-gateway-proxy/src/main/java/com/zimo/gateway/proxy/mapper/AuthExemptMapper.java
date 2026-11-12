package com.zimo.gateway.proxy.mapper;

import com.zimo.gateway.proxy.ratelimit.AuthExemptRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 鉴权豁免规则只读查询（规则由控制台经 config 服务维护）
 */
@Mapper
public interface AuthExemptMapper {

    @Select("SELECT id, name, path_pattern, methods, exempt_type, expire_at FROM tb_gw_auth_exempt "
            + "WHERE status = 1 AND (expire_at IS NULL OR expire_at > NOW()) ORDER BY id ASC")
    List<AuthExemptRule> listEffective();
}
