package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.PermItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 权限点数据访问
 */
@Mapper
public interface PermItemMapper {

    @Select("SELECT * FROM tb_perm_item ORDER BY menu_id, func_name, id")
    List<PermItemEntity> selectAll();

    @Select("SELECT COUNT(*) FROM tb_perm_item WHERE code = #{code} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    long countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    @Insert("INSERT INTO tb_perm_item (menu_id, func_name, code, name, type, api, enabled) "
            + "VALUES (#{menuId}, #{funcName}, #{code}, #{name}, #{type}, #{api}, #{enabled})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PermItemEntity item);

    @Update("UPDATE tb_perm_item SET menu_id = #{menuId}, func_name = #{funcName}, name = #{name}, "
            + "type = #{type}, api = #{api}, enabled = #{enabled} WHERE id = #{id}")
    int update(PermItemEntity item);

    @Delete("DELETE FROM tb_perm_item WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
