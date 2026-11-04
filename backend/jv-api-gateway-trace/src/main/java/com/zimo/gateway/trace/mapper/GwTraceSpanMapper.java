package com.zimo.gateway.trace.mapper;

import com.zimo.gateway.trace.entity.GwTraceSpan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 调用链路 span 表（tb_gw_trace_span，按天分区）。
 *
 * <p>除数据读写外还承担分区维护查询：分区信息来自 information_schema.PARTITIONS，
 * DDL（ADD/DROP PARTITION）由 PartitionMaintainer 用 ensurePartition/dropPartition 执行。
 */
@Mapper
public interface GwTraceSpanMapper {

    int batchInsert(@Param("spans") List<GwTraceSpan> spans);

    /** 按链路聚合（瀑布图），start_time 升序 */
    List<GwTraceSpan> selectByTraceId(@Param("traceId") String traceId);

    // ------------------------------------------------------------- 分区维护

    /** 现有分区：name / description（LESS THAN 的 TO_DAYS 值） */
    List<Map<String, Object>> selectPartitions(@Param("tableName") String tableName);

    int addPartition(@Param("tableName") String tableName,
                     @Param("partitionName") String partitionName,
                     @Param("lessThanDays") int lessThanDays);

    int dropPartition(@Param("tableName") String tableName,
                      @Param("partitionName") String partitionName);
}
