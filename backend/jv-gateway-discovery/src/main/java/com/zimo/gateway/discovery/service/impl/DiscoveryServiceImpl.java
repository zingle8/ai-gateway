package com.zimo.gateway.discovery.service.impl;

import com.zimo.gateway.client.common.dto.ServiceInstanceDTO;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.discovery.entity.GwService;
import com.zimo.gateway.discovery.mapper.GwServiceMapper;
import com.zimo.gateway.discovery.model.ServiceQuery;
import com.zimo.gateway.discovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务发现服务实现
 */
@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {

    /** 未指定区间时的默认统计窗口（秒）：近 5 分钟 */
    private static final int DEFAULT_WINDOW_SECONDS = 300;

    private final GwServiceMapper serviceMapper;

    @Override
    public GwService getById(Long id) {
        return id == null ? null : serviceMapper.selectById(id);
    }

    @Override
    public Page<GwService> pageServices(ServiceQuery query) {
        long total = serviceMapper.countPage(query);
        long current = Math.max(1, query.getCurrent());
        long size = Math.max(1, query.getSize());
        List<GwService> records = total == 0 ? List.of() : serviceMapper.selectPage(query);
        int window = query.getWindowSeconds() > 0 ? query.getWindowSeconds() : DEFAULT_WINDOW_SECONDS;
        fillRuntimeStats(records, window);
        return Page.of(records, total, current, size);
    }

    /**
     * 用 tb_gw_trace_log 的真实调用记录覆盖注册时写入的 qps / avgRt / 流量。
     *
     * <p>tb_gw_service 里这几个字段只在注册瞬间写过一次，之后没人更新；
     * 这里按服务名聚合：QPS 恒取近 60 秒（瞬时速率语义），耗时与流量按页面所选区间统计。
     *
     * @param windowSeconds 平均耗时 / 流量的聚合窗口（秒），由统一时间区间换算
     */
    private void fillRuntimeStats(List<GwService> services, int windowSeconds) {
        if (services == null || services.isEmpty()) {
            return;
        }
        Map<String, double[]> stats = new HashMap<>();
        for (Map<String, Object> row : serviceMapper.selectRuntimeStats(windowSeconds)) {
            Object name = row.get("service_name");
            if (name == null) {
                continue;
            }
            stats.put(String.valueOf(name), new double[]{
                    number(row.get("qps")), number(row.get("avg_cost")),
                    number(row.get("in_bytes")), number(row.get("out_bytes"))});
        }

        for (GwService service : services) {
            double[] stat = stats.get(service.getServiceName());
            if (stat != null) {
                service.setQps((int) stat[0]);
                service.setAvgRt((int) Math.round(stat[1]));
                service.setInFlow(humanBytes(stat[2]));
                service.setOutFlow(humanBytes(stat[3]));
            } else {
                service.setQps(0);
                service.setAvgRt(0);
                service.setInFlow("0 B");
                service.setOutFlow("0 B");
            }
        }
    }

    private static double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0d;
    }

    /** 字节数转可读文本 */
    private String humanBytes(double bytes) {
        if (bytes < 1024) {
            return (long) bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024);
        }
        return String.format("%.1f MB", bytes / 1024 / 1024);
    }

    @Override
    public Map<String, Object> filterOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("apps", serviceMapper.selectDistinctApps());
        options.put("services", serviceMapper.selectDistinctServices());
        options.put("languages", serviceMapper.selectDistinctLanguages());
        return options;
    }

    @Override
    public GwService getDetail(Long id) {
        GwService service = serviceMapper.selectById(id);
        if (service == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "服务不存在：" + id);
        }
        return service;
    }

    @Override
    public void register(ServiceInstanceDTO instance) {
        if (instance == null || !StringUtils.hasText(instance.getServiceName())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "服务名不能为空");
        }
        GwService service = serviceMapper.selectByServiceName(instance.getServiceName());
        LocalDateTime now = LocalDateTime.now();
        if (service == null) {
            service = new GwService();
            service.setAppId(instance.getAppId());
            service.setAppName(instance.getAppName());
            service.setServiceName(instance.getServiceName());
            service.setIp(instance.getIp());
            service.setPort(instance.getPort());
            service.setLanguage(instance.getLanguage());
            service.setVersion(instance.getVersion());
            service.setQps(0);
            service.setAvgRt(0);
            service.setStatus("健康");
            service.setInFlow("0 B");
            service.setOutFlow("0 B");
            service.setInstances(1);
            service.setRegisterTime(instance.getRegisterTime() == null ? now : instance.getRegisterTime());
            service.setLastHeartbeatTime(now);
            service.setCreateTime(now);
            service.setUpdateTime(now);
            service.setDeleted(0);
            serviceMapper.insert(service);
            return;
        }

        service.setAppId(instance.getAppId());
        service.setAppName(instance.getAppName());
        service.setIp(instance.getIp());
        service.setPort(instance.getPort());
        service.setLanguage(instance.getLanguage());
        service.setVersion(instance.getVersion());
        service.setLastHeartbeatTime(now);
        service.setUpdateTime(now);
        // 服务重启后能重新上线：心跳恢复即视为健康，覆盖上一次的「异常」/「离线」
        service.setStatus("健康");
        serviceMapper.update(service);
    }

    @Override
    public void heartbeat(ServiceInstanceDTO instance) {
        // 心跳沿用注册逻辑：刷新心跳时间与实例信息，保证服务列表中能看到活跃实例
        register(instance);
    }

    @Override
    public void deregister(ServiceInstanceDTO instance) {
        if (instance == null || !StringUtils.hasText(instance.getServiceName())) {
            return;
        }
        serviceMapper.markAbnormalByName(instance.getServiceName(), LocalDateTime.now());
    }
}
