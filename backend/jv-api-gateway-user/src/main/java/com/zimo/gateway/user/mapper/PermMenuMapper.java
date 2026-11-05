package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.PermMenuEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 权限字典的平台 / 菜单骨架
 */
@Mapper
public interface PermMenuMapper {

    @Select("SELECT * FROM tb_perm_menu ORDER BY sort, id")
    List<PermMenuEntity> selectAll();

    @Select("SELECT COUNT(*) FROM tb_perm_menu WHERE id = #{id}")
    long countById(@Param("id") String id);

    @Insert("INSERT IGNORE INTO tb_perm_menu (id, name, parent_id, sort) VALUES (#{id}, #{name}, #{parentId}, #{sort})")
    int insert(PermMenuEntity menu);

    @Update("UPDATE tb_perm_menu SET name = #{name}, sort = #{sort} WHERE id = #{id}")
    int update(PermMenuEntity menu);

    @Delete("DELETE FROM tb_perm_menu WHERE id = #{id}")
    int deleteById(@Param("id") String id);
}
