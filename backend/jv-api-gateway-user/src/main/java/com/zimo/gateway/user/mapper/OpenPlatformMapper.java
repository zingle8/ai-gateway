package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.OpenPlatformEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 开放平台登记数据访问
 */
@Mapper
public interface OpenPlatformMapper {

    @Select("SELECT * FROM tb_gw_open_platform ORDER BY id ASC")
    List<OpenPlatformEntity> selectAll();

    @Select("SELECT * FROM tb_gw_open_platform WHERE app_id = #{appId}")
    OpenPlatformEntity selectByAppId(@Param("appId") String appId);
}
