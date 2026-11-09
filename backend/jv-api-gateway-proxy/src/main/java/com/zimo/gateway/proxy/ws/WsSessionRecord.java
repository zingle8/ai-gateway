package com.zimo.gateway.proxy.ws;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket 会话级采集记录（tb_gw_ws_session）。
 *
 * <p>连接维度：双向消息数与字节数、双向首帧样本（前 N 字节）、起止时间与关闭状态。
 * 帧明细不落库（量大），样本用于排查协议问题。
 */
@Data
@Builder
public class WsSessionRecord {

    /** 链路追踪 ID（来自网关 TraceGlobalFilter 注入的 X-Gateway-Trace-Id） */
    private String traceId;

    /** 客户端请求路径（握手 URL） */
    private String requestPath;

    /** 网关转发到的上游地址（ws://host:port） */
    private String upstreamUrl;

    private String clientIp;

    private LocalDateTime connStart;

    private LocalDateTime connEnd;

    private Long durationMs;

    /** 入站（客户端→网关→下游）消息数 */
    private Integer inMsgCount;

    /** 入站字节数（消息 payload） */
    private Long inBytes;

    /** 出站（下游→网关→客户端）消息数 */
    private Integer outMsgCount;

    /** 出站字节数 */
    private Long outBytes;

    /** 入站首帧样本（前 N 字节，超长截断） */
    private String inSample;

    /** 出站首帧样本（前 N 字节） */
    private String outSample;

    /** 关闭状态：NORMAL / ERROR(原因摘要) */
    private String closeStatus;
}
