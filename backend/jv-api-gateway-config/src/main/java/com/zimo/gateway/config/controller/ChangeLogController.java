package com.zimo.gateway.config.controller;

import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.config.entity.ChangeLogEntity;
import com.zimo.gateway.config.service.ChangeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 变更审计查询（v0.2 第 8 项）：控制台「系统设置」与「路由配置」页的「变更记录」数据源。
 *
 * <p>路由变更由 discovery 服务写入同一张表（tb_gw_change_log），页面统一从本接口读。
 */
@RestController
@RequestMapping("/change-logs")
@RequiredArgsConstructor
public class ChangeLogController {

    private final ChangeLogService changeLogService;

    /**
     * @param bizType CONFIG / POLICY / ROUTE，空表示全部
     * @param bizKey  配置键 / 策略键 / 路由前缀，空表示全部
     */
    @GetMapping
    public R<Page<ChangeLogEntity>> page(@RequestParam(required = false) String bizType,
                                         @RequestParam(required = false) String bizKey,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(changeLogService.page(bizType, bizKey, page, size));
    }
}
