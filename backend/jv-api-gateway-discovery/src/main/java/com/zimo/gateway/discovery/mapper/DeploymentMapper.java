package com.zimo.gateway.discovery.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 部署关联与跨主机调用统计（v0.2 第 11 项）。
 *
 * <p>三方关联：服务实例（tb_gw_service.ip / port / hostname）↔ 主机（tb_gw_host_metrics.hostname）
 * ↔ 调用记录（tb_gw_trace_log.req_ip / res_ip）。只做数据聚合，不臆造网络设备信息。
 */
@Mapper
public interface DeploymentMapper {

    /** 在册服务实例（含 hostname / ip / port / 状态 / 心跳），按主机名分组由 Service 层完成 */
    @Select("""
            SELECT app_id        AS appId,
                   app_name      AS appName,
                   service_id    AS serviceId,
                   service_name  AS serviceName,
                   ip            AS ip,
                   hostname      AS hostname,
                   port          AS port,
                   status        AS status,
                   qps           AS qps,
                   avg_rt        AS avgRt,
                   language      AS language,
                   version       AS version,
                   last_heartbeat_time AS lastHeartbeatTime
            FROM tb_gw_service
            WHERE deleted = 0
            ORDER BY hostname, service_id
            """)
    List<Map<String, Object>> selectInstances();

    /** 每台主机最新一条运行指标（用于部署清单的资源水位） */
    @Select("""
            SELECT m.hostname      AS hostname,
                   m.cpu_usage     AS cpuUsage,
                   m.mem_used_pct  AS memUsedPct,
                   m.disk_used_pct AS diskUsedPct,
                   m.report_time   AS reportTime
            FROM tb_gw_host_metrics m
            INNER JOIN (
                SELECT hostname, MAX(report_time) AS latest
                FROM tb_gw_host_metrics
                WHERE report_time >= DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
                GROUP BY hostname
            ) t ON t.hostname = m.hostname AND t.latest = m.report_time
            """)
    List<Map<String, Object>> selectLatestMetrics(@Param("minutes") int minutes);

    /**
     * 跨主机调用聚合：按「源 IP → 目标 IP」统计调用量 / 平均耗时 / 错误率。
     * 错误口径：status_code >= 500 或存在 err_type（网关侧错误）。
     */
    @Select("""
            SELECT req_ip                                        AS srcIp,
                   res_ip                                        AS dstIp,
                   (SELECT s.hostname FROM tb_gw_service s
                     WHERE s.ip = t.req_ip AND s.hostname IS NOT NULL LIMIT 1) AS srcHost,
                   (SELECT s.hostname FROM tb_gw_service s
                     WHERE s.ip = t.res_ip AND s.hostname IS NOT NULL LIMIT 1) AS dstHost,
                   COUNT(*)                                      AS calls,
                   ROUND(AVG(t.cost), 1)                         AS avgCost,
                   ROUND(SUM(CASE WHEN t.status_code >= 500 OR t.err_type IS NOT NULL THEN 1 ELSE 0 END)
                         * 100.0 / COUNT(*), 2)                  AS errorRate,
                   MAX(t.req_time)                               AS lastTime
            FROM tb_gw_trace_log t
            WHERE t.req_time >= DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
              AND t.req_ip IS NOT NULL AND t.req_ip <> ''
              AND t.res_ip IS NOT NULL AND t.res_ip <> ''
            GROUP BY t.req_ip, t.res_ip
            ORDER BY calls DESC
            LIMIT 200
            """)
    List<Map<String, Object>> selectCrossHost(@Param("minutes") int minutes);

    /** 某主机上实例承载的调用量（窗口内，按服务ID），用于影响面分析 */
    @Select("""
            SELECT service_name AS serviceId, COUNT(*) AS calls
            FROM tb_gw_trace_log
            WHERE req_time >= DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
            GROUP BY service_name
            """)
    List<Map<String, Object>> selectCallsByService(@Param("minutes") int minutes);
}
