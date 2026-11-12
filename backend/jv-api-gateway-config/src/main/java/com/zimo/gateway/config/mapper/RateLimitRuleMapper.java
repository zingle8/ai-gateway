package com.zimo.gateway.config.mapper;

import com.zimo.gateway.config.entity.RateLimitRuleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 限流规则数据访问
 */
@Mapper
public interface RateLimitRuleMapper {

    @Select("SELECT * FROM tb_gw_rate_limit ORDER BY id ASC")
    List<RateLimitRuleEntity> selectAll();

    @Select("SELECT * FROM tb_gw_rate_limit WHERE id = #{id}")
    RateLimitRuleEntity selectById(@Param("id") Long id);

    @Insert("INSERT INTO tb_gw_rate_limit (name, dimension, target, replenish_rate, burst_capacity, "
            + "requested_tokens, status, remark) VALUES (#{name}, #{dimension}, #{target}, #{replenishRate}, "
            + "#{burstCapacity}, #{requestedTokens}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RateLimitRuleEntity entity);

    @Update("UPDATE tb_gw_rate_limit SET name = #{name}, dimension = #{dimension}, target = #{target}, "
            + "replenish_rate = #{replenishRate}, burst_capacity = #{burstCapacity}, "
            + "requested_tokens = #{requestedTokens}, status = #{status}, remark = #{remark} WHERE id = #{id}")
    int update(RateLimitRuleEntity entity);

    @Update("UPDATE tb_gw_rate_limit SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Delete("DELETE FROM tb_gw_rate_limit WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
