package com.zimo.gateway.proxy.mapper;

import com.zimo.gateway.proxy.ws.WsFrameRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** WebSocket 帧明细采集写入（tb_gw_ws_frame） */
@Mapper
public interface WsFrameMapper {

    @Insert("INSERT INTO tb_gw_ws_frame(session_key, trace_id, direction, msg_type,"
            + " payload, length, recv_time)"
            + " VALUES(#{f.sessionKey}, #{f.traceId}, #{f.direction}, #{f.msgType},"
            + " #{f.payload}, #{f.length}, #{f.recvTime})")
    int insert(@Param("f") WsFrameRecord record);
}
