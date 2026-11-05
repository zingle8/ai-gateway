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
 * 账号表结构初始化：先于 AdminAccountInitializer 执行。
 *
 * <p>SQL 统一维护在 backend/sql（见 sql/README.md），打包时按模块前缀复制到
 * classpath:db/ 下。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 2)
public class UserSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("db/jv-api-gateway-user-auth-tb_sys_user.sql"),
                    StandardCharsets.UTF_8));
            log.info("[初始化] 账号表结构就绪");
        }
    }
}
