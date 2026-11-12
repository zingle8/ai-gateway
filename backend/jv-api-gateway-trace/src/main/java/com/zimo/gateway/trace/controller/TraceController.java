package com.zimo.gateway.trace.controller;

import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.common.result.PageResult;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.trace.entity.GwTraceLogEntity;
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
    public R<PageResult<GwTraceLogEntity>> page(@RequestParam(required = false) String traceId,
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
     * 导出当前筛选结果（CSV，最多 10000 行；带操作人与导出时间列）
     */
    @GetMapping("/logs/export")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) String traceId,
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
            jakarta.servlet.http.HttpServletRequest httpRequest) {
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
        query.setCurrent(1);
        query.setSize(10000);
        com.zimo.gateway.common.result.Page<GwTraceLogEntity> result = traceService.pageTraces(query);
        // 导出留痕：优先取控制台传入的操作人（网关鉴权后前端携带），否则回落上下文
        String operator = httpRequest.getHeader("X-Username");
        if (operator == null || operator.isBlank()) {
            operator = com.zimo.gateway.common.context.UserContext.getUsername();
        }
        if (operator == null || operator.isBlank()) {
            Long uid = com.zimo.gateway.common.context.UserContext.getUserId();
            operator = uid == null ? "system" : "uid:" + uid;
        }
        String exportTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder csv = new StringBuilder("调用时间,应用,服务,接口,状态码,耗时(ms),协议,错误类型,操作人,导出时间\n");
        for (GwTraceLogEntity e : result.getRecords()) {
            csv.append(csvCell(e.getReqTime())).append(',')
                    .append(csvCell(e.getAppName())).append(',')
                    .append(csvCell(e.getServiceName())).append(',')
                    .append(csvCell(e.getApi())).append(',')
                    .append(csvCell(e.getStatusCode())).append(',')
                    .append(csvCell(e.getCost())).append(',')
                    .append(csvCell(e.getProtocol())).append(',')
                    .append(csvCell(e.getErrType())).append(',')
                    .append(csvCell(operator)).append(',')
                    .append(csvCell(exportTime)).append('\n');
        }
        byte[] bytes = ("\ufeff" + csv).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"trace-logs.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return text.contains(",") || text.contains("\"") || text.contains("\n")
                ? "\"" + text + "\"" : text;
    }

    /**
     * 日志详情
     */
    @GetMapping("/logs/{id}")
    public R<GwTraceLogEntity> detail(@PathVariable Long id) {
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
     * 服务拓扑：默认全局（所有应用 × 所有服务的真实调用边），支持按应用 / 服务过滤。
     *
     * @param range   统一时间区间：1m / 5m / ... / 30d，默认 24h
     * @param appId   应用过滤（可选），只保留该应用下服务的调用边
     * @param service 服务过滤（可选），只保留以该服务为一端的调用边
     */
    @GetMapping("/topology")
    public R<Map<String, Object>> topology(@RequestParam(defaultValue = "24h") String range,
                                           @RequestParam(required = false) String appId,
                                           @RequestParam(required = false) String service) {
        return R.ok(traceService.topology(range, appId, service));
    }

    /**
     * 慢链路 TopN：区间内按链路总耗时降序
     *
     * @param range 统一时间区间（同 topology）
     */
    @GetMapping("/slow")
    public R<List<Map<String, Object>>> slow(@RequestParam(defaultValue = "24h") String range,
                                             @RequestParam(defaultValue = "10") int limit) {
        return R.ok(traceService.slowTraces(range, limit));
    }

    /**
     * WebSocket 会话分页列表（proxy 旁路采集落库 tb_gw_ws_session）
     */
    @GetMapping("/ws/sessions")
    public R<PageResult<com.zimo.gateway.trace.entity.GwWsSessionEntity>> wsSessions(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String closeStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        com.zimo.gateway.trace.model.WsSessionQuery query = new com.zimo.gateway.trace.model.WsSessionQuery();
        query.setTraceId(traceId);
        query.setPath(path);
        query.setCloseStatus(closeStatus);
        query.setStartTime(startTime);
        query.setCurrent(current);
        query.setSize(size);
        return R.ok(PageResult.of(traceService.pageWsSessions(query)));
    }

    /**
     * WebSocket 会话帧明细（tb_gw_ws_frame，按 sessionKey 关联）
     */
    @GetMapping("/ws/frames")
    public R<java.util.List<com.zimo.gateway.trace.entity.GwWsFrameEntity>> wsFrames(@RequestParam String sessionKey) {
        return R.ok(traceService.wsFrames(sessionKey));
    }
}
