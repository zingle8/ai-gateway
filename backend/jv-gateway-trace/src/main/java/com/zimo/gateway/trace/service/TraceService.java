package com.zimo.gateway.trace.service;

import com.zimo.gateway.client.common.dto.TraceLogDTO;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.trace.entity.GwTraceLog;
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

    /**
     * 筛选项字典：协议 / 服务名 / 请求格式 / 返回格式，取库中真实出现过的值。
     *
     * @return key 为 protocols / services / reqFormats / resFormats
     */
    Map<String, Object> filterOptions();
}
