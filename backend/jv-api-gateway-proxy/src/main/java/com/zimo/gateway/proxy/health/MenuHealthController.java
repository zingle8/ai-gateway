package com.zimo.gateway.proxy.health;

import com.zimo.gateway.proxy.auth.AuthCacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 侧边栏菜单状态数据（免鉴权，已在 gateway.proxy.auth.skip-paths 放行）。
 *
 * <p>返回各后端服务当前状态 + 鉴权缓存实际生效模式，前端 30 秒轮询一次。
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class MenuHealthController {

    private final MenuHealthService healthService;
    private final AuthCacheStore cacheStore;

    @GetMapping("/menu-services")
    public Mono<Map<String, Object>> menuServices() {
        return Mono.fromCallable(() -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("services", healthService.snapshot());
                    body.put("authCacheMode", cacheStore.resolvedMode());
                    return body;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 便捷探活（服务本身） */
    @GetMapping("/ping")
    public Mono<String> ping() {
        return Mono.just("ok");
    }
}
