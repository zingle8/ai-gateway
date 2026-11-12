package com.zimo.gateway.discovery.mapper;

import com.zimo.gateway.discovery.entity.HostMetricsEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface HostMetricsMapper {

    /** 批量写入（单批上限由服务层控制，避免超大 SQL） */
    @Insert("""
            <script>
            INSERT INTO tb_gw_host_metrics
              (hostname, app_id, service_name, os_name, os_version, cpu_cores, cpu_usage, load_avg,
               mem_total, mem_used_pct, proc_rss, jvm_heap_pct, gc_count, gc_time_ms, thread_count, fd_count,
               disk_total, disk_used_pct, disk_read_bps, disk_write_bps, disk_iops,
               net_rx_bps, net_tx_bps, net_conn, gpu_count, gpu_util, gpu_mem_pct, report_time)
            VALUES
            <foreach collection="list" item="m" separator=",">
              (#{m.hostname}, #{m.appId}, #{m.serviceName}, #{m.osName}, #{m.osVersion},
               #{m.cpuCores}, #{m.cpuUsage}, #{m.loadAvg},
               #{m.memTotal}, #{m.memUsedPct}, #{m.procRss}, #{m.jvmHeapPct}, #{m.gcCount}, #{m.gcTimeMs},
               #{m.threadCount}, #{m.fdCount},
               #{m.diskTotal}, #{m.diskUsedPct}, #{m.diskReadBps}, #{m.diskWriteBps}, #{m.diskIops},
               #{m.netRxBps}, #{m.netTxBps}, #{m.netConn},
               #{m.gpuCount}, #{m.gpuUtil}, #{m.gpuMemPct}, #{m.reportTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<HostMetricsEntity> list);

    /**
     * 每台主机窗口内最新一条 + 窗口聚合（用于「主机资源」概览）：
     * 聚合值取平均，瞬时值（磁盘 / 内存总量）取最新一条。
     */
    @Select("""
            SELECT m.hostname                                   AS hostname,
                   MAX(m.app_id)                                AS appId,
                   MAX(m.service_name)                          AS serviceName,
                   MAX(m.os_name)                               AS osName,
                   MAX(m.os_version)                            AS osVersion,
                   MAX(m.cpu_cores)                             AS cpuCores,
                   ROUND(AVG(m.cpu_usage), 2)                   AS cpuAvg,
                   ROUND(MAX(m.cpu_usage), 2)                   AS cpuMax,
                   ROUND(AVG(m.mem_used_pct), 2)                AS memAvg,
                   ROUND(AVG(m.jvm_heap_pct), 2)                AS heapAvg,
                   MAX(m.mem_total)                             AS memTotal,
                   MAX(m.disk_total)                            AS diskTotal,
                   ROUND(AVG(m.disk_used_pct), 2)               AS diskUsedPct,
                   ROUND(AVG(m.net_rx_bps), 0)                  AS netRxAvg,
                   ROUND(AVG(m.net_tx_bps), 0)                  AS netTxAvg,
                   MAX(m.gpu_count)                             AS gpuCount,
                   ROUND(AVG(m.gpu_util), 2)                    AS gpuUtil,
                   MAX(m.report_time)                           AS lastReportTime,
                   COUNT(*)                                     AS samples
            FROM tb_gw_host_metrics m
            WHERE m.report_time >= DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
            GROUP BY m.hostname
            ORDER BY m.hostname
            """)
    List<Map<String, Object>> selectHostOverview(@Param("minutes") int minutes);

    /** 单主机时间序列（原始点，Service 层按桶归并） */
    @Select("""
            SELECT report_time    AS reportTime,
                   cpu_usage      AS cpuUsage,
                   mem_used_pct   AS memUsedPct,
                   jvm_heap_pct   AS jvmHeapPct,
                   disk_used_pct  AS diskUsedPct,
                   net_rx_bps     AS netRxBps,
                   net_tx_bps     AS netTxBps
            FROM tb_gw_host_metrics
            WHERE hostname = #{hostname}
              AND report_time >= DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
            ORDER BY report_time
            """)
    List<Map<String, Object>> selectTrend(@Param("hostname") String hostname, @Param("minutes") int minutes);

    /** 最近一次有上报的主机名列表（供趋势页下拉） */
    @Select("""
            SELECT DISTINCT hostname FROM tb_gw_host_metrics
            WHERE report_time >= DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
            ORDER BY hostname
            """)
    List<String> selectHostnames(@Param("minutes") int minutes);
}
