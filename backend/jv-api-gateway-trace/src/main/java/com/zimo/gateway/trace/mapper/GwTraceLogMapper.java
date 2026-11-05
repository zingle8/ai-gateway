package com.zimo.gateway.trace.mapper;

import com.zimo.gateway.trace.entity.GwTraceLogEntity;
import com.zimo.gateway.trace.model.FilterOption;
import com.zimo.gateway.trace.model.TraceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调用日志数据访问（原生 MyBatis，SQL 见 resources/mapper/GwTraceLogMapper.xml）
 */
@Mapper
public interface GwTraceLogMapper {

    /** 列表查询：不取 Header / 报文体大字段，详情接口再取 */
    List<GwTraceLogEntity> selectPage(TraceQuery query);

    /** 与 selectPage 同条件计数 */
    long countPage(TraceQuery query);

    /** 详情：含全部大字段 */
    GwTraceLogEntity selectDetailById(@Param("id") Long id);

    /** 批量入库，主键自增 */
    int batchInsert(@Param("logs") List<GwTraceLogEntity> logs);

    /**
     * 筛选项字典：库中真实出现过的协议 / 服务名 / 报文格式。
     *
     * <p>下拉框用它填充，避免前端写死枚举——新接入 gRPC、WebSocket 时选项自动出现。
     */
    List<String> selectDistinctProtocols();

    /** HTTP 方法字典（从 api 前缀解析） */
    List<String> selectDistinctMethods();

    List<FilterOption> selectDistinctServices();

    /** 某应用下实际调用过的服务名集合（拓扑按应用过滤时圈定服务范围） */
    List<String> selectServiceNamesByApp(@Param("appId") String appId);

    /** 筛选项字典：应用列表（app_id + app_name），供「全部应用」下拉 */
    List<FilterOption> selectDistinctApps();

    List<String> selectDistinctApis();

    List<String> selectDistinctReqFormats();

    List<String> selectDistinctResFormats();
}
