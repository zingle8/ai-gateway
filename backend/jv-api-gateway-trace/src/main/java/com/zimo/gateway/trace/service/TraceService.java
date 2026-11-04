package com.zimo.gateway.trace.service;

import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.trace.entity.GwTraceLog;
import com.zimo.gateway.trace.model.SpanDTO;
import com.zimo.gateway.trace.model.TraceQuery;

import java.util.List;
import java.util.Map;

/**
 * 日志追踪服务
 */
public interface TraceService {

    /**
     * 调用日志分页列表（多维过滤）
     */
    Page<GwTraceLog> pageTraces(TraceQuery query);

    /**
     * 日志详情（含 Header 与报文体）
     */
    GwTraceLog getDetail(Long id);

    /**
     * 批量上报（供业务系统接入 SDK 调用）
     *
     * @return 成功入库条数
     */
    int batchSave(List<TraceLogDTO> logs);

    /** 批量上报链路 span（SDK / 网关写入 tb_gw_trace_span） */
    int batchSaveSpans(List<SpanDTO> spans);

    /** 链路详情：按 traceId 取全量 span（瀑布图），start_time 升序 */
    List<SpanDTO> listSpans(String traceId);

    /**
     * 服务拓扑：区间内调用边列表（默认全局：所有应用 × 所有服务的真实调用边）。
     *
     * <p>短区间（≤30 分钟）直接对 tb_gw_trace_span 实时聚合以保证分钟级精度，
     * 更长区间查 tb_gw_service_edge 预聚合表（小时窗口）避免大表 GROUP BY。
     *
     * @param range   统一时间区间 code（1m ~ 30d）
     * @param appId   应用过滤（可选）：只保留该应用下服务的调用边
     * @param service 服务过滤（可选）：只保留以该服务为一端的调用边
     * @return {edges: [{src,dst,calls,errors,avgCost,maxCost}], nodes: [{name,inCalls,outCalls}]}
     */
    Map<String, Object> topology(String range, String appId, String service);

    /** 慢链路 TopN：区间内按链路总耗时降序（含根服务/根接口信息） */
    List<Map<String, Object>> slowTraces(String range, int limit);

    /**
     * 筛选项字典：协议 / 服务名 / 请求格式 / 返回格式，取库中真实出现过的值。
     *
     * @return key 为 protocols / services / reqFormats / resFormats
     */
    Map<String, Object> filterOptions();
}
