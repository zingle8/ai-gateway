package com.zimo.gateway.trace.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket 会话采集（表 tb_gw_ws_session，由 proxy 旁路捕获写入）
 */
@Data
public class GwWsSessionEntity {

    private Long id;

    /** 会话唯一键（帧明细关联） */
    private String sessionKey;

    private String traceId;

    /** 客户端握手请求地址 */
    private String requestPath;

    /** 转发上游地址（ws://） */
    private String upstreamUrl;

    private String clientIp;

    private LocalDateTime connStart;

    private LocalDateTime connEnd;

    private Long durationMs;

    private Integer inMsgCount;

    private Long inBytes;

    private Integer outMsgCount;

    private Long outBytes;

    private String inSample;

    private String outSample;

    /** 关闭状态：NORMAL / ABNORMAL(信号) */
    private String closeStatus;

    private LocalDateTime createTime;
}
