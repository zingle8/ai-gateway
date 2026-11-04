package com.zimo.gateway.trace.service.impl;

import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.client.common.enums.ErrType;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.trace.entity.GwTraceLog;
import com.zimo.gateway.trace.entity.GwTraceSpan;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志追踪服务实现
 */
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final GwTraceLogMapper traceLogMapper;

    private final GwTraceSpanMapper traceSpanMapper;

    private final GwServiceEdgeMapper edgeMapper;

    @Override
    public Page<GwTraceLog> pageTraces(TraceQuery query) {
        long total = traceLogMapper.countPage(query);
        long current = Math.max(1, query.getCurrent());
        long size = Math.max(1, query.getSize());
        // 计数与列表共用同一 WHERE 片段，total 为 0 时不再查列表
        List<GwTraceLog> records = total == 0 ? List.of() : traceLogMapper.selectPage(query);
        return Page.of(records, total, current, size);
    }

    @Override
    public GwTraceLog getDetail(Long id) {
        GwTraceLog log = traceLogMapper.selectDetailById(id);
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
        List<GwTraceLog> entities = new ArrayList<>(logs.size());
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
        List<GwTraceSpan> entities = new ArrayList<>(spans.size());
        for (SpanDTO dto : spans) {
            if (dto == null || isBlank(dto.getTraceId()) || isBlank(dto.getSpanId())) {
                continue; // 缺主键无法组链，丢弃
            }
            GwTraceSpan e = new GwTraceSpan();
            BeanUtils.copyProperties(dto, e);
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
        List<GwTraceSpan> spans = traceSpanMapper.selectByTraceId(traceId);
        List<SpanDTO> result = new ArrayList<>(spans.size());
        for (GwTraceSpan e : spans) {
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
    public Map<String, Object> topology(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(clampHours(hours));
        List<Map<String, Object>> edges = edgeMapper.selectEdges(since);

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
        result.put("edges", edges);
        result.put("nodes", new ArrayList<>(nodes.values()));
        return result;
    }

    @Override
    public List<Map<String, Object>> slowTraces(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(clampHours(hours));
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

    private int clampHours(int hours) {
        return Math.min(Math.max(1, hours), 24 * 7);
    }

    @Override
    public Map<String, Object> filterOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("apps", traceLogMapper.selectDistinctApps());
        options.put("protocols", traceLogMapper.selectDistinctProtocols());
        options.put("methods", traceLogMapper.selectDistinctMethods());
        options.put("services", traceLogMapper.selectDistinctServices());
        options.put("apis", traceLogMapper.selectDistinctApis());
        options.put("reqFormats", traceLogMapper.selectDistinctReqFormats());
        options.put("resFormats", traceLogMapper.selectDistinctResFormats());
        return options;
    }

    private GwTraceLog convert(TraceLogDTO dto) {
        GwTraceLog entity = new GwTraceLog();
        BeanUtils.copyProperties(dto, entity);
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
}
