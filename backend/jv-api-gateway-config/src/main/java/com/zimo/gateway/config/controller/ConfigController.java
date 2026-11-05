package com.zimo.gateway.config.controller;

import com.zimo.gateway.common.result.R;
import com.zimo.gateway.config.model.ConfigUpdateRequest;
import com.zimo.gateway.config.model.ConfigVO;
import com.zimo.gateway.config.service.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统配置接口
 *
 * <p>/configs/public 免鉴权：菜单健康标识、鉴权缓存模式等开关由前端/其他模块轮询；
 * 其余接口需登录（JWT）。
 */
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public R<List<ConfigVO>> list() {
        return R.ok(configService.listAll());
    }

    @GetMapping("/public")
    public R<Map<String, String>> publicFlags() {
        return R.ok(configService.publicFlags());
    }

    @PutMapping("/{key}")
    public R<Void> update(@PathVariable String key, @Valid @RequestBody ConfigUpdateRequest request) {
        configService.update(key, request.value());
        return R.ok();
    }
}
