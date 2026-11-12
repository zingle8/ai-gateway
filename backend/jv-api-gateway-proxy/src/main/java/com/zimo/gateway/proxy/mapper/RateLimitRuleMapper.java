package com.zimo.gateway.proxy.mapper;

import com.zimo.gateway.proxy.ratelimit.RateLimitRule;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 限流规则只读查询（规则由控制台经 config 服务维护）
 */
@Mapper
public interface RateLimitRuleMapper {

    @Select("SELECT id, name, dimension, target, replenish_rate, burst_capacity, requested_tokens, status "
            + "FROM tb_gw_rate_limit WHERE status = 1 ORDER BY id ASC")
    List<RateLimitRule> listEnabled();
}
