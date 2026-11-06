package com.zimo.gateway.discovery.mapper;

import com.zimo.gateway.discovery.entity.GwServiceEntity;
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
    List<GwServiceEntity> selectPage(ServiceQuery query);

    /** 与 selectPage 同条件计数 */
    long countPage(ServiceQuery query);

    GwServiceEntity selectById(@Param("id") Long id);

    /**
     * 近 windowSeconds 秒按服务ID聚合真实调用：QPS（近 60 秒计数）、调用次数、平均耗时、入/出流量。
     * 返回列名 service_name / qps / calls / avg_cost / in_bytes / out_bytes。
     */
    List<Map<String, Object>> selectRuntimeStats(@Param("windowSeconds") int windowSeconds);

    /** 已登记接口数（tb_gw_api 按服务ID汇总），返回列 service_name / api_count */
    List<Map<String, Object>> selectApiCounts();

    /**
     * 趋势分桶：区间内按桶起点聚合请求数（稀疏桶，b = 桶起点 epoch 秒 / c = 请求数），
     * 支持按应用 / 服务ID过滤（tb_gw_trace_log.service_name 存的是服务ID），补点由 TrendBuckets 完成。
     */
    List<Map<String, Object>> selectTrendBuckets(@Param("since") java.time.LocalDateTime since,
                                                 @Param("bucketSec") long bucketSec,
                                                 @Param("appId") String appId,
                                                 @Param("serviceId") String serviceId);

    /** 注册 / 心跳按服务ID（英文唯一标识）定位 */
    GwServiceEntity selectByServiceId(@Param("serviceId") String serviceId);

    /** 筛选项字典：应用列表（app_id + app_name），供「全部应用」下拉 */
    List<FilterOption> selectDistinctApps();

    /** 筛选项字典：服务列表（app_id + service_id + service_name），供「全部服务」下拉并按应用级联 */
    List<FilterOption> selectDistinctServices();

    /** 筛选项字典：语言类型列表 */
    List<String> selectDistinctLanguages();

    /** 新增，主键自增回填到 entity.id */
    int insert(GwServiceEntity service);

    /** 按非空字段更新 */
    int update(GwServiceEntity service);

    /** 主动下线：按服务ID把状态置为离线（心跳恢复时覆盖回健康） */
    int markAbnormalByServiceId(@Param("serviceId") String serviceId,
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
