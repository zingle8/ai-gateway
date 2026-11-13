package com.zimo.gateway.user.service;

import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.user.entity.UserEntity;
import com.zimo.gateway.user.model.LoginVO;
import com.zimo.gateway.user.model.RegisterRequest;
import com.zimo.gateway.user.model.UserQuery;
import com.zimo.gateway.user.model.UserSaveRequest;

/**
 * 用户服务
 */
public interface UserService {

    /** 按主键查询（逻辑删除的不返回） */
    UserEntity getById(Long id);

    /** 未删除用户总数（启动时判断是否需要初始化内置账号） */
    long count();

    /**
     * 登录：校验账号密码，成功返回 JWT
     */
    LoginVO login(String username, String password, String ip);

    /**
     * 登录并登记在线会话。
     *
     * @param ua    浏览器 UA（解析设备指纹，供按端踢人）
     * @param appId 归属平台（控制台 console，开放认证为接入方 appId）
     */
    LoginVO login(String username, String password, String ip, String ua, String appId);

    /**
     * 非密码因子登录（v0.2 第 4 项）：短信 / 邮件动态码、扫码确认通过后，
     * 因子校验已在 LoginFactorService 完成，这里只做状态校验 + 会话登记 + 签发令牌。
     *
     * @param factor PASSWORD / SMS / EMAIL / QR / QR_CONFIRM，仅用于登录历史标注
     */
    LoginVO loginByFactor(UserEntity user, String ip, String ua, String appId, String factor);

    /**
     * 注册：校验账号格式与唯一性，默认 VIEWER 角色，成功即登录（返回 JWT）
     */
    LoginVO register(RegisterRequest request, String ip);

    /**
     * 退出：清理登录态（JWT 无状态，这里仅记录退出日志 / 清理缓存位）
     */
    void logout(Long userId);

    /**
     * 用户分页列表
     */
    Page<UserEntity> pageUsers(UserQuery query);

    /**
     * 新增用户
     */
    void createUser(UserSaveRequest request);

    /**
     * 修改用户
     */
    void updateUser(Long id, UserSaveRequest request);

    /**
     * 删除用户（逻辑删除）
     */
    void deleteUser(Long id);

    /**
     * 启用 / 禁用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 解锁账号登录：清除该账号的连续失败计数。
     *
     * 账号连续输错达到上限后会被锁定（锁定期间连正确密码也会被拒），
     * 需等待计数过期或由管理员手动解锁，避免管理员把自己锁在门外。
     */
    void unlockLogin(Long id);
}
