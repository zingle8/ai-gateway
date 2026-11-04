package com.zimo.gateway.discovery.mapper;

import com.zimo.gateway.discovery.entity.GwService;
import com.zimo.gateway.discovery.model.FilterOption;
import com.zimo.gateway.discovery.model.ServiceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 服务实例 Mapper（原生 MyBatis，SQL 见 resources/mapper/GwServiceMapper.xml）
 */
@Mapper
public interface GwServiceMapper {

    /** 分页列表（动态条件） */
    List<GwService> selectPage(ServiceQuery query);

    /** 与 selectPage 同条件计数 */
    long countPage(ServiceQuery query);

    GwService selectById(@Param("id") Long id);

    /**
     * 近 windowSeconds 秒按服务名聚合真实调用：QPS（近 60 秒计数）、平均耗时、入/出流量。
     * 返回列名 service_name / qps / avg_cost / in_bytes / out_bytes。
     */
    List<Map<String, Object>> selectRuntimeStats(@Param("windowSeconds") int windowSeconds);

    /** 注册 / 心跳按服务名定位 */
    GwService selectByServiceName(@Param("serviceName") String serviceName);

    /** 筛选项字典：应用列表（app_id + app_name），供「全部应用」下拉 */
    List<FilterOption> selectDistinctApps();

    /** 筛选项字典：服务列表（app_id + service_name），供「全部服务」下拉并按应用级联 */
    List<FilterOption> selectDistinctServices();

    /** 筛选项字典：语言类型列表 */
    List<String> selectDistinctLanguages();

    /** 新增，主键自增回填到 entity.id */
    int insert(GwService service);

    /** 按非空字段更新 */
    int update(GwService service);

    /** 下线：按服务名把状态置为异常 */
    int markAbnormalByName(@Param("serviceName") String serviceName,
                           @Param("now") LocalDateTime now);

    /**
     * 把超时未上报心跳的服务标记为离线。
     *
     * @param status   目标状态，通常为「离线」
     * @param deadline 心跳截止时间，早于该时间视为超时
     * @param now      更新时间
     * @return 受影响行数
     */
    int markOffline(@Param("status") String status,
                    @Param("deadline") LocalDateTime deadline,
                    @Param("now") LocalDateTime now);
}
