package com.zimo.gateway.config.runner;

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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统配置表初始化（幂等）。
 *
 * <p>SQL 统一维护在 backend/sql，打包时按模块前缀复制到 classpath:db/。
 * 由于 MySQL 5.7 没有 ADD COLUMN IF NOT EXISTS，多来源字段的存量迁移
 * 在这里用 DatabaseMetaData 判断后执行，避免每次启动重复 ALTER。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigSchemaInitializer implements ApplicationRunner {

    private static final String TABLE = "tb_sys_config";

    /** 存量库需要补齐的列（顺序敏感，用 AFTER 保证可读） */
    private static final Map<String, String> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put("config_source",
                "ALTER TABLE " + TABLE + " ADD COLUMN config_source VARCHAR(16) NOT NULL DEFAULT 'DB' "
                        + "COMMENT '存储来源：DB/REDIS/NACOS' AFTER value_type");
        COLUMNS.put("source_ref",
                "ALTER TABLE " + TABLE + " ADD COLUMN source_ref VARCHAR(255) DEFAULT NULL "
                        + "COMMENT '来源定位：Redis=Hash键，Nacos=dataId|groupName' AFTER config_source");
        COLUMNS.put("platform_name",
                "ALTER TABLE " + TABLE + " ADD COLUMN platform_name VARCHAR(64) NOT NULL DEFAULT 'API网关平台' "
                        + "COMMENT '所属平台' AFTER source_ref");
        COLUMNS.put("app_name",
                "ALTER TABLE " + TABLE + " ADD COLUMN app_name VARCHAR(64) NOT NULL DEFAULT 'jv-api-gateway' "
                        + "COMMENT '所属应用' AFTER platform_name");
        COLUMNS.put("service_name",
                "ALTER TABLE " + TABLE + " ADD COLUMN service_name VARCHAR(64) NOT NULL DEFAULT '*' "
                        + "COMMENT '所属服务，* 表示平台级' AFTER app_name");
    }

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            migrateColumns(connection);
            for (String script : new String[]{"db/jv-api-gateway-config-config-tb_sys_config.sql",
                    "db/jv-api-gateway-config-ratelimit-tb_gw_rate_limit.sql"}) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ClassPathResource(script), StandardCharsets.UTF_8));
            }
            log.info("[初始化] 系统配置表、默认配置与限流规则表就绪");
        } catch (Exception e) {
            log.warn("[初始化] 系统配置脚本执行失败（请手工执行 backend/sql 下脚本）：{}", e.getMessage());
        }
    }

    /** 补齐 config_source / source_ref / platform_name / app_name / service_name 与值长度 */
    private void migrateColumns(Connection connection) throws Exception {
        boolean exists;
        try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, TABLE, new String[]{"TABLE"})) {
            exists = rs.next();
        }
        if (!exists) {
            return;   // 新库：建表脚本会带全字段
        }
        Map<String, Integer> existing = columnLengths(connection);
        for (Map.Entry<String, String> entry : COLUMNS.entrySet()) {
            if (!existing.containsKey(entry.getKey())) {
                try (Statement st = connection.createStatement()) {
                    st.execute(entry.getValue());
                    log.info("[初始化] tb_sys_config 补齐字段 {}", entry.getKey());
                }
            }
        }
        Integer valueLen = existing.get("config_value");
        if (valueLen != null && valueLen < 512) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE " + TABLE + " MODIFY COLUMN config_value VARCHAR(512) NOT NULL COMMENT '配置值（非 DB 来源时为最后写入快照）'");
                log.info("[初始化] tb_sys_config.config_value 长度扩展到 512");
            }
        }
    }

    private Map<String, Integer> columnLengths(Connection connection) throws Exception {
        Map<String, Integer> out = new LinkedHashMap<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, TABLE, null)) {
            while (rs.next()) {
                out.put(rs.getString("COLUMN_NAME").toLowerCase(), rs.getInt("COLUMN_SIZE"));
            }
        }
        return out;
    }
}
