# 配置信息一览（数据库 / 中间件 / 端口 / 密钥）

> 本文汇总项目全部外部依赖的连接信息与配置方式，供开发与部署快速查阅。
> 所有配置均有两层来源：`backend/*/src/main/resources/application.yml`（默认值）
> 与**环境变量覆盖**（优先级更高）。生产环境务必用环境变量覆盖默认口令。

> ⚠️ **安全提示**：本文与 `application.yml` 中的口令均为本机演示环境默认值。
> 若仓库公开或口令在真实环境复用，请立即轮换，并通过环境变量注入新值。

## 1. 总览

| 组件 | 地址 | 账号 / 密码 | 容器名 / 镜像 | 是否必需 |
| --- | --- | --- | --- | --- |
| MySQL | `127.0.0.1:3306` | `root` / `Root@123456` | `mysql57`（`mysql:5.7`） | **必需** |
| Redis | `127.0.0.1:6379` | 无用户名 / `Redis@123456` | `redis`（`redis:7.2-alpine`） | 可选（降级运行） |
| Nacos | `127.0.0.1:8848`（控制台 `8080`） | `nacos` / `nacos` | `nacos`（`nacos/nacos-server:latest`，standalone） | 可选（降级运行） |

容器统一由 `scripts/init-env.sh` 管理（幂等：存在则启动，不存在则按上述默认参数创建）。

## 2. MySQL

| 项 | 值 | 说明 |
| --- | --- | --- |
| 主机 / 端口 | `127.0.0.1:3306` | 本机端口映射，容器内 3306 |
| 用户名 / 密码 | `root` / `Root@123456` | 所有后端服务共用 |
| 数据库 | `api_gateway` | 唯一业务库，5 个微服务共用 |
| JDBC URL | `jdbc:mysql://127.0.0.1:3306/api_gateway?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` | proxy 额外加 `rewriteBatchedStatements=true`（调用日志批量写入） |
| 初始化 | `./scripts/init-db.sh` | 建库 + 建表 + 种子数据；`--drop` 删库重建 |
| 表 | `tb_sys_user` / `tb_gw_service` / `tb_gw_route` / `tb_gw_api` / `tb_gw_trace_log` | 见 `backend/sql/` 下对应模块脚本 |

MySQL 密码目前硬编码在 `application.yml`，改法：直接改各服务 yml 的
`spring.datasource.username/password`，或迁移到环境变量（推荐）。

## 3. Redis

用于登录限流计数（`gw:login:fail:*`）与账号解锁，**不可用时自动降级为单实例内存计数，不影响启动与登录**。

| 项 | 值 | 环境变量（默认值） |
| --- | --- | --- |
| 主机 | `127.0.0.1` | `REDIS_HOST`（`127.0.0.1`） |
| 端口 | `6379` | `REDIS_PORT`（`6379`） |
| 密码 | `Redis@123456` | `REDIS_PASSWORD`（`Redis@123456`） |

配置位置：`jv-api-gateway-user/src/main/resources/application.yml` → `spring.data.redis.*`。

运维排查：

```bash
docker exec redis redis-cli -a 'Redis@123456' ping          # 期望 PONG
docker exec redis redis-cli -a 'Redis@123456' KEYS 'gw:login:fail:*'   # 被锁账号
```

## 4. Nacos（注册中心 / 配置中心）

**以 `optional:` 方式引入，Nacos 挂掉不影响任何服务启动**，只失去动态配置能力。

| 项 | 值 |
| --- | --- |
| 服务地址（客户端连接） | `127.0.0.1:8848`（**必须用 8848**，v2.x 按 8848+1000 推算 gRPC 端口 9848；用控制台 8080 会导致注册失败） |
| 控制台 | `http://localhost:8080/nacos`，账号 `nacos` / `nacos` |
| 模式 | standalone |
| 挂载的配置文件 | `api-jv-api-gateway-common.yaml`、`api-jv-api-gateway-user.yaml`、`api-jv-api-gateway-overview.yaml`、`api-jv-api-gateway-discovery.yaml`、`api-jv-api-gateway-trace.yaml`（均为 optional，可不存在） |

配置位置：各服务 yml → `spring.config.import` 与 `spring.cloud.nacos.*`。

## 5. JWT 与内置账号（jv-api-gateway-user）

