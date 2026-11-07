package com.zimo.gateway.scheduler.controller;

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
 */
@RestController
@RequestMapping("/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchedulerService service;

    /** 任务列表（含状态与执行时间） */
    @GetMapping("/jobs")
    public Map<String, Object> jobs() {
        List<SchedulerJobEntity> data = service.listJobs();
        return Map.of("code", 0, "data", data);
    }

    /** 执行计划列表（分区预建计划等） */
    @GetMapping("/jobs/{jobKey}/plans")
    public Map<String, Object> plans(@PathVariable String jobKey,
                                     @RequestParam(defaultValue = "50") int limit) {
        List<SchedulerPlanEntity> data = service.listPlans(jobKey, limit);
        return Map.of("code", 0, "data", data);
    }

    /** 执行记录列表 */
    @GetMapping("/jobs/{jobKey}/logs")
    public Map<String, Object> logs(@PathVariable String jobKey,
                                    @RequestParam(defaultValue = "50") int limit) {
        List<SchedulerRunLogEntity> data = service.listRunLogs(jobKey, limit);
        return Map.of("code", 0, "data", data);
    }

    /** 手动触发任务 */
    @PostMapping("/jobs/{jobKey}/run")
    public Map<String, Object> run(@PathVariable String jobKey) {
        if (!"data-clean".equals(jobKey) && !"partition-daily".equals(jobKey)) {
            return Map.of("code", 404, "message", "未知任务: " + jobKey);
        }
        Object log = "data-clean".equals(jobKey) ? service.runClean("MANUAL") : service.runPartition("MANUAL");
        return Map.of("code", 0, "data", log, "message", "任务已触发");
    }

    /** 读取调度时间配置 */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("code", 0, "data", Map.of("runTime", service.getCleanRunTime()));
    }

    /** 修改调度时间（MySQL + Redis 双写） */
    @PutMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody Map<String, String> body) {
        service.updateCleanRunTime(body.get("runTime"));
        return Map.of("code", 0, "message", "调度时间已更新并双写 Redis/MySQL");
    }
}
