package com.zimo.gateway.proxy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * 路由表结构初始化：保证 DbRouteDefinitionLocator 首次启动即有表可读。
 * SQL 统一维护在 backend/sql，打包时按模块前缀复制到 classpath:db/。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProxySchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("db/jv-api-gateway-proxy-route-tb_gw_route.sql"),
                    StandardCharsets.UTF_8));
            log.info("[初始化] 网关路由表结构与种子数据就绪");
        } catch (Exception e) {
            log.warn("[初始化] 路由脚本执行失败（请手工执行 backend/sql 下脚本）：{}", e.getMessage());
        }
    }
}
