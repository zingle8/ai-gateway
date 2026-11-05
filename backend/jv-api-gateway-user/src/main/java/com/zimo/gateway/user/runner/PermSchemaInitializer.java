package com.zimo.gateway.user.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * 分组权限 / API Key 表结构初始化：
 * 启动时执行 classpath:db/perm_apikey.sql（全部为幂等语句，可重复执行）。
 *
 * <p>排在 AdminAccountInitializer 之后：种子里的「admin 加入超级管理员分组」
 * 依赖内置账号已存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE + 1)
public class PermSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            EncodedResource script = new EncodedResource(
                    new ClassPathResource("db/perm_apikey.sql"), StandardCharsets.UTF_8);
            ScriptUtils.executeSqlScript(connection, script);
            log.info("[初始化] 分组权限 / API Key 表结构与种子数据就绪");
        } catch (Exception e) {
            log.warn("[初始化] perm_apikey.sql 执行失败（不影响既有功能，请手工执行该脚本）：{}", e.getMessage());
        }
    }
}
