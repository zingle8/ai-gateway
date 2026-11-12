package com.zimo.gateway.config.service;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.config.entity.RateLimitRuleEntity;
import com.zimo.gateway.config.mapper.RateLimitRuleMapper;
import com.zimo.gateway.config.model.RateLimitRuleSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 限流规则维护。
 *
 * <p>规则存 tb_gw_rate_limit，网关 proxy 每 5 秒加载一次启用规则并执行令牌桶，
 * 因此这里的改动最迟 5 秒生效，无需重启网关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final Set<String> DIMENSIONS = Set.of("GLOBAL", "ROUTE", "API_KEY", "IP");

    private final RateLimitRuleMapper mapper;

    public List<RateLimitRuleEntity> list() {
        return mapper.selectAll();
    }

    public RateLimitRuleEntity create(RateLimitRuleSaveRequest request) {
        RateLimitRuleEntity entity = toEntity(request);
        mapper.insert(entity);
        log.info("[限流] 新增规则 {}（{} {} → {} 次/秒）", entity.getName(), entity.getDimension(),
                entity.getTarget(), entity.getReplenishRate());
        return entity;
    }

    public RateLimitRuleEntity update(Long id, RateLimitRuleSaveRequest request) {
        requireExists(id);
        RateLimitRuleEntity entity = toEntity(request);
        entity.setId(id);
        mapper.update(entity);
        log.info("[限流] 修改规则 {}（{} {} → {} 次/秒）", entity.getName(), entity.getDimension(),
                entity.getTarget(), entity.getReplenishRate());
        return entity;
    }

    public void updateStatus(Long id, Integer status) {
        requireExists(id);
        int value = status != null && status == 1 ? 1 : 0;
        mapper.updateStatus(id, value);
        log.info("[限流] 规则 {} 状态改为 {}", id, value == 1 ? "启用" : "停用");
    }

    public void delete(Long id) {
        requireExists(id);
        mapper.delete(id);
        log.info("[限流] 删除规则 {}", id);
    }

    private void requireExists(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "限流规则不存在：" + id);
        }
    }

    private RateLimitRuleEntity toEntity(RateLimitRuleSaveRequest request) {
        String dimension = request.dimension().trim().toUpperCase();
        if (!DIMENSIONS.contains(dimension)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "不支持的限流维度：" + request.dimension());
        }
        String target = request.target() == null ? "" : request.target().trim();
        if ("ROUTE".equals(dimension)) {
            if (target.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "路由维度必须填写路径（如 /gw-demo/**）");
            }
            if (!target.startsWith("/")) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "路由路径需以 / 开头");
            }
        } else if (target.isEmpty()) {
            target = "*";
        }
        int requested = request.requestedTokens() == null ? 1 : request.requestedTokens();
        if (requested < 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "每次请求消耗令牌数至少为 1");
        }
        if (request.burstCapacity() < requested) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "桶容量不能小于每次请求消耗令牌数");
        }
        RateLimitRuleEntity entity = new RateLimitRuleEntity();
        entity.setName(request.name().trim());
        entity.setDimension(dimension);
        entity.setTarget(target);
        entity.setReplenishRate(request.replenishRate());
        entity.setBurstCapacity(request.burstCapacity());
        entity.setRequestedTokens(requested);
        entity.setStatus(request.status() == null ? 1 : (request.status() == 1 ? 1 : 0));
        entity.setRemark(request.remark());
        return entity;
    }
}
