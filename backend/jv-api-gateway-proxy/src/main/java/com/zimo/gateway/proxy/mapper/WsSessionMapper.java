package com.zimo.gateway.proxy.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** WebSocket 会话采集写入（tb_gw_ws_session） */
@Mapper
public interface WsSessionMapper {

    @Insert("INSERT INTO tb_gw_ws_session(trace_id, request_path, upstream_url, client_ip,"
            + " conn_start, conn_end, duration_ms, in_msg_count, in_bytes, out_msg_count, out_bytes,"
            + " in_sample, out_sample, close_status, create_time)"
            + " VALUES(#{r.traceId}, #{r.requestPath}, #{r.upstreamUrl}, #{r.clientIp},"
            + " #{r.connStart}, #{r.connEnd}, #{r.durationMs}, #{r.inMsgCount}, #{r.inBytes},"
            + " #{r.outMsgCount}, #{r.outBytes}, #{r.inSample}, #{r.outSample}, #{r.closeStatus}, NOW())")
    int insert(@org.apache.ibatis.annotations.Param("r") com.zimo.gateway.proxy.ws.WsSessionRecord record);
}
