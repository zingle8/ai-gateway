package com.zimo.gateway.trace.model;

import lombok.Data;

/**
 * WebSocket 会话分页查询条件
 */
@Data
public class WsSessionQuery {

    private String traceId;

    /** 握手路径模糊匹配 */
    private String path;

    private String closeStatus;

    /** 开始时间下界（yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    private long current = 1;

    private long size = 10;
}
