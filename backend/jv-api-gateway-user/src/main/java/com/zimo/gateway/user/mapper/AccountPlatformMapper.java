package com.zimo.gateway.user.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号-开放平台授权数据访问
 */
@Mapper
public interface AccountPlatformMapper {

    @Select("SELECT app_id FROM tb_gw_account_platform WHERE user_id = #{userId} ORDER BY app_id ASC")
    List<String> selectAppIdsByUser(@Param("userId") Long userId);

    /** 是否存在授权（开放认证换 token 校验用） */
    @Select("SELECT COUNT(1) FROM tb_gw_account_platform WHERE user_id = #{userId} AND app_id = #{appId}")
    int countByUserAndApp(@Param("userId") Long userId, @Param("appId") String appId);

    @Delete("DELETE FROM tb_gw_account_platform WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO tb_gw_account_platform (user_id, app_id) VALUES (#{userId}, #{appId})")
    int insert(@Param("userId") Long userId, @Param("appId") String appId);
}
