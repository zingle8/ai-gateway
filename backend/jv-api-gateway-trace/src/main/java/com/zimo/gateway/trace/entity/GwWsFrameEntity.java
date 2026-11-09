package com.zimo.gateway.trace.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket 帧明细采集（表 tb_gw_ws_frame，由 proxy 旁路捕获写入）
 */
@Data
public class GwWsFrameEntity {

    private Long id;

    /** 会话唯一键（tb_gw_ws_session.session_key） */
    private String sessionKey;

    private String traceId;

    /** 方向：IN=客户端→下游，OUT=下游→客户端 */
    private String direction;

    /** 帧类型：TEXT / BINARY */
    private String msgType;

    /** payload 样本（文本 UTF-8 / 二进制 hex，前 N 字节） */
    private String payload;

    /** 原始字节数 */
    private Integer length;

    private LocalDateTime recvTime;
}
