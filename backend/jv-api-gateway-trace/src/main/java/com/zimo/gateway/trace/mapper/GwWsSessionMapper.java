package com.zimo.gateway.trace.mapper;

import com.zimo.gateway.trace.entity.GwWsSessionEntity;
import com.zimo.gateway.trace.model.WsSessionQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** WebSocket 会话采集查询（tb_gw_ws_session，proxy 写入、trace 只读） */
@Mapper
public interface GwWsSessionMapper {

    long countPage(WsSessionQuery query);

    List<GwWsSessionEntity> selectPage(WsSessionQuery query);
}
