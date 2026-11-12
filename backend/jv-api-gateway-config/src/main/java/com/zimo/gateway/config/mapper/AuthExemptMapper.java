package com.zimo.gateway.config.mapper;

import com.zimo.gateway.config.entity.AuthExemptEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 鉴权豁免规则数据访问
 */
@Mapper
public interface AuthExemptMapper {

    @Select("SELECT * FROM tb_gw_auth_exempt ORDER BY id ASC")
    List<AuthExemptEntity> selectAll();

    @Select("SELECT * FROM tb_gw_auth_exempt WHERE id = #{id}")
    AuthExemptEntity selectById(@Param("id") Long id);

    @Insert("INSERT INTO tb_gw_auth_exempt (name, path_pattern, methods, exempt_type, status, expire_at, remark) "
            + "VALUES (#{name}, #{pathPattern}, #{methods}, #{exemptType}, #{status}, #{expireAt}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuthExemptEntity entity);

    @Update("UPDATE tb_gw_auth_exempt SET name = #{name}, path_pattern = #{pathPattern}, methods = #{methods}, "
            + "exempt_type = #{exemptType}, status = #{status}, expire_at = #{expireAt}, remark = #{remark} "
            + "WHERE id = #{id}")
    int update(AuthExemptEntity entity);

    @Update("UPDATE tb_gw_auth_exempt SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Delete("DELETE FROM tb_gw_auth_exempt WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
