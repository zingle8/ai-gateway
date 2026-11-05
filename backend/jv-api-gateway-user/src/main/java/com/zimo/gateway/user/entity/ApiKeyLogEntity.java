package com.zimo.gateway.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 操作审计（表 tb_gw_api_key_log）
 */
@Data
public class ApiKeyLogEntity {

    private Long id;

    private String keyId;

    private String keyName;

    /** CREATE / ENABLE / DISABLE / ROTATE / DELETE */
    private String action;

    private Long operatorUserId;

    private String operatorName;

    private String remark;

    private LocalDateTime createTime;
}
