package com.zimo.gateway.discovery.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.discovery.mapper.DeploymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署关联与跨主机调用统计（v0.2 第 11 项）。
 *
 * <ul>
 *   <li>{@code GET /discovery/deployments} —— 部署清单：主机 / pod → 服务实例 + 资源水位；</li>
 *   <li>{@code GET /discovery/cross-host} —— 跨主机调用：源 IP → 目标 IP 调用量 / 耗时 / 错误率；</li>
 *   <li>{@code GET /discovery/impact} —— 单点影响面：某主机故障时受影响的实例与服务、调用量占比。</li>
 * </ul>
 *
 * <p>主机名缺失时用 IP 兜底（`ip:port` 作为节点标签），不做猜测性归并。
 * 边界：不采集机柜 / 交换机等物理网络信息，也不做 3D 机房。
 */
@RestController
@RequestMapping("/discovery")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentMapper deploymentMapper;

    /** 部署清单：每台主机 / pod 上跑了哪些实例，带最新资源水位 */
    @GetMapping("/deployments")
    public R<List<Map<String, Object>>> deployments(@RequestParam(defaultValue = "60") int minutes) {
        int window = clamp(minutes);
        List<Map<String, Object>> instances = deploymentMapper.selectInstances();
        Map<String, Map<String, Object>> metricsByHost = new HashMap<>();
        for (Map<String, Object> metric : deploymentMapper.selectLatestMetrics(window)) {
            metricsByHost.put(String.valueOf(metric.get("hostname")), metric);
        }

        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> instance : instances) {
            String hostname = str(instance.get("hostname"));
            String ip = str(instance.get("ip"));
            String nodeKey = hostname != null && !hostname.isBlank() ? hostname
                    : (ip == null || ip.isBlank() ? "unknown" : ip);
            Map<String, Object> node = nodes.computeIfAbsent(nodeKey, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("node", key);
                created.put("ip", ip);
                created.put("hostname", hostname);
                created.put("instances", new ArrayList<Map<String, Object>>());
                Map<String, Object> metric = metricsByHost.get(key);
                created.put("cpuUsage", metric == null ? null : metric.get("cpuUsage"));
                created.put("memUsedPct", metric == null ? null : metric.get("memUsedPct"));
                created.put("diskUsedPct", metric == null ? null : metric.get("diskUsedPct"));
                created.put("lastReportTime", metric == null ? null : metric.get("reportTime"));
                return created;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) node.get("instances");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("serviceId", instance.get("serviceId"));
            item.put("serviceName", instance.get("serviceName"));
            item.put("appName", instance.get("appName"));
            item.put("ip", instance.get("ip"));
            item.put("port", instance.get("port"));
            item.put("status", instance.get("status"));
            item.put("qps", instance.get("qps"));
            item.put("avgRt", instance.get("avgRt"));
            item.put("lastHeartbeatTime", instance.get("lastHeartbeatTime"));
            list.add(item);
            if (node.get("ip") == null && ip != null) {
                node.put("ip", ip);
            }
        }
        return R.ok(new ArrayList<>(nodes.values()));
    }

    /** 跨主机调用统计（源 IP → 目标 IP），按调用量倒序 */
    @GetMapping("/cross-host")
    public R<List<Map<String, Object>>> crossHost(@RequestParam(defaultValue = "60") int minutes) {
        return R.ok(deploymentMapper.selectCrossHost(clamp(minutes)));
    }

    /**
     * 单点影响面：给定主机名 / IP，列出其上的实例、涉及服务，以及这些服务的调用量占全站比例。
     * 只做「部署 + 调用」两个真实维度的估算，不计算网络层依赖。
     */
    @GetMapping("/impact")
    public R<Map<String, Object>> impact(@RequestParam String hostname,
                                         @RequestParam(defaultValue = "60") int minutes) {
        int window = clamp(minutes);
        List<Map<String, Object>> instances = deploymentMapper.selectInstances().stream()
                .filter(row -> hostname.equals(str(row.get("hostname"))) || hostname.equals(str(row.get("ip"))))
                .toList();

        Map<String, Long> callsByService = new HashMap<>();
        long totalCalls = 0;
        for (Map<String, Object> row : deploymentMapper.selectCallsByService(window)) {
            long calls = row.get("calls") instanceof Number n ? n.longValue() : 0;
            callsByService.put(str(row.get("serviceId")), calls);
            totalCalls += calls;
        }

        List<Map<String, Object>> affected = new ArrayList<>();
        long affectedCalls = 0;
        for (Map<String, Object> instance : instances) {
            String serviceId = str(instance.get("serviceId"));
            long calls = callsByService.getOrDefault(serviceId, 0L);
            affectedCalls += calls;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("serviceId", serviceId);
            item.put("serviceName", instance.get("serviceName"));
            item.put("appName", instance.get("appName"));
            item.put("ip", instance.get("ip"));
            item.put("port", instance.get("port"));
            item.put("status", instance.get("status"));
            item.put("calls", calls);
            item.put("callsShare", totalCalls == 0 ? 0
                    : Math.round(calls * 10000.0 / totalCalls) / 100.0);
            affected.add(item);
        }
        affected.sort(Comparator.comparingLong((Map<String, Object> m) -> (long) m.get("calls")).reversed());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hostname", hostname);
        result.put("minutes", window);
        result.put("instanceCount", instances.size());
        result.put("serviceCount", affected.stream().map(m -> m.get("serviceId")).distinct().count());
        result.put("affectedCalls", affectedCalls);
        result.put("totalCalls", totalCalls);
        result.put("callsShare", totalCalls == 0 ? 0 : Math.round(affectedCalls * 10000.0 / totalCalls) / 100.0);
        result.put("instances", affected);
        return R.ok(result);
    }

    private int clamp(int minutes) {
        return Math.min(Math.max(minutes, 5), 10080);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
