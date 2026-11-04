package com.zimo.gateway.discovery.controller;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.R;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.discovery.entity.GwRoute;
import com.zimo.gateway.discovery.mapper.GwRouteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 转发路由管理。
 *
 * <p>挂在 /discovery 下复用已有的服务端代理规则；
 * 改动后 jv-gateway-proxy 最多 30 秒内自动生效，无需重启。
 */
@RestController
@RequestMapping("/discovery")
@RequiredArgsConstructor
public class RouteController {

    private final GwRouteMapper routeMapper;

    @GetMapping("/routes")
    public R<List<GwRoute>> list(@RequestParam(required = false) String keyword) {
        return R.ok(routeMapper.selectList(StringUtils.hasText(keyword) ? keyword.trim() : null));
    }

    @PostMapping("/routes")
    public R<GwRoute> create(@RequestBody GwRoute route) {
        validate(route, null);
        route.setId(null);
        route.setPathPrefix(route.getPathPrefix().trim());
        route.setUpstreamUrl(route.getUpstreamUrl().trim());
        // app_id 列 NOT NULL：控制台快速新增只填路径和目标时兜底
        if (!StringUtils.hasText(route.getAppId())) {
            route.setAppId("default");
            route.setAppName("默认应用");
        }
        if (route.getStatus() == null) {
            route.setStatus(1);
        }
        if (route.getStripPrefix() == null) {
            route.setStripPrefix(1);
        }
        LocalDateTime now = LocalDateTime.now();
        route.setCreateTime(now);
        route.setUpdateTime(now);
        route.setDeleted(0);
        routeMapper.insert(route);
        return R.ok(route);
    }

    @PutMapping("/routes/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody GwRoute route) {
        GwRoute exist = routeMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "路由不存在");
        }
        validate(route, id);
        route.setId(id);
        route.setPathPrefix(route.getPathPrefix().trim());
        route.setUpstreamUrl(route.getUpstreamUrl().trim());
        route.setUpdateTime(LocalDateTime.now());
        routeMapper.update(route);
        return R.ok();
    }

    @DeleteMapping("/routes/{id}")
    public R<Void> delete(@PathVariable Long id) {
        routeMapper.deleteById(id, LocalDateTime.now());
        return R.ok();
    }

    /** 启用 / 停用，供列表页开关直接调用 */
    @PostMapping("/routes/{id}/toggle")
    public R<Void> toggle(@PathVariable Long id) {
        GwRoute exist = routeMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "路由不存在");
        }
        GwRoute update = new GwRoute();
        update.setId(id);
        update.setStatus(exist.getStatus() != null && exist.getStatus() == 1 ? 0 : 1);
        routeMapper.update(update);
        return R.ok();
    }

    private void validate(GwRoute route, Long selfId) {
        if (route == null || !StringUtils.hasText(route.getPathPrefix())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "匹配路径不能为空");
        }
        String path = route.getPathPrefix().trim();
        if (!path.startsWith("/")) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "匹配路径必须以 / 开头，如 /order/**");
        }
        if (!StringUtils.hasText(route.getUpstreamUrl())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "转发目标不能为空");
        }
        String upstream = route.getUpstreamUrl().trim();
        if (!upstream.startsWith("http://") && !upstream.startsWith("https://")) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "转发目标必须以 http:// 或 https:// 开头");
        }
        if (routeMapper.countByPathPrefix(path, selfId) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该匹配路径已存在，请换一个");
        }
    }
}
