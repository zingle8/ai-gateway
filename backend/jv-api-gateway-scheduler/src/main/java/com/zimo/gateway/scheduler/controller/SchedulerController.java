package com.zimo.gateway.scheduler.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.scheduler.entity.SchedulerJobEntity;
import com.zimo.gateway.scheduler.entity.SchedulerPlanEntity;
import com.zimo.gateway.scheduler.entity.SchedulerRunLogEntity;
import com.zimo.gateway.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 调度计划对外接口（控制台「系统管理 → 调度计划」页数据支撑）
 *
 * 统一返回平台标准响应体 R（code=200 成功），与前端 @zimo/shared request 拦截器对齐。
 */
@RestController
@RequestMapping("/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchedulerService service;

    /** 任务列表（含状态与执行时间） */
    @GetMapping("/jobs")
    public R<List<SchedulerJobEntity>> jobs() {
        return R.ok(service.listJobs());
    }

    /** 执行计划列表（分区预建计划等） */
    @GetMapping("/jobs/{jobKey}/plans")
    public R<List<SchedulerPlanEntity>> plans(@PathVariable String jobKey,
                                             @RequestParam(defaultValue = "50") int limit) {
        return R.ok(service.listPlans(jobKey, limit));
    }

    /** 执行记录列表 */
    @GetMapping("/jobs/{jobKey}/logs")
    public R<List<SchedulerRunLogEntity>> logs(@PathVariable String jobKey,
                                               @RequestParam(defaultValue = "50") int limit) {
        return R.ok(service.listRunLogs(jobKey, limit));
    }

    /** 手动触发任务 */
    @PostMapping("/jobs/{jobKey}/run")
    public R<SchedulerRunLogEntity> run(@PathVariable String jobKey) {
        if (!SchedulerService.isKnownJob(jobKey)) {
            return R.fail("未知任务: " + jobKey);
        }
        return R.ok("data-clean".equals(jobKey) ? service.runClean("MANUAL") : service.runPartition("MANUAL"));
    }

    /** 读取调度时间配置 */
    @GetMapping("/config")
    public R<Map<String, String>> config() {
        return R.ok(Map.of("runTime", service.getCleanRunTime()));
    }

    /** 修改调度时间（MySQL + Redis 双写） */
    @PutMapping("/config")
    public R<Void> updateConfig(@RequestBody Map<String, String> body) {
        service.updateCleanRunTime(body.get("runTime"));
        return R.ok();
    }
}
