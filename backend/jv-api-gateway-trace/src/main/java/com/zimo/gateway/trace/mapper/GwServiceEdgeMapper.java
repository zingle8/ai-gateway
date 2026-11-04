package com.zimo.gateway.trace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 服务调用拓扑预聚合表（tb_gw_service_edge，EdgeAggregator 维护） */
@Mapper
public interface GwServiceEdgeMapper {

    /**
     * 重算一个小时窗口：先清空该窗口旧的聚合结果，再从 span 表重新聚合写入。
     *
     * <p>必须「先删后插」而不是纯 UPSERT —— 否则 span 被清理或修正后，
     * 已不存在的边会永久残留在聚合表里。
     *
     * @return 写入的边数
     */
    default int rebuildWindow(LocalDateTime windowStart) {
        deleteWindow(windowStart);
        return insertWindow(windowStart);
    }

    int deleteWindow(@Param("windowStart") LocalDateTime windowStart);

    int insertWindow(@Param("windowStart") LocalDateTime windowStart);

    int deleteBefore(@Param("time") LocalDateTime time);

    /** 时间窗内的调用边（拓扑图）：按 src/dst 跨窗口汇总 */
    List<Map<String, Object>> selectEdges(@Param("since") LocalDateTime since);

    /**
     * 分钟级区间直接对 span 表实时聚合调用边。
     *
     * <p>预聚合表是小时窗口，1~30 分钟区间查它会拿到整小时的累计值；
     * 短区间内 span 量很小，走 idx_edge(service_name, peer_service, start_time) 成本可接受。
     */
    List<Map<String, Object>> selectEdgesRealtime(@Param("since") LocalDateTime since);

    /** 慢链路 TopN：时间窗内按链路总耗时（首 span 起点到最晚 span 终点）降序 */
    List<Map<String, Object>> selectSlowTraces(@Param("since") LocalDateTime since,
                                               @Param("limit") int limit);

    /** 慢链路的根 span 信息（parent 为空即链路入口） */
    List<Map<String, Object>> selectRootSpans(@Param("traceIds") List<String> traceIds);
}
