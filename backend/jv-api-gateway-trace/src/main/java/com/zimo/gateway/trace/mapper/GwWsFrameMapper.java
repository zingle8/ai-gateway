package com.zimo.gateway.trace.mapper;

import com.zimo.gateway.trace.entity.GwWsFrameEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** WebSocket 帧明细查询（tb_gw_ws_frame，proxy 写入、trace 只读） */
@Mapper
public interface GwWsFrameMapper {

    List<GwWsFrameEntity> listBySessionKey(@Param("sessionKey") String sessionKey);
}
