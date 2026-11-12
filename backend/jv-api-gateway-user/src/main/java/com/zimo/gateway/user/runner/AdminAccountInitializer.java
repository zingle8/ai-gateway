package com.zimo.gateway.user.runner;

import com.zimo.gateway.user.model.UserSaveRequest;
import com.zimo.gateway.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台账号初始化：用户表为空时写入内置超级管理员与演示账号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserService userService;

    @Value("${gateway.default-admin.username:admin}")
    private String adminUsername;

    @Value("${gateway.default-admin.password:Admin@123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userService.count() > 0) {
            return;
        }

        create(adminUsername, adminPassword, "平台管理员", "admin@example.com", "13000000000", "ADMIN");
        create("dev", "Dev@123", "张开发", "dev@example.com", "13100000001", "OPERATOR");
        create("ops", "Ops@123", "李运维", "ops@example.com", "13200000002", "OPERATOR");
        create("viewer", "View@123", "王只读", "viewer@example.com", "13300000003", "VIEWER");

        log.info("==================================================");
        log.info("  网关平台账号已初始化");
        // 初始密码脱敏打印（v0.2 第 14 项）：避免明文密码落到日志文件
        log.info("  超级管理员：{} / {}", adminUsername, com.zimo.gateway.common.log.LogMask.token(adminPassword));
        log.info("  演示账号：dev / Dev@123、ops / Ops@123、viewer / View@123");
        log.info("==================================================");
    }

    private void create(String username, String password, String nickname,
                        String email, String phone, String role) {
        UserSaveRequest request = new UserSaveRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setNickname(nickname);
        request.setEmail(email);
        request.setPhone(phone);
        request.setRole(role);
        request.setStatus(1);
        userService.createUser(request);
    }
}
