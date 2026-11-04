package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户数据访问（原生 MyBatis，SQL 见 resources/mapper/UserMapper.xml）
 */
@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    /** 账号唯一性校验；excludeId 修改场景排除自身 */
    long countByUsername(@Param("username") String username, @Param("excludeId") Long excludeId);

    List<User> selectPage(@Param("keyword") String keyword,
                          @Param("role") String role,
                          @Param("status") Integer status,
                          @Param("offset") long offset,
                          @Param("size") long size);

    long countPage(@Param("keyword") String keyword,
                   @Param("role") String role,
                   @Param("status") Integer status);

    /** 新增，主键自增回填到 entity.id */
    int insert(User user);

    /** 按非空字段更新 */
    int update(User user);

    /** 逻辑删除 */
    int deleteById(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}
