package com.zimo.gateway.trace.controller;

import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.common.result.PageResult;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.trace.entity.GwTraceLog;
import com.zimo.gateway.trace.model.SpanDTO;
import com.zimo.gateway.trace.model.TraceQuery;
import com.zimo.gateway.trace.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 日志追踪接口
 */
@RestController
@RequestMapping("/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    /**
     * 调用日志分页列表
     */
    @GetMapping("/logs")
    public R<PageResult<GwTraceLog>> page(@RequestParam(required = false) String traceId,
                                          @RequestParam(required = false) String appId,
                                          @RequestParam(required = false) String service,
                                          @RequestParam(required = false) String api,
                                          @RequestParam(required = false) String params,
                                          @RequestParam(required = false) String method,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) String errType,
                                          @RequestParam(required = false) String protocol,
                                          @RequestParam(required = false) String reqFormat,
                                          @RequestParam(required = false) String resFormat,
                                          @RequestParam(required = false) String startTime,
                                          @RequestParam(required = false) Integer costMin,
                                          @RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size) {
        TraceQuery query = new TraceQuery();
        query.setTraceId(traceId);
        query.setAppId(appId);
        query.setService(service);
        query.setApi(api);
        query.setParams(params);
        query.setMethod(method);
        query.setStatus(status);
        query.setErrType(errType);
        query.setProtocol(protocol);
        query.setReqFormat(reqFormat);
        query.setResFormat(resFormat);
        query.setStartTime(startTime);
        query.setCostMin(costMin);
        query.setCurrent(current);
        query.setSize(size);
        return R.ok(PageResult.of(traceService.pageTraces(query)));
    }

    /**
     * 日志详情
     */
    @GetMapping("/logs/{id}")
    public R<GwTraceLog> detail(@PathVariable Long id) {
        return R.ok(traceService.getDetail(id));
    }

    /**
     * 筛选项字典：协议 / 服务名 / 请求格式 / 返回格式。
     *
     * <p>取库中真实出现过的值给下拉框，避免前端写死枚举后与实际数据对不上。
     * 注：必须声明在 {@code /logs/{id}} 之前语义等价——Spring 按字面量优先匹配，不会被 {id} 吞掉。
     */
    @GetMapping("/logs/filters")
    public R<Map<String, Object>> filters() {
        return R.ok(traceService.filterOptions());
    }

    /**
     * 批量上报调用日志（业务系统接入 SDK 使用）
     */
    @PostMapping("/logs/batch")
    public R<Integer> batchSave(@RequestBody List<TraceLogDTO> logs) {
        return R.ok(traceService.batchSave(logs));
    }

    /**
     * 批量上报链路 span（业务系统接入 SDK 使用）
     */
    @PostMapping("/spans/batch")
    public R<Integer> batchSaveSpans(@RequestBody List<SpanDTO> spans) {
        return R.ok(traceService.batchSaveSpans(spans));
    }

    /**
     * 链路详情：按 traceId 取全量 span，前端组树画瀑布图。
     * span 为空说明该请求早于链路功能上线（只有调用日志，没有 span）。
     */
    @GetMapping("/traces/{traceId}")
    public R<List<SpanDTO>> trace(@PathVariable String traceId) {
        return R.ok(traceService.listSpans(traceId));
    }

    /**
     * 服务拓扑：时间窗内调用边 + 节点（来自 tb_gw_service_edge 预聚合表）
     */
    @GetMapping("/topology")
    public R<Map<String, Object>> topology(@RequestParam(defaultValue = "24") int hours) {
        return R.ok(traceService.topology(hours));
    }

    /**
     * 慢链路 TopN：时间窗内按链路总耗时降序
     */
    @GetMapping("/slow")
    public R<List<Map<String, Object>>> slow(@RequestParam(defaultValue = "24") int hours,
                                             @RequestParam(defaultValue = "10") int limit) {
        return R.ok(traceService.slowTraces(hours, limit));
    }
}
