package com.zimo.gateway.trace.service.impl;

import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.client.common.enums.ErrType;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.TimeRange;
import com.zimo.gateway.trace.entity.GwTraceLogEntity;
import com.zimo.gateway.trace.entity.GwTraceSpanEntity;
import com.zimo.gateway.trace.mapper.GwServiceEdgeMapper;
import com.zimo.gateway.trace.mapper.GwTraceLogMapper;
import com.zimo.gateway.trace.mapper.GwTraceSpanMapper;
import com.zimo.gateway.trace.model.SpanDTO;
import com.zimo.gateway.trace.model.TraceQuery;
import com.zimo.gateway.trace.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日志追踪服务实现
 */
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final GwTraceLogMapper traceLogMapper;

    private final GwTraceSpanMapper traceSpanMapper;

    private final GwServiceEdgeMapper edgeMapper;
    private final com.zimo.gateway.trace.mapper.GwWsSessionMapper wsSessionMapper;
    private final com.zimo.gateway.trace.mapper.GwWsFrameMapper wsFrameMapper;

    @Override
    public Page<GwTraceLogEntity> pageTraces(TraceQuery query) {
        long total = traceLogMapper.countPage(query);
        long current = Math.max(1, query.getCurrent());
        long size = Math.max(1, query.getSize());
        // 计数与列表共用同一 WHERE 片段，total 为 0 时不再查列表
        List<GwTraceLogEntity> records = total == 0 ? List.of() : traceLogMapper.selectPage(query);
        return Page.of(records, total, current, size);
    }

    @Override
    public GwTraceLogEntity getDetail(Long id) {
        GwTraceLogEntity log = traceLogMapper.selectDetailById(id);
        if (log == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "日志不存在：" + id);
        }
        return log;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSave(List<TraceLogDTO> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0;
        }
        List<GwTraceLogEntity> entities = new ArrayList<>(logs.size());
        for (TraceLogDTO dto : logs) {
            if (dto == null) {
                continue;
            }
            entities.add(convert(dto));
        }
        if (entities.isEmpty()) {
            return 0;
        }
        traceLogMapper.batchInsert(entities);
        return entities.size();
    }

    @Override
    public int batchSaveSpans(List<SpanDTO> spans) {
        if (spans == null || spans.isEmpty()) {
            return 0;
        }
        List<GwTraceSpanEntity> entities = new ArrayList<>(spans.size());
        for (SpanDTO dto : spans) {
            if (dto == null || isBlank(dto.getTraceId()) || isBlank(dto.getSpanId())) {
                continue; // 缺主键无法组链，丢弃
            }
            GwTraceSpanEntity e = new GwTraceSpanEntity();
            BeanUtils.copyProperties(dto, e);
            // 同 log：span 的归属列存服务ID
            e.setServiceName(isBlank(dto.getServiceId()) ? dto.getServiceName() : dto.getServiceId());
            if (e.getStartTime() == null) {
                e.setStartTime(LocalDateTime.now());
            }
            entities.add(e);
        }
        if (entities.isEmpty()) {
            return 0;
        }
        traceSpanMapper.batchInsert(entities);
        return entities.size();
    }

    @Override
    public List<SpanDTO> listSpans(String traceId) {
        if (isBlank(traceId)) {
            return List.of();
        }
        List<GwTraceSpanEntity> spans = traceSpanMapper.selectByTraceId(traceId);
        List<SpanDTO> result = new ArrayList<>(spans.size());
        for (GwTraceSpanEntity e : spans) {
            SpanDTO dto = new SpanDTO();
            BeanUtils.copyProperties(e, dto);
            result.add(dto);
        }
        return result;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public Map<String, Object> topology(String range, String appId, String service) {
        TimeRange timeRange = TimeRange.of(range);
        LocalDateTime since = timeRange.since();
        // 预聚合表是小时窗口，分钟级区间直接查 span 表才能保证精度；
        // 30 分钟以内的 span 量很小，走 idx_edge 实时聚合成本可接受
        List<Map<String, Object>> edges = timeRange.getWindowSeconds() <= 1800
                ? edgeMapper.selectEdgesRealtime(since)
                : edgeMapper.selectEdges(since);

        // 全局数据 + 过滤：默认展示所有应用 × 所有服务的真实调用边，
        // 传了 appId / service 时在内存里裁剪（边表量级为服务对数量，内存过滤足够快）
        List<Map<String, Object>> filtered = edges;
        if (!isBlank(appId)) {
            Set<String> appServices = new HashSet<>(traceLogMapper.selectServiceNamesByApp(appId));
            filtered = filtered.stream().filter(e ->
                    appServices.contains(String.valueOf(e.get("src")))
                            || appServices.contains(String.valueOf(e.get("dst")))).toList();
        }
        if (!isBlank(service)) {
            filtered = filtered.stream().filter(e ->
                    service.equals(String.valueOf(e.get("src")))
                            || service.equals(String.valueOf(e.get("dst")))).toList();
        }
        edges = filtered;

        // 节点在 edge 基础上推导：入调用 / 出调用次数，前端免二次汇总
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> e : edges) {
            String src = String.valueOf(e.get("src"));
            String dst = String.valueOf(e.get("dst"));
            long calls = ((Number) e.get("calls")).longValue();
            addCalls(nodeOf(nodes, src), "outCalls", calls);
            addCalls(nodeOf(nodes, dst), "inCalls", calls);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", timeRange.getCode());
        result.put("appId", appId);
        result.put("service", service);
        result.put("realtime", timeRange.getWindowSeconds() <= 1800);
        result.put("edges", edges);
        result.put("nodes", new ArrayList<>(nodes.values()));
        return result;
    }

    @Override
    public List<Map<String, Object>> slowTraces(String range, int limit) {
        LocalDateTime since = TimeRange.of(range).since();
        int top = Math.min(Math.max(1, limit), 50);
        List<Map<String, Object>> traces = edgeMapper.selectSlowTraces(since, top);
        if (traces.isEmpty()) {
            return traces;
        }
        // 补根 span 信息：哪个服务的哪个接口发起
        List<String> traceIds = traces.stream().map(t -> String.valueOf(t.get("traceId"))).toList();
        Map<String, Map<String, Object>> roots = new LinkedHashMap<>();
        for (Map<String, Object> r : edgeMapper.selectRootSpans(traceIds)) {
            roots.put(String.valueOf(r.get("traceId")), r);
        }
        for (Map<String, Object> t : traces) {
            Map<String, Object> root = roots.get(String.valueOf(t.get("traceId")));
            t.put("rootService", root == null ? "-" : root.get("rootService"));
            t.put("rootApi", root == null ? "-" : root.get("rootApi"));
        }
        return traces;
    }

    private Map<String, Object> newNode(String name) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("name", name);
        n.put("inCalls", 0L);
        n.put("outCalls", 0L);
        return n;
    }

    private Map<String, Object> nodeOf(Map<String, Map<String, Object>> nodes, String name) {
        return nodes.computeIfAbsent(name, this::newNode);
    }

    private void addCalls(Map<String, Object> node, String key, long delta) {
        long cur = ((Number) node.get(key)).longValue();
        node.put(key, cur + delta);
    }

    @Override
    public Map<String, Object> filterOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("apps", traceLogMapper.selectDistinctApps());
        options.put("protocols", traceLogMapper.selectDistinctProtocols());
        options.put("statusCodes", traceLogMapper.selectDistinctStatusCodes());
        options.put("methods", traceLogMapper.selectDistinctMethods());
        options.put("services", traceLogMapper.selectDistinctServices());
        options.put("apis", traceLogMapper.selectDistinctApis());
        options.put("reqFormats", traceLogMapper.selectDistinctReqFormats());
        options.put("resFormats", traceLogMapper.selectDistinctResFormats());
        return options;
    }

    private GwTraceLogEntity convert(TraceLogDTO dto) {
        GwTraceLogEntity entity = new GwTraceLogEntity();
        BeanUtils.copyProperties(dto, entity);
        // 归属列统一存服务ID（英文唯一标识）：新版 SDK 传 serviceId，旧版/仅传 serviceName 时回退；
        // 中文服务名只在 tb_gw_service 维护，展示层按 ID 映射
        entity.setServiceName(isBlank(dto.getServiceId()) ? dto.getServiceName() : dto.getServiceId());
        if (entity.getReqTime() == null) {
            entity.setReqTime(LocalDateTime.now());
        }
        if (entity.getResTime() == null) {
            entity.setResTime(entity.getReqTime());
        }
        if (entity.getErrType() == null) {
            entity.setErrType(ErrType.resolve(entity.getStatusCode()));
        }
        if (entity.getReqBytes() == null) {
            entity.setReqBytes(0L);
        }
        if (entity.getResBytes() == null) {
            entity.setResBytes(0L);
        }
        entity.setCreateTime(LocalDateTime.now());
        entity.setDeleted(0);
        return entity;
    }

    @Override
    public Page<com.zimo.gateway.trace.entity.GwWsSessionEntity> pageWsSessions(com.zimo.gateway.trace.model.WsSessionQuery query) {
        long total = wsSessionMapper.countPage(query);
        long current = Math.max(1, query.getCurrent());
        long size = Math.max(1, query.getSize());
        List<com.zimo.gateway.trace.entity.GwWsSessionEntity> records = total == 0
                ? List.of() : wsSessionMapper.selectPage(query);
        return Page.of(records, total, current, size);
    }

    @Override
    public List<com.zimo.gateway.trace.entity.GwWsFrameEntity> wsFrames(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return List.of();
        }
        return wsFrameMapper.listBySessionKey(sessionKey);
    }
}
