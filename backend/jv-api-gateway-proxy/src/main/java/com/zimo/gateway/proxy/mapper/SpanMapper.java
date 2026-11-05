package com.zimo.gateway.proxy.mapper;

import com.zimo.gateway.proxy.trace.SpanRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 调用链路 span 批量写入（tb_gw_trace_span，按天分区，见 backend/sql/jv-api-gateway-trace-span-tb_gw_trace_span.sql） */
@Mapper
public interface SpanMapper {

    int batchInsert(@Param("spans") List<SpanRecord> spans);
}
