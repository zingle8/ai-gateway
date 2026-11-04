package com.zimo.gateway.overview.mapper;

import com.zimo.gateway.overview.model.StatsAgg;
import com.zimo.gateway.overview.model.StatsQuery;
import com.zimo.gateway.overview.model.StatsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 网关总览 / 数据统计 的全部 SQL（原生 MyBatis，见 resources/mapper/OverviewMapper.xml）。
 *
 * <p>所有返回 Map 的查询都用列别名直接给出前端需要的字段名，避免 Java 侧二次搬运。
 */
@Mapper
public interface OverviewMapper {

    // ---------------------------------------------------------------- 总览

    long countApps();

    long countServices();

    long countOnlineServices();

    long countApis();

    /** 近 60 秒调用数，用于折算实时 QPS */
    long countLastMinute();

    StatsAgg todayStats();

    /** 今日 P95 / P99：MySQL 5.7 无窗口函数，用排序 + 偏移量 */
    Long todayPercentile(@Param("offset") int offset);

    /** 已注册服务实例 + 近 5 分钟真实调用聚合 */
    List<Map<String, Object>> instances();

    // ---------------------------------------------------------------- 趋势（总览 / 统计共用）

    /** 按 epoch 秒分桶，返回 b（桶起点秒）、c（桶内请求数） */
    List<Map<String, Object>> trendBuckets(StatsQuery query);

    // ---------------------------------------------------------------- 统计字典

    List<Map<String, Object>> filterApps();

    List<String> distinctServices();

    List<String> distinctApis();

    List<String> distinctMethods();

    // ---------------------------------------------------------------- 统计主体

    long statsAppCount();

    long statsApiCount();

    StatsAgg statsAgg(StatsQuery query);

    Long statsPercentile(StatsQuery query);

    List<StatsRow> statusDist(StatsQuery query);

    List<StatsRow> methodDist(StatsQuery query);

    List<StatsRow> topServices(StatsQuery query);

    List<StatsRow> topApis(StatsQuery query);
}
