package com.zimo.gateway.proxy.mapper;

import com.zimo.gateway.proxy.trace.TraceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调用日志批量落库（原生 MyBatis，SQL 见 resources/mapper/TraceLogMapper.xml）。
 */
@Mapper
public interface TraceLogMapper {

    /** 队列攒批后一次 INSERT，避免逐条往返 */
    int batchInsert(@Param("records") List<TraceRecord> records);
}
