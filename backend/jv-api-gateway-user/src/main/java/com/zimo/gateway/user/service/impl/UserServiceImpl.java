package com.zimo.gateway.user.service.impl;

import com.zimo.gateway.common.context.UserContext;
import com.zimo.gateway.common.exception.BizException;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.common.result.ResultCode;
import com.zimo.gateway.common.util.JwtUtil;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.mapper.UserMapper;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.model.RegisterRequest;
import com.zimo.gateway.user.model.UserQuery;
import com.zimo.gateway.user.model.UserSaveRequest;
import com.zimo.gateway.user.service.LoginPolicyService;
import com.zimo.gateway.user.service.SessionService;
import com.zimo.gateway.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final LoginPolicyService loginPolicyService;

    /**
     * Redis 模板（可选依赖）。
     * 未引入 Redis 或 Redis 不可用时为 null / 抛异常，此时自动降级为内存计数，
     * 保证登录功能不受中间件可用性影响。
     */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** 登录失败计数的降级存储（仅在 Redis 不可用时使用，单实例内存态） */
    private final Map<String, Integer> failCount = new ConcurrentHashMap<>();

    /** 连续失败达到该次数即锁定账号 */

    /** 失败计数在 Redis 中的存活时间：到期自动解锁 */

    private static final String FAIL_KEY_PREFIX = "gw:login:fail:";

    private String failKey(String username) {
        return FAIL_KEY_PREFIX + username;
    }

    /** 读取当前连续失败次数（Redis 异常时降级内存） */
    private int currentFailCount(String username) {
        if (redisTemplate != null) {
            try {
                String val = redisTemplate.opsForValue().get(failKey(username));
                return val == null ? 0 : Integer.parseInt(val);
            } catch (Exception e) {
                log.warn("读取登录失败计数失败（Redis 不可用），降级为内存计数", e);
            }
        }
        return failCount.getOrDefault(username, 0);
    }

    /** 累加一次失败并返回累计次数（首次写入时设置过期时间，到期自动解锁） */
    private int incrFailCount(String username) {
        if (redisTemplate != null) {
            try {
                Long n = redisTemplate.opsForValue().increment(failKey(username));
                if (n != null && n == 1L) {
                    redisTemplate.expire(failKey(username), Duration.ofMinutes(loginPolicyService.lockMinutes()));
                }
                return n == null ? 1 : n.intValue();
            } catch (Exception e) {
                log.warn("累加登录失败计数失败（Redis 不可用），降级为内存计数", e);
            }
        }
        return failCount.merge(username, 1, Integer::sum);
    }

    /** 清除失败计数（登录成功时调用） */
    private void clearFailCount(String username) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(failKey(username));
            } catch (Exception e) {
                log.warn("清除登录失败计数失败（Redis 不可用），仅清理内存计数", e);
            }
        }
        failCount.remove(username);
    }

    /** 锁定剩余秒数；内存降级场景返回 -1（无自动解锁时间） */
    private long lockRemainSeconds(String username) {
        if (redisTemplate != null) {
            try {
                Long ttl = redisTemplate.getExpire(failKey(username));
                return ttl == null ? -1 : Math.max(0, ttl);
            } catch (Exception e) {
                log.warn("读取锁定剩余时间失败（Redis 不可用）", e);
            }
        }
        return -1;
    }

    @Override
    public UserEntity getById(Long id) {
        return id == null ? null : userMapper.selectById(id);
    }

    @Override
    public long count() {
        return userMapper.countPage(null, null, null);
    }

    @Override
    public LoginVO login(String username, String password, String ip) {
        return login(username, password, ip, null, "console");
    }

    @Override
    public LoginVO login(String username, String password, String ip, String ua, String appId) {
        if (!loginPolicyService.passwordChannelEnabled()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "密码登录通道已关闭，请使用其他登录方式");
        }
        if (currentFailCount(username) >= loginPolicyService.lockThreshold()) {
            long remain = lockRemainSeconds(username);
            String tip = remain > 0
                    ? "请 " + (remain / 60 + 1) + " 分钟后重试"
                    : "请稍后重试或联系管理员解锁";
            throw new BizException(ResultCode.FORBIDDEN.getCode(),
                    "连续输错 " + loginPolicyService.lockThreshold() + " 次，账号已锁定，" + tip);
        }

        UserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号不存在");
        }
        if (Objects.equals(user.getStatus(), 0)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "账号已禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            int count = incrFailCount(username);
            log.warn("用户登录失败：{}（{}），连续失败 {} 次", username, ip, count);
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(),
                    "账号或密码错误，还可尝试 " + Math.max(0, loginPolicyService.lockThreshold() - count) + " 次");
        }

        clearFailCount(username);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);

        String sid = sessionService.newSid();
        String token = jwtUtil.createToken(user.getId(), username, user.getTokenVersion(), sid);
        sessionService.register(user, sid, ip, ua, appId);
        log.info("用户登录成功：{}（{}，会话 {}，平台 {}）", username, ip, sid, appId);
        return LoginVO.of(token, user, loginPolicyService.mfaRequiredFor(user.getRole()));
    }

    @Override
    public void logout(Long userId) {
        // JWT 无状态：自增版本后，该账号此前签发的所有令牌都会被拦截器判为失效
        UserEntity user = getById(userId);
        if (user != null) {
            UserEntity update = new UserEntity();
            update.setId(userId);
            update.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
            update.setUpdateTime(LocalDateTime.now());
            userMapper.update(update);
        }
        sessionService.clearByUser(userId, user == null ? null : user.getUsername());
        log.info("用户退出登录：userId={}（全部会话已清理）", userId);
        UserContext.clear();
    }

    @Override
    public LoginVO register(RegisterRequest request, String ip) {
        String username = request.username() == null ? "" : request.username().trim();
        if (!username.matches("^[a-zA-Z0-9_]{3,32}$")) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "账号需为 3-32 位字母、数字或下划线");
        }
        if (request.password() == null || request.password().length() < 8 || request.password().length() > 64) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "密码长度需为 8-64 位");
        }
        UserSaveRequest save = new UserSaveRequest();
        save.setUsername(username);
        save.setPassword(request.password());
        save.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : username);
        // 开放注册默认只读角色，后续由管理员调整 / 分组授权
        save.setRole("VIEWER");
        save.setStatus(1);
        createUser(save);
        log.info("新用户注册：username={}，ip={}", username, ip);
        return login(username, request.password(), ip);
    }

    @Override
    public Page<UserEntity> pageUsers(UserQuery query) {
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        String role = StringUtils.hasText(query.getRole()) ? query.getRole() : null;

        long total = userMapper.countPage(keyword, role, query.getStatus());
        long current = Math.max(1, query.getCurrent());
        long size = Math.max(1, query.getSize());
        List<UserEntity> records = total == 0 ? List.of()
                : userMapper.selectPage(keyword, role, query.getStatus(), (current - 1) * size, size);
        records.forEach(u -> u.setPassword(null));
        return Page.of(records, total, current, size);
    }

    @Override
    public void createUser(UserSaveRequest request) {
        checkUsernameUnique(request.getUsername(), null);

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        // DB 列 NOT NULL 且无默认值，MyBatis 显式插入 null 会被拒，这里补 0
        user.setTokenVersion(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
    }

    @Override
    public void updateUser(Long id, UserSaveRequest request) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        checkUsernameUnique(request.getUsername(), id);

        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        user.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userMapper.update(user);
    }

    @Override
    public void deleteUser(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        if ("admin".equals(user.getUsername())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "内置超级管理员不可删除");
        }
        userMapper.deleteById(id, LocalDateTime.now());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        UserEntity update = new UserEntity();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.update(update);
    }

    @Override
    public void unlockLogin(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "用户不存在");
        }
        clearFailCount(user.getUsername());
        log.info("管理员解锁账号登录：{}（id={}）", user.getUsername(), id);
    }

    /**
     * 校验账号唯一性（excludeId 为当前修改的用户，新增时为 null）
     */
    private void checkUsernameUnique(String username, Long excludeId) {
        if (userMapper.countByUsername(username, excludeId) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "账号已存在：" + username);
        }
    }
}
