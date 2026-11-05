# SQL 目录规范

所有建表与种子脚本统一放在**本目录**，不再散落在各模块的 `src/main/resources` 下。

## 命名规则

```
<模块名>-<功能名>-<主表名>.sql
```

| 前缀 | 归属服务 | 说明 |
| --- | --- | --- |
| `jv-api-gateway-user-` | 用户与权限 | 账号、分组权限、API Key |
| `jv-api-gateway-proxy-` | 网关代理 | 转发路由 |
| `jv-api-gateway-config-` | 系统配置 | 平台级配置项 |
| `jv-api-gateway-discovery-` | 服务发现 | 服务注册表与接口清单 |
| `jv-api-gateway-trace-` | 链路追踪 | span / 调用日志 / 拓扑聚合 |

示例：`jv-api-gateway-user-perm-tb_perm_group.sql`（模块 = user，功能 = 分组权限，主表 = tb_perm_group）。

## 打包方式

各模块 `pom.xml` 通过 `<resources>` 把**本目录中带自身模块前缀的脚本**复制进 jar 的 `db/` 目录：

```xml
<resource>
  <directory>${project.basedir}/../sql</directory>
  <includes><include>jv-api-gateway-user-*.sql</include></includes>
  <targetPath>db</targetPath>
</resource>
```

因此模块启动时仍可用 `classpath:db/xxx.sql` 读取，`*SchemaInitializer` 自动执行（全部幂等）。

## 幂等约定

- 建表统一 `CREATE TABLE IF NOT EXISTS`
- 种子统一 `INSERT IGNORE`（依赖唯一键）或 `WHERE NOT EXISTS`
- 不要写 `DROP TABLE`
