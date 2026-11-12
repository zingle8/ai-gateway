package com.zimo.gateway.config.mapper;

import com.zimo.gateway.config.entity.ChangeLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChangeLogMapper {

    @Insert("INSERT INTO tb_gw_change_log (biz_type, biz_key, action, before_value, after_value, "
            + "operator, operator_id, request_ip) VALUES (#{bizType}, #{bizKey}, #{action}, "
            + "#{beforeValue}, #{afterValue}, #{operator}, #{operatorId}, #{requestIp})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChangeLogEntity log);

    /** 最近变更：bizType / bizKey 均可选（为空表示不限） */
    @Select("""
            <script>
            SELECT * FROM tb_gw_change_log
            <where>
              <if test="bizType != null and bizType != ''">AND biz_type = #{bizType}</if>
              <if test="bizKey != null and bizKey != ''">AND biz_key = #{bizKey}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<ChangeLogEntity> selectPage(@Param("bizType") String bizType, @Param("bizKey") String bizKey,
                                     @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM tb_gw_change_log
            <where>
              <if test="bizType != null and bizType != ''">AND biz_type = #{bizType}</if>
              <if test="bizKey != null and bizKey != ''">AND biz_key = #{bizKey}</if>
            </where>
            </script>
            """)
    long count(@Param("bizType") String bizType, @Param("bizKey") String bizKey);
}
