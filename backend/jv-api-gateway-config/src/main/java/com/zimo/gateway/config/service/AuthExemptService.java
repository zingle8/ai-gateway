package com.zimo.gateway.config.service;

import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.config.entity.AuthExemptEntity;
import com.zimo.gateway.config.mapper.AuthExemptMapper;
import com.zimo.gateway.config.model.AuthExemptSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 鉴权 / 限流豁免规则维护（高危配置：放行面直接决定网关安全性）。
 *
 * <p>网关 proxy 5 秒内加载生效；本表规则为「追加」放行，yml 内置白名单不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthExemptService {

    private static final Set<String> TYPES = Set.of("LOGIN", "RATE", "ALL");

    private final AuthExemptMapper mapper;

    public List<AuthExemptEntity> list() {
        return mapper.selectAll();
    }

    public AuthExemptEntity create(AuthExemptSaveRequest request) {
        AuthExemptEntity entity = toEntity(request);
        mapper.insert(entity);
        log.warn("[豁免] 新增规则「{}」{} {}（{}）", entity.getName(), entity.getMethods(),
                entity.getPathPattern(), entity.getExemptType());
        return entity;
    }

    public AuthExemptEntity update(Long id, AuthExemptSaveRequest request) {
        requireExists(id);
        AuthExemptEntity entity = toEntity(request);
        entity.setId(id);
        mapper.update(entity);
        log.warn("[豁免] 修改规则「{}」{} {}（{}）", entity.getName(), entity.getMethods(),
                entity.getPathPattern(), entity.getExemptType());
        return entity;
    }

    public void updateStatus(Long id, Integer status) {
        requireExists(id);
        int value = status != null && status == 1 ? 1 : 0;
        mapper.updateStatus(id, value);
        log.warn("[豁免] 规则 {} 状态改为 {}", id, value == 1 ? "启用" : "停用");
    }

    public void delete(Long id) {
        requireExists(id);
        mapper.delete(id);
        log.warn("[豁免] 删除规则 {}", id);
    }

    private void requireExists(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "豁免规则不存在：" + id);
        }
    }

    private AuthExemptEntity toEntity(AuthExemptSaveRequest request) {
        String path = request.pathPattern().trim();
        if (!path.startsWith("/")) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "路径需以 / 开头");
        }
        String type = request.exemptType().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "豁免类型只能是 LOGIN / RATE / ALL");
        }
        LocalDateTime expireAt = null;
        if (request.expireAt() != null && !request.expireAt().isBlank()) {
            try {
                expireAt = LocalDateTime.parse(request.expireAt().trim());
            } catch (Exception e) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                        "到期时间格式应为 yyyy-MM-ddTHH:mm:ss（如 2026-12-31T23:59:59）");
            }
            if (expireAt.isBefore(LocalDateTime.now())) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "到期时间必须晚于当前时间");
            }
        }
        AuthExemptEntity entity = new AuthExemptEntity();
        entity.setName(request.name().trim());
        entity.setPathPattern(path);
        entity.setMethods(request.methods() == null || request.methods().isBlank()
                ? "*" : request.methods().trim().toUpperCase());
        entity.setExemptType(type);
        entity.setStatus(request.status() == null ? 1 : (request.status() == 1 ? 1 : 0));
        entity.setExpireAt(expireAt);
        entity.setRemark(request.remark());
        return entity;
    }
}
