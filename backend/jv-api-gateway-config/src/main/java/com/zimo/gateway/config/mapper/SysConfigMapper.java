package com.zimo.gateway.config.mapper;

import com.zimo.gateway.config.entity.SysConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统配置读写（注解式，与平台既有 MyBatis 风格一致）
 */
public interface SysConfigMapper {

    @Select("SELECT id, config_key, config_value, value_type, remark, update_time "
            + "FROM tb_sys_config ORDER BY config_key")
    List<SysConfig> selectAll();

    @Select("SELECT id, config_key, config_value, value_type, remark, update_time "
            + "FROM tb_sys_config WHERE config_key = #{key}")
    SysConfig selectByKey(@Param("key") String key);

    @Update("UPDATE tb_sys_config SET config_value = #{value} WHERE config_key = #{key}")
    int updateValue(@Param("key") String key, @Param("value") String value);
}