| 项 | 默认值 | 环境变量 |
| --- | --- | --- |
| JWT 签名密钥 | `ai-gateway-platform-jwt-secret-key-change-me-please` | `GATEWAY_JWT_SECRET`（**生产必须覆盖**，≥ 32 字节） |
| Token 有效期 | 8 小时 | yml `gateway.jwt.expire-hours` |
| 内置超管账号 | `admin` / `Admin@123` | yml `gateway.default-admin.username/password` |

说明：内置账号仅在 `tb_sys_user` **为空时**由 `AdminAccountInitializer` 写入；
`init-db.sh` 初始化的种子数据已含 admin，故演示账号（dev/ops/viewer）不会自动创建。

## 6. 服务端口清单

| 服务 | 端口 | context-path |
| --- | --- | --- |
| 控制台前端（Vite dev） | 28080 | `/`（`/api` 代理到 28090） |
| jv-api-gateway-user（账号权限） | 28081 | `/api` |
| jv-api-gateway-overview（总览统计） | 28082 | `/api` |
| jv-api-gateway-discovery（服务/路由） | 28083 | `/api` |
| jv-api-gateway-trace（日志追踪） | 28084 | `/api` |
| jv-api-gateway-proxy（网关入口） | 28090 | `/` |

## 7. 环境变量速查

| 变量 | 默认值 | 作用 |
| --- | --- | --- |
| `GATEWAY_JWT_SECRET` | `ai-gateway-platform-jwt-secret-key-change-me-please` | JWT 签名密钥（生产必改） |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `127.0.0.1` / `6379` / `Redis@123456` | Redis 连接 |
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` / `DB_NAME` | `127.0.0.1` / `3306` / `root` / `Root@123456` / `api_gateway` | 仅供 `scripts/init-db.sh` 等脚本使用（后端连接串在 yml 内） |
| `JAVA_OPTS` | `-Xms256m -Xmx512m` | `scripts/start.sh` 启动 JVM 参数 |

## 7.1 业务接入侧配置（zimo.gateway.client.*，业务系统 yml）

业务系统引入 `jv-api-gateway-client-starter` 后在自身 `application.yml` 配置，完整示例见
[`docs/client-integration.md`](client-integration.md)：

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `zimo.gateway.client.server-addr` | `http://127.0.0.1:28080/api` | 平台统一入口（注册 / 心跳 / 上报都走它） |
| `zimo.gateway.client.app-id` / `app-name` | `app-demo` / 示例应用 | 接入归属：应用ID（英文）/ 应用名（中文） |
| `zimo.gateway.client.service-id` / `service-name` | `demo-service` / 演示服务 | 接入归属：服务ID（英文唯一标识，注册幂等键）/ 服务名（中文显示名，不填则显示服务ID） |
| `zimo.gateway.client.trace.sample-rate` | `1.0` | 采集采样率（高流量服务建议 0.1~0.3） |
| `zimo.gateway.client.registry.heartbeat-interval` | `30000` | 心跳间隔（ms） |

另：`jv-api-gateway-proxy` 自注册走 `zimo.gateway.discovery.addr`（默认 `http://127.0.0.1:28083/api`，直连 discovery，不经自身转发）。

## 8. 修改配置的三种方式（优先级从高到低）

1. **环境变量**：如 `GATEWAY_JWT_SECRET=xxx ./scripts/start.sh`（已支持 `${VAR:default}` 占位符的项）；
2. **Nacos 配置中心**：在控制台修改 `api-jv-api-gateway-<service>.yaml`，服务重启后生效（当前为 optional 引入）；
3. **直接改 `application.yml`**：改完需 `./scripts/restart.sh backend --build` 重新构建生效。

## 平台配置的多来源存储

| 来源 | 位置 | 默认值 |
| --- | --- | --- |
| DB | MySQL `api_gateway.tb_sys_config.config_value` | 3 个平台开关默认落这里 |
| Redis | Hash `gw:config:platform`（field = 配置键） | 连接沿用 `spring.data.redis.*` |
| Nacos | `DEFAULT_GROUP / gw-platform-config.properties`（properties 文本） | 地址 `127.0.0.1:8848`，账号 `nacos/nacos`，可用 `NACOS_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` 覆盖 |

> 元数据（key / 来源 / 平台 / 应用 / 服务）始终在 MySQL；值在所选存储里。
> 远端不可达时控制台展示最后写入快照并标记 STALE。
