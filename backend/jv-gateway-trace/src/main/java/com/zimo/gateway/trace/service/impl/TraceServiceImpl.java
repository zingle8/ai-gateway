package com.zimo.gateway.trace.service.impl;

import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.client.common.enums.ErrType;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.trace.entity.GwTraceLog;
import com.zimo.gateway.trace.mapper.GwTraceLogMapper;
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
    public Map<String, List<String>> filterOptions() {
        Map<String, List<String>> options = new LinkedHashMap<>();
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
