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
import java.util.List;

/**
 * 分组权限 / API Key 表结构初始化。
 *
 * <p>SQL 统一维护在 backend/sql（见 sql/README.md），打包时按模块前缀复制到
 * classpath:db/ 下。排在 AdminAccountInitializer 之后：种子里的
 * 「admin 加入超级管理员分组」依赖内置账号已存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE + 1)
public class PermSchemaInitializer implements ApplicationRunner {

    private static final List<String> SCRIPTS = List.of(
            "db/jv-api-gateway-user-perm-tb_perm_group.sql",
            "db/jv-api-gateway-user-apikey-tb_gw_api_key.sql",
            "db/jv-api-gateway-user-apikey-log-tb_gw_api_key_log.sql"
    );

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            for (String script : SCRIPTS) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ClassPathResource(script), StandardCharsets.UTF_8));
            }
            log.info("[初始化] 分组权限 / API Key 表结构与种子数据就绪");
        } catch (Exception e) {
            log.warn("[初始化] 分组权限脚本执行失败（不影响既有功能，请手工执行 backend/sql 下脚本）：{}", e.getMessage());
        }
    }
}
