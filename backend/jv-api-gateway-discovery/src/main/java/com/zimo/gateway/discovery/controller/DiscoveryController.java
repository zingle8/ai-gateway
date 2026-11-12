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
                                         @RequestParam(required = false) String serviceId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String language,
                                         @RequestParam(defaultValue = "24h") String range,
                                         @RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size) {
        ServiceQuery query = new ServiceQuery();
        query.setKeyword(keyword);
        query.setAppId(appId);
        query.setServiceId(serviceId);
        query.setStatus(status);
        query.setLanguage(language);
        query.setWindowSeconds((int) Math.min(Integer.MAX_VALUE, TimeRange.of(range).getWindowSeconds()));
        query.setCurrent(current);
        query.setSize(size);
        return R.ok(PageResult.of(discoveryService.pageServices(query)));
    }

    /**
     * 导出当前筛选结果（CSV，最多 10000 行；带操作人与导出时间列）
     */
    @GetMapping("/services/export")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "24h") String range,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        ServiceQuery query = new ServiceQuery();
        query.setKeyword(keyword);
        query.setAppId(appId);
        query.setServiceId(serviceId);
        query.setStatus(status);
        query.setLanguage(language);
        query.setWindowSeconds((int) Math.min(Integer.MAX_VALUE, TimeRange.of(range).getWindowSeconds()));
        query.setCurrent(1);
        query.setSize(10000);
        var result = discoveryService.pageServices(query);
        // 导出留痕：优先取控制台传入的操作人（网关鉴权后前端携带），否则回落上下文
        String operator = httpRequest.getHeader("X-Username");
        if (operator == null || operator.isBlank()) {
            operator = com.zimo.gateway.common.context.UserContext.getUsername();
        }
        if (operator == null || operator.isBlank()) {
            Long uid = com.zimo.gateway.common.context.UserContext.getUserId();
            operator = uid == null ? "system" : "uid:" + uid;
        }
        String exportTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder csv = new StringBuilder("服务名,应用,实例IP,端口,语言,版本,状态,QPS,平均耗时(ms),调用量,接口数,实例数,注册时间,操作人,导出时间\n");
        for (GwServiceEntity e : result.getRecords()) {
            csv.append(csvCell(e.getServiceName())).append(',')
                    .append(csvCell(e.getAppName())).append(',')
                    .append(csvCell(e.getIp())).append(',')
                    .append(csvCell(e.getPort())).append(',')
                    .append(csvCell(e.getLanguage())).append(',')
                    .append(csvCell(e.getVersion())).append(',')
                    .append(csvCell(e.getStatus())).append(',')
                    .append(csvCell(e.getQps())).append(',')
                    .append(csvCell(e.getAvgRt())).append(',')
                    .append(csvCell(e.getCalls())).append(',')
                    .append(csvCell(e.getApiCount())).append(',')
                    .append(csvCell(e.getInstances())).append(',')
                    .append(csvCell(e.getRegisterTime())).append(',')
                    .append(csvCell(operator)).append(',')
                    .append(csvCell(exportTime)).append('\n');
        }
        byte[] bytes = ("\ufeff" + csv).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"services.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    private static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return text.contains(",") || text.contains("\"") || text.contains("\n")
                ? "\"" + text + "\"" : text;
    }

    /**
     * 调用趋势：区间内请求量与 QPS 两条序列，支持按应用 / 服务过滤（默认全局）。
     * 与服务列表的区间选择器共用同一 range，口径一致。
     */
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(@RequestParam(defaultValue = "24h") String range,
                                        @RequestParam(required = false) String appId,
                                        @RequestParam(required = false) String serviceId) {
        return R.ok(discoveryService.trend(range, appId, serviceId));
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
