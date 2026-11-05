package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.PermGroupEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 分组数据访问（分组 + 成员 + 权限点关联，原生 MyBatis 注解 SQL）
 */
@Mapper
public interface PermGroupMapper {

    @Select("SELECT * FROM tb_perm_group WHERE deleted = 0 ORDER BY type, parent_id, id")
    List<PermGroupEntity> selectAll();

    @Select("SELECT * FROM tb_perm_group WHERE id = #{id} AND deleted = 0")
    PermGroupEntity selectById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM tb_perm_group WHERE code = #{code} AND deleted = 0")
    long countByCode(@Param("code") String code);

    @Insert("INSERT INTO tb_perm_group (name, code, type, parent_id, data_scope, leader, remark, builtin, status) "
            + "VALUES (#{name}, #{code}, #{type}, #{parentId}, #{dataScope}, #{leader}, #{remark}, #{builtin}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PermGroupEntity group);

    @Update("UPDATE tb_perm_group SET name = #{name}, data_scope = #{dataScope}, leader = #{leader}, "
            + "remark = #{remark}, status = #{status} WHERE id = #{id} AND deleted = 0")
    int update(PermGroupEntity group);

    /** 逻辑删除；子分组由 service 层一并处理 */
    @Update("UPDATE tb_perm_group SET deleted = 1 WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM tb_perm_group WHERE parent_id = #{id} AND deleted = 0")
    long countChildren(@Param("id") Long id);

    // ---------- 成员 ----------

    @Select("SELECT u.id, u.username, u.nickname, u.role FROM tb_perm_group_member m "
            + "JOIN tb_sys_user u ON m.user_id = u.id "
            + "WHERE m.group_id = #{groupId} AND u.deleted = 0 ORDER BY u.id")
    List<Map<String, Object>> selectMembers(@Param("groupId") Long groupId);

    @Select("SELECT user_id FROM tb_perm_group_member WHERE group_id = #{groupId}")
    List<Long> selectMemberIds(@Param("groupId") Long groupId);

    @Insert("INSERT IGNORE INTO tb_perm_group_member (group_id, user_id) VALUES (#{groupId}, #{userId})")
    int addMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("DELETE FROM tb_perm_group_member WHERE group_id = #{groupId}")
    int clearMembers(@Param("groupId") Long groupId);

    // ---------- 权限点 ----------

    @Select("SELECT perm_code FROM tb_perm_group_perm WHERE group_id = #{groupId}")
    List<String> selectPermCodes(@Param("groupId") Long groupId);

    @Insert("INSERT IGNORE INTO tb_perm_group_perm (group_id, perm_code) VALUES (#{groupId}, #{permCode})")
    int addPerm(@Param("groupId") Long groupId, @Param("permCode") String permCode);

    @Delete("DELETE FROM tb_perm_group_perm WHERE group_id = #{groupId}")
    int clearPerms(@Param("groupId") Long groupId);

    /** 用户所属的启用分组（builtin 分组代表「全部权限」，由 service 层特殊处理） */
    @Select("SELECT g.* FROM tb_perm_group g "
            + "JOIN tb_perm_group_member gm ON gm.group_id = g.id "
            + "WHERE gm.user_id = #{userId} AND g.deleted = 0 AND g.status = 1")
    List<PermGroupEntity> selectGroupsByUser(@Param("userId") Long userId);

    /** 用户有效权限：所属（非内置）分组的权限点并集 */
    @Select("SELECT DISTINCT gp.perm_code FROM tb_perm_group_perm gp "
            + "JOIN tb_perm_group_member gm ON gm.group_id = gp.group_id "
            + "JOIN tb_perm_group g ON g.id = gp.group_id AND g.deleted = 0 AND g.status = 1 AND g.builtin = 0 "
            + "WHERE gm.user_id = #{userId}")
    List<String> selectPermCodesByUser(@Param("userId") Long userId);

    /** 各分组成员数（聚合，避免列表页 N+1） */
    @Select("SELECT group_id, COUNT(*) AS cnt FROM tb_perm_group_member GROUP BY group_id")
    List<Map<String, Object>> countMembers();

    /** 各分组权限点数 */
    @Select("SELECT group_id, COUNT(*) AS cnt FROM tb_perm_group_perm GROUP BY group_id")
    List<Map<String, Object>> countPerms();
}
