package com.zimo.gateway.discovery.controller;

import com.zimo.gateway.client.common.dto.ServiceInstanceDTO;
import com.zimo.gateway.common.result.PageResult;
import com.zimo.gateway.common.util.TimeRange;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.discovery.entity.GwServiceEntity;
import com.zimo.gateway.discovery.model.ServiceQuery;
import com.zimo.gateway.discovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 服务发现接口
 */
@RestController
@RequestMapping("/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    /**
     * 服务分页列表
     *
     * @param range 运行指标统计区间：1m / 5m / 10m / 30m / 1h / 2h / 24h / 1d / 7d / 30d，
     *              决定平均耗时与流量的聚合窗口（默认 24h，与其它页面一致）
     */
    @GetMapping("/services")
    public R<PageResult<GwServiceEntity>> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String appId,
                                         @RequestParam(required = false) String serviceName,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String language,
                                         @RequestParam(defaultValue = "24h") String range,
                                         @RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size) {
        ServiceQuery query = new ServiceQuery();
        query.setKeyword(keyword);
        query.setAppId(appId);
        query.setServiceName(serviceName);
        query.setStatus(status);
        query.setLanguage(language);
        query.setWindowSeconds((int) Math.min(Integer.MAX_VALUE, TimeRange.of(range).getWindowSeconds()));
        query.setCurrent(current);
        query.setSize(size);
        return R.ok(PageResult.of(discoveryService.pageServices(query)));
    }

    /**
     * 调用趋势：区间内请求量与 QPS 两条序列，支持按应用 / 服务过滤（默认全局）。
     * 与服务列表的区间选择器共用同一 range，口径一致。
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(@RequestParam(defaultValue = "24h") String range,
                                        @RequestParam(required = false) String appId,
                                        @RequestParam(required = false) String serviceName) {
        return R.ok(discoveryService.trend(range, appId, serviceName));
    }

    /**
     * 筛选项字典：应用 / 服务 / 语言类型（全部取自库中真实出现过的值）。
     * 注：必须声明在 {@code /services/{id}} 之前语义等价——Spring 按字面量优先匹配，不会被 {id} 吞掉。
     */
    @GetMapping("/filters")
    public R<Map<String, Object>> filters() {
        return R.ok(discoveryService.filterOptions());
    }

    /**
     * 服务详情
     */
    @GetMapping("/services/{id}")
    public R<GwServiceEntity> detail(@PathVariable Long id) {
        return R.ok(discoveryService.getDetail(id));
    }

    /**
     * 服务实例注册 / 更新（自动接入）
     */
    @PostMapping("/services/register")
    public R<Void> register(@RequestBody ServiceInstanceDTO instance) {
        discoveryService.register(instance);
        return R.ok();
    }

    /**
     * 服务心跳
     */
    @PostMapping("/services/heartbeat")
    public R<Void> heartbeat(@RequestBody ServiceInstanceDTO instance) {
        discoveryService.heartbeat(instance);
        return R.ok();
    }

    /**
     * 服务实例下线
     */
    @DeleteMapping("/services/deregister")
    public R<Void> deregister(@RequestBody ServiceInstanceDTO instance) {
        discoveryService.deregister(instance);
        return R.ok();
    }
}
