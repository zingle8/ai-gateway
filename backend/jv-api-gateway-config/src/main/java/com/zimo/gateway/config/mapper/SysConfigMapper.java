package com.zimo.gateway.config.mapper;

import com.zimo.gateway.config.entity.SysConfigEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统配置读写（注解式，与平台既有 MyBatis 风格一致）
 */
@Mapper
public interface SysConfigMapper {

    String COLUMNS = "id, config_key, config_value, value_type, config_source, source_ref, "
            + "platform_name, app_name, service_name, remark, update_time";

    @Select("SELECT " + COLUMNS + " FROM tb_sys_config ORDER BY config_source, config_key")
    List<SysConfigEntity> selectAll();

    @Select("SELECT " + COLUMNS + " FROM tb_sys_config WHERE config_key = #{key}")
    SysConfigEntity selectByKey(@Param("key") String key);

    @Insert("INSERT INTO tb_sys_config (config_key, config_value, value_type, config_source, source_ref, "
            + "platform_name, app_name, service_name, remark) "
            + "VALUES (#{configKey}, #{configValue}, #{valueType}, #{configSource}, #{sourceRef}, "
            + "#{platformName}, #{appName}, #{serviceName}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysConfigEntity config);

    /** 更新值（同时刷新快照） */
    @Update("UPDATE tb_sys_config SET config_value = #{value}, update_time = NOW() WHERE config_key = #{key}")
    int updateValue(@Param("key") String key, @Param("value") String value);

    @Delete("DELETE FROM tb_sys_config WHERE config_key = #{key}")
    int deleteByKey(@Param("key") String key);
}
