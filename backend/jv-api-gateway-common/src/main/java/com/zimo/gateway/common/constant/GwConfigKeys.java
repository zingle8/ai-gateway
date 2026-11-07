package com.zimo.gateway.common.constant;

/**
 * 平台级配置键（tb_sys_config / Nacos 公共配置）约定。
 *
 * 跨模块共享的配置键统一在此登记，键名修改需全局检索同步。
 */
public final class GwConfigKeys {

    /** 历史数据清理任务每日执行时间（HH:mm），系统设置页读写，调度中心与设置模块共用 */
    public static final String SCHEDULER_CLEAN_RUN_TIME = "scheduler.clean.run-time";

    /** 平台自身接入上报共享密钥（X-Gateway-Client-Token）环境变量名 */
    public static final String ENV_GATEWAY_CLIENT_TOKEN = "GATEWAY_CLIENT_TOKEN";

    /** JWT 签名密钥环境变量名 */
    public static final String ENV_GATEWAY_JWT_SECRET = "GATEWAY_JWT_SECRET";

    private GwConfigKeys() {
        throw new UnsupportedOperationException("常量类禁止实例化");
    }
}
