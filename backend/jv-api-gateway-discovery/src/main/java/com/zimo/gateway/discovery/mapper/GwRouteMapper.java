package com.zimo.gateway.discovery.mapper;

import com.zimo.gateway.discovery.entity.GwRoute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 转发路由 Mapper（原生 MyBatis，SQL 见 resources/mapper/GwRouteMapper.xml）
 */
@Mapper
public interface GwRouteMapper {

    /** 全量列表（关键字可选），按 id 倒序 */
    List<GwRoute> selectList(@Param("keyword") String keyword);

    GwRoute selectById(@Param("id") Long id);

    /** 匹配路径唯一性校验；excludeId 修改场景排除自身 */
    long countByPathPrefix(@Param("pathPrefix") String pathPrefix, @Param("excludeId") Long excludeId);

    /** 新增，主键自增回填到 entity.id */
    int insert(GwRoute route);

    /** 按非空字段更新 */
    int update(GwRoute route);

    /** 逻辑删除 */
    int deleteById(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}
