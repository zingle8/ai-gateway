package com.zimo.gateway.proxy.ws;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket 帧明细采集记录（tb_gw_ws_frame）。
 *
 * <p>每条消息一行：方向、类型、payload 样本（文本帧 UTF-8 / 二进制帧 hex，前 N 字节）、
 * 原始长度、接收时间。通过 session_key 关联会话记录（tb_gw_ws_session）。
 */
@Data
@Builder
public class WsFrameRecord {

    /** 会话唯一键（tb_gw_ws_session.session_key） */
    private String sessionKey;

    private String traceId;

    /** 方向：IN=客户端→网关→下游，OUT=下游→网关→客户端 */
    private String direction;

    /** 帧类型：TEXT / BINARY */
    private String msgType;

    /** payload 样本（前 N 字节，超长截断标记） */
    private String payload;

    /** 原始 payload 字节数 */
    private Integer length;

    private LocalDateTime recvTime;
}
