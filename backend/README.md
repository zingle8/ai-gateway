# AI 网关平台 · 后端工程（com.zimo）

Spring Boot 3.2.12 + 原生 MyBatis + MySQL + Nacos，Maven 多模块，
按业务域拆分为 5 个可独立启动的微服务。

> 持久层统一使用 **原生 MyBatis**（`mybatis-spring-boot-starter` + XML SQL），
> 不使用 MyBatis-Plus，不依赖任何 ORM 分页插件。规范见「[持久层](#持久层)」。

## 模块划分

| 模块 | 说明 | 端口 | 包名 |
| --- | --- | --- | --- |
| `jv-api-gateway-common` | 平台内部公共模块：统一响应、异常、分页封装（`Page` / `PageResult`）、JWT、IP 工具、密码器配置 | — | `com.zimo.gateway.common` |
| `jv-api-gateway-client-common` | **对外公共模块**：上报模型、服务实例模型、常量、错误枚举（不依赖 Spring，业务系统可安全引入） | — | `com.zimo.gateway.client.common` |
| `jv-api-gateway-client-starter` | **网关子模块（Spring Boot Starter）**：自动接入注册、请求与响应采集、异步批量上报 | — | `com.zimo.gateway.client` |
| `jv-api-gateway-user` | 用户与权限模块：注册 / 登录 / 退出 / 当前用户、账号增删改查、分组与权限（部门组织 / 权限点字典 / 有效权限并集）、API Key 管理、鉴权拦截器、权限表结构初始化 | 28081 | `com.zimo.gateway.user` |
| `jv-api-gateway-config` | 系统配置模块：平台级配置统一存储与实时下发（菜单健康标识 / 鉴权缓存模式 / 开放认证开关），启动自动建表+种子 | 28085 | `com.zimo.gateway.config` |
| `jv-api-gateway-overview` | 网关总览：指标汇总、QPS 趋势、运行时信息、网关实例 | 28082 | `com.zimo.gateway.overview` |
| `jv-api-gateway-discovery` | 服务发现：注册服务列表、服务详情（端口 / 实例数 / 注册时间）、心跳巡检、转发路由（`tb_gw_route`）增删改查 | 28083 | `com.zimo.gateway.discovery` |
| `jv-api-gateway-trace` | 日志追踪：调用日志分页查询、请求/响应报文详情 | 28084 | `com.zimo.gateway.trace` |
| `jv-api-gateway-proxy` | 流量代理：按 `tb_gw_route` 转发并记录调用详情（WebFlux，不依赖 common） | 28090 | `com.zimo.gateway.proxy` |

所有服务统一 `context-path: /api`，路径前缀与前端模块的 `/api/<service>` 一一对应。

### 流量入口

前端所有 `/api` 请求（含控制台自身的登录、列表）统一打 **`jv-api-gateway-proxy`（28090）**，
再由 `tb_gw_route` 转发到后端各微服务：

```
浏览器 / 外部调用方
      ↓  /api/**
jv-api-gateway-proxy :28090   按 tb_gw_route 匹配 path_prefix → upstream_url
      ↓
user :28081 / overview :28082 / discovery :28083 / trace :28084
```

这样做的两个好处：

1. 控制台自己的每次请求都会被网关采集，日志追踪里能看到完整链路；
2. 后端换端口只改 `tb_gw_route`，前端不用动（路由改动最多 30 秒内由 proxy 热加载生效）。

### 平台自监控（自己监控自己）

平台本身也是被监控对象，5 个模块都会注册进服务发现，服务发现页能看到自己：

| 模块 | 上报方式 |
| --- | --- |
| user / overview / discovery / trace | 引入 `jv-api-gateway-client-starter`，`zimo.gateway.client.*` 配置后自动注册 + 心跳（30 秒） |
| proxy | WebFlux 无 Servlet 过滤器，由 `ProxySelfRegistrar` 手写定时心跳 |

- 各模块 `zimo.gateway.client.trace.enabled: false`：链路采集已由 proxy 在转发时完成，模块内不再重复埋点
- 协议字段记的是**客户端到网关**的真实版本（`HTTP/1.1` / `HTTP/2`）：proxy 已开启 `server.http2.enabled`，
  实现上需沿 `ServerHttpRequestDecorator` 链解到底层 reactor-netty 对象反射取 `version()`，
  取不到时回落 `HTTP/1.1`
- 心跳巡检：`HeartbeatMonitor` 每 60 秒扫一次，超过 **90 秒**（3 个心跳周期）无心跳的服务标记为「离线」，
  进程被强杀后状态不会永远停在「健康」；服务重启后第一次心跳即自动恢复「健康」
- 服务列表的 QPS / 平均耗时 / 出入流量不是注册时写死的快照，而是从 `tb_gw_trace_log` 实时聚合
  （QPS 取近 60 秒，耗时与流量取近 5 分钟）

## 对外复用模块（SDK）

| 模块 | 定位 | 依赖 |
| --- | --- | --- |
| `jv-api-gateway-client-common` | 公共模块：DTO / 常量 / 枚举，供所有项目复用，避免重复定义 | 无 Spring 依赖 |
| `jv-api-gateway-client-starter` | 网关子模块：自动装配，零编码完成接入与采集 | Spring Boot 3 |

- 业务系统只引 `jv-api-gateway-client-starter` 即可；它传递依赖 `jv-api-gateway-client-common`。
- 只想要模型（例如自己实现上报逻辑）时，单独引 `jv-api-gateway-client-common`。
- 网关平台本身也是微服务，其他项目通过 `GatewayPlatformClient` 调用平台能力。

详见 [../docs/client-integration.md](../docs/client-integration.md)。

## 命名规范

- Maven groupId：`com.zimo`
- 模块目录 / artifactId / jar 名 / `spring.application.name`：统一 `jv-api-gateway-<模块>`
  （如 `jv-api-gateway-overview`，注意是 `jv-api-gateway-` 而非 `jv-gateway-`）
- Java 根包名：`com.zimo.gateway.<模块>`
  - 例：`com.zimo.gateway.user.entity`、`com.zimo.gateway.trace.service.impl`
- Mapper XML：`classpath*:/mapper/**/*.xml`
- `@MapperScan`：`com.zimo.gateway.*.mapper`

## 持久层

统一使用 **原生 MyBatis**：Mapper 接口 + XML SQL，不使用 MyBatis-Plus。

### 依赖与配置

```xml
<dependency>
  <groupId>org.mybatis.spring.boot</groupId>
  <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>
```

```yaml
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true   # 蛇形列 → 驼峰属性
```

XML 放在各模块 `src/main/resources/mapper/<Entity>Mapper.xml`，
Mapper 接口加 `@Mapper`（启动类另有 `@MapperScan("com.zimo.gateway.<模块>.mapper")`）。

### 必须显式处理的四件事

MyBatis-Plus 的隐式行为在本工程里全部改成**显式 SQL**，新写 Mapper 时照着来：

| 能力 | 做法 |
| --- | --- |
| 逻辑删除 | 所有查询显式 `WHERE deleted = 0`；删除是 `UPDATE ... SET deleted = 1`，不做物理删除 |
| 主键回填 | `<insert useGeneratedKeys="true" keyProperty="id">` |
| 部分更新 | `<update>` 里用 `<set>` + 逐字段 `<if test="xx != null">`，等价于 MP `updateById` 忽略 null 的语义 |
| 分页 | **COUNT + LIMIT 两条 SQL，共用同一段 `<sql>` 条件片段** |

### 分页约定

返回 `com.zimo.gateway.common.result.Page<T>`，由 `Page.of(records, total, current, size)` 构造
（`pages = (total + size - 1) / size`，与常见分页插件口径一致），
Controller 再转 `PageResult` 输出。响应结构固定：

```json
{ "records": [], "total": 233, "pages": 24, "current": 1, "size": 10 }
```

条件片段必须复用，否则「总条数」和「当前页记录」会对不上：

```xml
<sql id="pageWhere">
    deleted = 0
    <if test="keyword != null and keyword != ''">
        AND (username LIKE CONCAT('%', #{keyword}, '%')
        OR nickname LIKE CONCAT('%', #{keyword}, '%'))
    </if>
</sql>

<select id="selectPage" resultType="...User">
    SELECT <include refid="cols"/> FROM tb_sys_user
    WHERE <include refid="pageWhere"/>
    ORDER BY id DESC LIMIT #{offset}, #{size}
</select>

<select id="countPage" resultType="long">
    SELECT COUNT(*) FROM tb_sys_user WHERE <include refid="pageWhere"/>
</select>
```

**全模块零 `JdbcTemplate` / 零 JPA**，聚合类查询同样走 Mapper + XML：

| 模块 | 持久层实现 | 说明 |
| --- | --- | --- |
| `jv-api-gateway-user` / `jv-api-gateway-discovery` / `jv-api-gateway-trace` | `*Mapper` + XML | 增量维护 |
| `jv-api-gateway-overview` | `OverviewMapper` + `OverviewMapper.xml` | 总览、数据统计、趋势的所有聚合 SQL |
| `jv-api-gateway-proxy` | `RouteMapper` / `TraceLogMapper` + XML | WebFlux 下由 `TraceSink` 每秒批量 INSERT，SqlSession 随调用开启关闭，不跨线程 |

聚合查询返回 `Map` 时，别名直接写成前端/实体需要的字段名（`SELECT COUNT(*) AS \`count\``），
列表类查询用 `StatsRow` / `StatsAgg` 这类轻量 DTO 承接，避免 `resultType="map"` 的 key 大小写踩坑。

## 本机环境（docker）

三个中间件容器由 `scripts/init-env.sh` 统一管理（幂等：容器存在则 `docker start`，
不存在则按默认参数创建；端口被非容器进程占用时视为可用）：

```bash
./scripts/init-env.sh          # 全部检查并拉起
./scripts/init-env.sh mysql    # 只拉起单个
```

| 组件 | 容器 / 镜像 | 地址 | 账号 |
| --- | --- | --- | --- |
| MySQL | `mysql57`（`mysql:5.7`） | `127.0.0.1:3306` | `root` / `Root@123456` |
| Nacos | `nacos`（`nacos/nacos-server:latest`） | 控制台 `http://localhost:8080/nacos`，服务端口 `8848` | `nacos` / `nacos` |
| Redis | `redis`（`redis:7.2-alpine`） | `redis://127.0.0.1:6379` | `Redis@123456` |

常用命令：

```bash
docker logs -f mysql57          # 查看 MySQL 日志
docker logs -f nacos            # 查看 Nacos 日志
docker exec -it redis redis-cli -a 'Redis@123456' ping   # 返回 PONG
```

> **Nacos 地址配置要点**：Nacos 2.x 会按 `主端口 + 1000` 推算 gRPC 端口，
> 因此客户端地址必须用**服务端口** `8848`（gRPC 对应 `9848`），
> 不能用**控制台端口** `8080` —— 那会推算到未映射的 `9080`，导致注册与配置拉取失败。
> 本机容器已映射 `8848`、`9848`，故四个模块统一配置为 `127.0.0.1:8848`。
> 自建容器时请一并映射 gRPC 端口：
>
> ```bash
> docker run -d --name nacos -e MODE=standalone \
>   -p 8848:8848 -p 9848:9848 -p 9849:9849 nacos/nacos-server:latest
> ```

## 数据库

- 库名：`api_gateway`
- 建表与示例数据：`sql/jv-api-gateway-discovery-registry-tb_gw_service.sql`（服务/接口）、`sql/jv-api-gateway-user-auth-tb_sys_user.sql`（账号）

主要表：

- `tb_sys_user`：平台账号（密码 BCrypt 存储）
- `tb_gw_service`：注册服务（含 `port`、`instances`、`register_time`、`last_heartbeat_time` 等）
- `tb_gw_route`：转发路由（`path_prefix` → `upstream_url`，proxy 每 30 秒热加载）
- `tb_gw_api`：网关接口（接口个数统计）
- `tb_gw_trace_log`：网关调用日志（含 `req_params`、`req_header`、`req_body`、`res_header`、`res_body`、`err_type` 等）
  - `req_params`：URL 上的原始 query string；表单（`x-www-form-urlencoded`）参数原文在 `req_body`
  - `req_bytes` / `res_bytes`：真实流经字节数（不再用截断后字符数）
- `tb_gw_trace_span`：调用链路 span（`jv-api-gateway-trace-span-tb_gw_trace_span.sql`），一跳两个 span：`SERVER`（服务端处理）+ `CLIENT`（发起调用）
- `tb_gw_service_edge`：服务调用拓扑预聚合（`jv-api-gateway-trace-topology-tb_gw_service_edge.sql`），按小时窗口存调用对

#### 服务拓扑聚合（阶段二）

`EdgeAggregator`（jv-api-gateway-trace，依赖 `@EnableScheduling`）每 60s 对**最近 3 个整点小时窗口**
做「先删后插」重算，把 `tb_gw_trace_span` 的 CLIENT span 汇成 `tb_gw_service_edge`：

```sql
-- 窗口内 CLIENT span 按调用对聚合，peer_service 即被调方
SELECT service_name, peer_service, COUNT(*), SUM(status_code >= 400), SUM(cost), MAX(cost)
FROM tb_gw_trace_span
WHERE kind = 'CLIENT' AND start_time >= :ws AND start_time < :ws + 1h
GROUP BY service_name, peer_service
```

- 重算而非增量累加：同一窗口重复执行幂等，批量上报的迟到 span 会在下一轮被纠正
- **先删后插**：span 被清理/修正后，已消失的边不会残留在聚合表里（仅覆盖 3 小时窗口内）
- 拓扑接口只查 `uk_edge_window(src, dst, window_start)` 覆盖的小表，不在 span 大表上实时 GROUP BY
- 窗口数据保留 30 天，任务内顺带清理

慢链路 TopN 直接查 span 表（`idx_start` 命中分区 + `GROUP BY trace_id`），
总耗时取「最晚 span 终点 − 最早 span 起点」而非 `SUM(cost)`，避免并行兄弟 span 重复计入。

### 日志/链路表分区（每天一个分区）

`tb_gw_trace_log`、`tb_gw_trace_span`、`tb_gw_service_edge` 三表均按天分区，
建表见 `sql/jv-api-gateway-trace-span-tb_gw_trace_span.sql`（阶段一）与 `sql/jv-api-gateway-trace-span-tb_gw_trace_span-partition.sql`（阶段二：edge 表纳入 + log 表索引复合化）：

```sql
PRIMARY KEY (id, req_time)                 -- 分区键必须并入主键（edge 表 PK(id, window_start)）
PARTITION BY RANGE (TO_DAYS(req_time)) (   -- span 表用 start_time，edge 表用 window_start
  PARTITION p20261104 VALUES LESS THAN (TO_DAYS('2026-11-05')),
  ...
)
```

- 分区由 `jv-api-gateway-trace` 的 `PartitionMaintainer` 维护：启动即校验一次，之后每天 00:05
  补建未来 3 天分区（缺分区会导致插入报 `Table has no partition for value`，直接丢数据）、
  删除 45 天前的分区（`DROP PARTITION` 秒级、不产生 binlog 峰值，替代 `DELETE` + 逻辑删除；
  edge 表另有聚合器自身的 30 天行级清理，分区 45 天兜底物理释放）
- `tb_gw_api` 是接口登记配置表（百行级、无时间衰减），不做分区
- 索引与查询场景对应关系：

| 索引 | 命中场景 |
| --- | --- |
| `idx_trace(trace_id)` | 链路瀑布图：按 traceId 取全量 span |
| `idx_start(start_time)` | 时间窗统计 + 分区裁剪（`WHERE start_time >= ?` 直接定位天分区） |
| `idx_edge(service_name, peer_service, start_time)` | 服务拓扑：CLIENT span 的调用对聚合 |
| `idx_reqtime(req_time)`（log 表） | 无附加过滤的时间窗分页（ORDER BY req_time DESC LIMIT） |
| `idx_service_time(service_name, req_time)`（log 表） | 按服务的统计 / 分页 / 服务趋势（替代原单列 idx_service，扫描行数约 -68%） |
| `idx_app_time(app_id, req_time)`（log 表） | 按应用的统计 / 拓扑按应用圈服务（扫描行数约 -75%） |
| `idx_errtype_time(err_type, req_time)`（log 表） | 错误类型检索（错误检索总伴随时间窗） |

### 链路传播（阶段一）

- 协议：W3C `traceparent: 00-<traceId:32hex>-<spanId:16hex>-01`，同时兼容旧 `X-Gateway-Trace-Id`
- 网关 `TraceGlobalFilter`：有上游 traceparent 就继承（否则作为链路根），转发时注入新的 CLIENT spanId
  与 `X-Gateway-Served-By`（网关服务名，供下游 SDK 记录 peer_service，拓扑图节点因此能与服务发现对齐）
- 服务侧 SDK：`GatewayTraceFilter` 读 traceparent 记 SERVER span；`TracePropagationInterceptor`
  在通过 Spring 自动装配的 `RestClient.Builder` 发起调用时记 CLIENT span 并注入 header
- SDK 自身的注册/心跳/上报路径已在 `excludePaths` 默认排除，避免自引用噪音

```bash
# 基础表 + 示例数据
mysql -h127.0.0.1 -P3306 -uroot -p'Root@123456' < sql/jv-api-gateway-user-auth-tb_sys_user.sql
# 链路 span 表 + 日志表按天分区改造（新环境必跑）
mysql -h127.0.0.1 -P3306 -uroot -p'Root@123456' api_gateway < sql/jv-api-gateway-trace-span-tb_gw_trace_span.sql
# 服务拓扑预聚合表（阶段二，依赖上一步的 tb_gw_trace_span）
mysql -h127.0.0.1 -P3306 -uroot -p'Root@123456' api_gateway < sql/jv-api-gateway-trace-topology-tb_gw_service_edge.sql
```

## Redis

`jv-api-gateway-user` 使用 Redis 保存**登录失败计数**，使连续失败锁定在多实例部署下同样生效。

配置（`jv-api-gateway-user/src/main/resources/application.yml`，均可用环境变量覆盖）：

| 配置项 | 默认值 |
| --- | --- |
| `spring.data.redis.host` | `${REDIS_HOST:127.0.0.1}` |
| `spring.data.redis.port` | `${REDIS_PORT:6379}` |
| `spring.data.redis.password` | `${REDIS_PASSWORD:Redis@123456}` |

行为说明：

- 连续输错 **5 次**锁定账号，计数键为 `gw:login:fail:<username>`，**TTL 30 分钟**，到期自动解锁
- 登录成功会清除计数
- **Redis 不可用不影响启动与登录**：连接为懒建立，异常时自动降级为单实例内存计数（仅失去跨实例能力），日志会打印 `降级为内存计数` 告警

解锁被锁账号（锁定期间连正确密码也会被拒）：

```bash
# 方式一：管理员调用接口（需要另一个可用账号的 token，走网关或直连 user 服务均可）
curl -X POST http://127.0.0.1:28090/api/users/1/unlock \
  -H "Authorization: Bearer <TOKEN>"

# 方式二：直接清理计数（唯一管理员被锁时用这个）
docker exec redis redis-cli -a 'Redis@123456' DEL gw:login:fail:admin

# 查看当前被锁账号
docker exec redis redis-cli -a 'Redis@123456' KEYS 'gw:login:fail:*'
```

## Nacos

- 地址：`127.0.0.1:8848`（服务端口，gRPC 对应 9848），命名空间 `public`，分组 `DEFAULT_GROUP`
- 各服务通过 `spring.config.import` 引入配置：
  - `optional:nacos:api-jv-api-gateway-common.yaml`
  - `optional:nacos:api-gateway-<模块>.yaml`

`optional:` 前缀保证 **Nacos 未启动时也能正常启动**，自动回落本地 `application.yml`。

## 一键启动 / 关闭 / 重启

脚本位于工程根目录 `scripts/`，首次使用先赋执行权限：

```bash
chmod +x scripts/*.sh
```

| 脚本 | 说明 |
| --- | --- |
| `scripts/init-env.sh` | 拉起依赖中间件容器（MySQL / Nacos / Redis），幂等；`--check` 只检查 |
| `scripts/init-db.sh` | 初始化数据库（建库 + 建表，仅需执行一次；`--drop` 删库重建） |
| `scripts/start.sh` | 一键启动（默认全部；`backend` / `frontend` 可单独指定；`--build` 强制重新构建；MySQL 不可达时自动尝试拉起容器） |
| `scripts/stop.sh` | 一键关闭 |
| `scripts/restart.sh` | 一键重启（参数同 start.sh） |
| `scripts/status.sh` | 查看各服务运行状态 |
| `scripts/logs.sh` | 查看 / 跟踪日志 |
| `scripts/build-sdk-jar.sh` | 构建 Java SDK 分发包到 `dist/sdk-jar/`（含 `install.sh` 与 `pom-snippet.xml`） |

```bash
./scripts/init-db.sh                 # 首次：初始化 api_gateway 库
./scripts/start.sh                   # 启动全部：5 个后端服务（4 个微服务 + proxy 网关）+ 前端
./scripts/start.sh backend           # 只启动后端
./scripts/start.sh all --build       # 强制重新构建后端后再启动
./scripts/status.sh                  # 查看状态
./scripts/logs.sh                    # 跟踪全部日志
./scripts/logs.sh jv-api-gateway-user       # 只跟踪指定服务
./scripts/stop.sh                    # 关闭全部
./scripts/restart.sh                 # 重启全部
```

说明：

- 后端 `jar` 不存在时自动构建；加 `--build` 可强制重新构建
- 启动前自动检查 MySQL（必需）、Nacos / Redis（可选，不可达仅告警）
- 每个服务逐个启动并等待端口就绪（后端 90s、前端 60s 超时）
- 停止时先发 `TERM` 优雅退出，15s 未退出再 `KILL`
- 端口被占用且无本脚本 PID 记录时跳过该服务，**不会强杀其他进程**
- 日志与 PID 统一存放在 `logs/`
- JVM 参数可覆盖：`JAVA_OPTS="-Xms512m -Xmx1g" ./scripts/start.sh`

> **注意：不要在服务运行时执行 `mvn package`。**
> 构建会覆盖 `target/*.jar`，而 JVM 是懒加载类的，运行中的进程再去读被覆盖的 jar
> 会抛 `ClassNotFoundException: org.apache.catalina.connector.Request` 之类错误，
> 表现为端口仍在监听、但所有请求返回空响应（curl 报 52 Empty reply）。
> 正确做法是先停再构建：`./scripts/stop.sh && mvn -DskipTests package && ./scripts/start.sh`，
> 或直接用 `./scripts/restart.sh --build`。

## 默认账号

| 账号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `admin` | `Admin@123` | ADMIN | 超级管理员 |
| `dev` | `Dev@123` | OPERATOR | 演示账号 |
| `ops` | `Ops@123` | OPERATOR | 演示账号 |
| `viewer` | `View@123` | VIEWER | 只读演示账号 |

上述账号由 `AdminAccountInitializer` 在**用户表为空时**一次性写入（表里已有数据则跳过，不会覆盖）。

> 注意：账号种子由 user 服务启动时写入（`AdminAccountInitializer`），因此用 `scripts/init-db.sh`
> 初始化后用户表非空，**演示账号不会自动创建**——需要演示账号时可在「用户管理」页手工添加，
> 或清空 `tb_sys_user` 后重启 user 服务让其自动写入。

可在 `application.yml` 的 `gateway.default-admin` 中修改。

## SQL 目录规范

所有建表与种子脚本统一放在 `backend/sql/`，命名：`<模块名>-<功能名>-<主表名>.sql`，
各模块 pom 按自身前缀打进 jar 的 `db/` 目录，启动时由 `*SchemaInitializer` 自动执行（全部幂等）。
详见 [sql/README.md](sql/README.md)。

| 文件 | 内容 |
| --- | --- |
| `jv-api-gateway-user-auth-tb_sys_user.sql` | 平台账号表 |
| `jv-api-gateway-user-perm-tb_perm_group.sql` | 分组 / 成员 / 权限点字典 + 种子 |
| `jv-api-gateway-user-apikey-tb_gw_api_key.sql` | API Key |
| `jv-api-gateway-proxy-route-tb_gw_route.sql` | 网关转发路由 + 平台自身路由种子 |
| `jv-api-gateway-config-config-tb_sys_config.sql` | 平台配置 + 默认开关 |
| `jv-api-gateway-discovery-registry-tb_gw_service.sql` | 服务注册与接口清单 |
| `jv-api-gateway-trace-log-tb_gw_trace_log.sql` / `...-span-...` / `...-topology-...` | 调用日志 / span / 拓扑 |

## 认证与鉴权

详见 [docs/auth-design.md](../docs/auth-design.md)。两层鉴权：

### 用户令牌（Bearer JWT，控制台会话）

- 注册 / 登录成功后前端保存 `token`，后续请求统一带 `Authorization: Bearer <token>`
- 校验链路：签名 & 过期 → 账号存在且未禁用 → 令牌版本（`tb_sys_user.token_version`）
- 退出登录时 `token_version + 1`，该账号此前签发的所有令牌立即失效
- 连续输错 5 次锁定账号；开放注册默认 `VIEWER` 角色

### 网关统一鉴权（jv-api-gateway-proxy 的 AuthGlobalFilter）

所有经网关（28090）的请求在 `AuthGlobalFilter`（`proxy/auth` 包）裁决：

| 凭证 | 格式 | 校验内容 |
| --- | --- | --- |
| `Authorization: Bearer <jwt>` | 登录签发 | 验签 + 过期 → `token_version` 比对 → 账号状态 |
| `X-Api-Key: gwak-<keyId>-<secret>` | 「API Key」页签发 | keyId 定位 → SHA-256(secret) 比对 → 状态 / 有效期 |

- 通过后注入 `X-Auth-User-Id` / `X-Auth-Key-Id` 供下游识别调用方
- 未通过一律 `401 {"code":401,"message":"..."}`
- 白名单（`gateway.proxy.auth.skip-paths`）：`/api/auth/login`、`/api/auth/register`、`/actuator/**`；`enabled: false` 可整体关闭
- **热路径缓存**：Key 记录与令牌版本各缓存 30 秒（Caffeine），停用 / 轮换 Key、退出登录最迟 30 秒生效
- 容错策略：共享库不可用时放行并告警（保持网关可用性，与路由加载一致）
- 注意：proxy 不依赖 `jv-api-gateway-common`（其 starter-web 会让 Gateway 失效），JWT 验签由 `ProxyJwtParser` 自带实现，密钥与 user 服务一致（`gateway.jwt.secret`）

### 分组与权限

- 分组（`tb_perm_group`）：`type=ORG` 部门分组（树形，决定数据范围）/ `type=PERM` 权限分组（绑权限点）
- 权限点（`tb_perm_item`）：`平台 → 菜单 → 功能 → 按钮/展示/接口` 四级，`code` 为前后端共用鉴权标识
- 用户有效权限 = 所属全部分组权限点并集；内置分组（builtin=1，如 `ROLE_SUPER`）语义为「全部权限」
- 表结构与种子由 user 服务启动时执行 `db/perm_apikey.sql` 自动就绪（幂等），无需手工建表

### 开放认证（供其他平台对接）

`jv-api-gateway-user` 的 `/open/auth/*` 对外开放，网关白名单放行（免平台令牌），
总开关 `platform.open.auth.enabled`（系统设置页，10 秒内生效）：

| 接口 | 说明 |
| --- | --- |
| `POST /api/open/auth/register` | 注册（默认 VIEWER），成功直接返回令牌 |
| `POST /api/open/auth/login` | 账号密码登录，返回 token / userId / username / nickname / role |
| `POST /api/open/auth/token/verify` | 校验令牌（签名、过期、退出登录、账号禁用），返回 valid + permCodes |

返回的 token 与平台控制台令牌等价（同密钥、同有效期），可直接作为
`Authorization: Bearer` 调用网关其余受保护接口。文档页：前端「接入中心 → 开放认证」。

### 鉴权缓存：Redis 优先、MySQL 兜底

- **MySQL 始终是源数据**：`tb_gw_api_key`、`tb_sys_user.token_version` 是唯一事实来源
- **缓存副本**：Redis（`gw:auth:key:*` / `gw:auth:user:*`，TTL 30s）优先；Redis 不可用时自动回落本地 Caffeine（同样 30s）
- **模式可切换**：`platform.auth.cache.mode` = `auto`（默认）/ `redis` / `local`，系统设置页改动后 30 秒内生效
- **失效语义**：缓存 TTL 30 秒 —— 停用 / 轮换 Key、退出登录（token_version 自增）最迟 30 秒生效

### 接入 SDK（jv-api-gateway-client-common / client-starter）

- `client-common`：契约层，零依赖（请求头 / 上报路径常量、ServiceInstanceDTO / TraceLogDTO / SpanRecordDTO、ErrType）
- `client-starter`：Spring Boot Starter，引入即接入（`zimo.gateway.client.*` 可配置，可整体关闭）：
  - 启动注册 + 30s 心跳 + 停机下线；**注册失败不放弃**——心跳周期检测到未注册会自动补注册（幂等），启动顺序早于网关也能自愈
  - 心跳 / 注册失败打 WARN（不再静默 debug）
  - Servlet Filter 采集请求响应 + traceparent 透传；异步批量上报，队列满直接丢弃不阻塞业务
  - 仅支持 Servlet 容器；WebFlux（如 proxy 自身）走独立的 `ProxySelfRegistrar` + `TraceGlobalFilter`
- 上报通道在网关白名单放行（`/api/discovery/services/*`、`/api/trace/logs|spans/batch`），当前仅限内网；
  TODO：用 `X-Gateway-Client-Token` 共享密钥收紧

### 菜单健康探测

proxy 每 30 秒直连探测各服务端口（`GET /health/menu-services` 免鉴权返回快照）：

| 状态 | 含义 |
| --- | --- |
| `UP` 绿 | 可达且非 5xx（4xx 也算活着，如 401） |
| `DOWN` 灰 | 连接拒绝 / 超时 / 无响应（服务没起来或检查不到） |
| `ERROR` 红 | 可达但返回 5xx（进程在、运行有问题） |

前端侧边栏据此渲染状态点 + 悬浮信息（菜单名｜服务名｜状态），
开关 `platform.menu.health.enabled`（系统设置页，30 秒内生效）。

控制台数据自动刷新间隔 `platform.ui.refresh.interval`（INTEGER，默认 5，2~300，公开配置）：
网关总览 / 数据统计 / 服务发现三个页面按该秒数自动轮询，修改 30 秒内生效（前端配置缓存 30 秒）。

### API Key

- 明文 `gwak-<keyId>-<secret>` 仅签发 / 轮换响应返回一次，服务端只存 SHA-256
- 支持：签发（可选有效期）、启停、轮换（keyId 不变换新密钥）、**续期**（只延长有效期）、吊销（删除）
- **续期语义**：未过期在原到期时间上顺延；已过期从当前时间起算（续期后立即恢复可用）；永久 Key 拒绝续期（400）
- 每次变更写 `tb_gw_api_key_log`（CREATE / ENABLE / DISABLE / ROTATE / RENEW / DELETE），统计看板据此汇总今日动作
- Key 管理接口本身属于控制台接口，仅支持 Bearer JWT；API Key 用于调用**业务路由**

## 接口清单

### jv-api-gateway-user（28081）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册（默认 VIEWER），成功即登录 |
| POST | `/api/auth/login` | 登录，返回 token 与用户信息 |
| POST | `/api/auth/logout` | 退出登录 |
| GET | `/api/auth/info` | 当前登录用户信息 |
| GET | `/api/auth/my-perms` | 当前用户有效权限点（所属分组并集） |
| GET | `/api/perm/groups` | 分组全量（含成员数 / 权限点数） |
| POST | `/api/perm/groups` | 新建分组（ORG / PERM） |
| PUT | `/api/perm/groups/{id}` | 修改分组 |
| DELETE | `/api/perm/groups/{id}` | 删除分组（内置组拒绝） |
| GET/PUT | `/api/perm/groups/{id}/members` | 分组成员查询 / 全量替换 |
| GET/PUT | `/api/perm/groups/{id}/perms` | 分组权限点查询 / 全量替换 |
| GET | `/api/perm/dictionary` | 权限字典：平台菜单树 + 权限点全量 |
| POST | `/api/perm/items` | 新增权限点（code 唯一） |
| PUT | `/api/perm/items/{id}` | 修改权限点 |
| DELETE | `/api/perm/items/{id}` | 删除权限点 |
| GET | `/api/keys` | API Key 列表（不含密钥） |
| POST | `/api/keys` | 签发 API Key（明文仅此一次） |
| PUT | `/api/keys/{id}/status` | 启用 / 停用 |
| POST | `/api/keys/{id}/rotate` | 轮换密钥 |
| POST | `/api/keys/{id}/renew` | 续期（`{"renewDays": 30}`，1~3650 天） |
| DELETE | `/api/keys/{id}` | 吊销并删除 |
| GET | `/api/keys/stats` | 统计看板（总数 / 今日新增·轮换·续期·吊销·删除·启用 / 7 日内到期） |
| GET | `/api/keys/logs?limit=20` | 最近操作审计 |
| GET | `/api/users` | 用户分页列表 |
| GET | `/api/users/{id}` | 用户详情 |
| POST | `/api/users` | 新增用户 |
| PUT | `/api/users/{id}` | 修改用户 |
| DELETE | `/api/users/{id}` | 删除用户（逻辑删除） |
| PUT | `/api/users/{id}/status` | 启用 / 禁用 |
| POST | `/api/users/{id}/unlock` | 解锁账号登录（清除连续失败计数） |

### 统一时间区间（`range` 参数）

网关总览 / 数据统计 / 服务发现 / 服务拓扑四个页面共用同一套时间维度，
后端统一由 `com.zimo.gateway.common.util.TimeRange` 解析，前端由 `@zimo/shared` 的 `TIME_RANGES` 提供：

| code | 含义 | 窗口 | 趋势桶宽 |
| --- | --- | --- | --- |
| `1m` | 1 分钟 | 60 秒 | 1 秒/点 |
| `5m` | 5 分钟 | 300 秒 | 5 秒/点 |
| `10m` | 10 分钟 | 600 秒 | 10 秒/点 |
| `30m` | 30 分钟 | 1800 秒 | 30 秒/点 |
| `1h` | 1 小时 | 3600 秒 | 1 分钟/点 |
| `2h` | 2 小时 | 7200 秒 | 2 分钟/点 |
| `24h` | 24 小时（滚动） | 86400 秒 | 24 分钟/点 |
| `1d` | 1 天（自然日，今日 00:00 起） | 视当前时刻 | 24 分钟/点 |
| `7d` | 7 天 | 604800 秒 | 2.8 小时/点 |
| `30d` | 30 天 | 2592000 秒 | 12 小时/点 |

约定：

- 趋势图固定切 60 个点，桶宽 = 窗口 / 60，因此任何区间的时间轴密度一致
- 标签随跨度自适应：跨天带日期（`MM-dd HH:mm`）、≥7 天只到日（`MM-dd`）、桶宽 <60 秒带秒（`HH:mm:ss`）
- `24h` 与 `1d` 跨度相同但语义不同：前者滚动 24 小时，后者从自然日 00:00 起，两者图表结果不同
- 非法或缺失的 `range` 一律兜底 `24h`，不抛异常

### jv-api-gateway-overview（28082）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/overview/summary` | 核心指标汇总（含 `avgRt` / `p95` / `p99`，单位 ms），`?range=` 统一时间区间，默认 `24h` |
| GET | `/api/overview/trend` | QPS 与请求量趋势（一次查询返回两套序列：`values`=QPS、`requestValues`=请求量），`?range=` 统一时间区间；桶宽随区间自动推导（区间内固定 60 点） |
| GET | `/api/overview/runtime` | 运行时信息 |
| GET | `/api/overview/instances` | 已接入服务实例列表（`calls` / 平均耗时按 `?range=` 区间统计，QPS 恒为近 60 秒；按区间调用量降序取前 8） |
| GET | `/api/stats/filters` | 数据统计筛选项（应用 / 服务 / 接口 / 方法） |
| GET | `/api/stats/summary` | 数据统计聚合：指标卡（含 `avgRt` / `p95` / `p99`）+ 状态码/方法分布 + Top 服务/接口，支持 `appId`、`serviceName`、`api`、`method`、`range` 过滤 |
| GET | `/api/stats/trend` | 请求量趋势，`?range=` 与总览 QPS 趋势完全一致（固定 60 点），同样支持四个维度过滤；返回桶内请求量（总览返回 QPS） |

### jv-api-gateway-discovery（28083）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/discovery/services` | 服务分页列表（keyword / appId / **serviceName** / status / language 过滤；`?range=` 决定平均耗时与流量的聚合窗口；返回 `calls`（区间调用次数）与 `apiCount`（已登记接口数） |
| GET | `/api/discovery/trend` | 调用趋势：区间内请求量 + QPS 双序列（`requestValues` / `values`），支持 `?appId=` `?serviceName=` 过滤、`?range=` 统一时间区间 |
| GET | `/api/discovery/filters` | 筛选项字典：应用（appId+appName）、服务（appId+serviceName）、语言类型 |
| GET | `/api/discovery/services/{id}` | 服务详情 |
| POST | `/api/discovery/services/register` | 服务实例注册（自动接入） |
| POST | `/api/discovery/services/heartbeat` | 服务心跳 |
| DELETE | `/api/discovery/services/deregister` | 服务实例下线 |

转发路由（控制台「接入中心 → 路由配置」维护，改动后 proxy 最多 30 秒热加载生效）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/discovery/routes` | 路由列表（`?keyword=` 可搜索路径 / 应用 / 服务 / 目标） |
| POST | `/api/discovery/routes` | 新增路由（校验路径以 `/` 开头、唯一性） |
| PUT | `/api/discovery/routes/{id}` | 修改路由 |
| DELETE | `/api/discovery/routes/{id}` | 删除路由（逻辑删除） |
| POST | `/api/discovery/routes/{id}/toggle` | 启用 / 停用 |

### jv-api-gateway-trace（28084）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/trace/logs` | 调用日志分页列表（含 `req_params`；支持 `appId` / `service` / `method` / `api` 精确过滤，`params` 模糊检索 query / 请求体） |
| GET | `/api/trace/logs/{id}` | 日志详情（含 `req_params`、Header、请求体、响应体） |
| GET | `/api/trace/logs/filters` | 筛选项字典（**应用** / 协议 / 方法 / 服务名（带 appId，供级联）/ 接口名 / 报文格式，取库中真实出现过的值） |
| POST | `/api/trace/logs/batch` | 批量上报调用日志（接入 SDK 使用） |
| POST | `/api/trace/spans/batch` | 批量上报链路 span（接入 SDK / 网关使用） |
| GET | `/api/trace/traces/{traceId}` | 链路详情：按 traceId 取全量 span（前端组树画瀑布图） |
| GET | `/api/trace/topology` | 服务拓扑：默认**全局**（所有应用 × 所有服务的真实调用边）+ 节点；`?appId=` 按应用、`?service=` 按服务过滤；`?range=` 默认 24h，≤30 分钟直接对 span 表实时聚合，更长区间查 tb_gw_service_edge |
| GET | `/api/trace/slow` | 慢链路 TopN：按链路总耗时降序（`range` / `limit`），含入口服务与接口 |

## 构建与启动

```bash
# 全量构建
mvn -DskipTests package

# 分别启动（需先启动 MySQL / Nacos，或使用 optional 回落）
java -jar jv-api-gateway-user/target/jv-api-gateway-user.jar
java -jar jv-api-gateway-overview/target/jv-api-gateway-overview.jar
java -jar jv-api-gateway-discovery/target/jv-api-gateway-discovery.jar
java -jar jv-api-gateway-trace/target/jv-api-gateway-trace.jar
java -jar jv-api-gateway-proxy/target/jv-api-gateway-proxy.jar      # 流量入口，需最后启动
```

> 说明：环境 JDK 为 25，而 Spring Boot 3.2.12 自带的 Lombok 1.18.36 在 JDK 21+ 上会抛
> `TypeTag::UNKNOWN`，因此父 POM 已将 `lombok.version` 覆盖为 `1.18.46`，
> 并在父 POM 统一配置 `maven-compiler-plugin` 的 `annotationProcessorPaths`，供所有子模块继承。

## 编码规范

- **实体**：`XxxEntity`（`UserEntity` / `ApiKeyEntity` / `GwRouteEntity` / `SysConfigEntity`），与表一一对应，只放字段
- **数据访问**：`XxxMapper`（MyBatis 注解式，SQL 尽量简单，复杂统计走子查询一次取回）
- **入参 / 出参**：`XxxRequest` / `XxxVO`，Controller 不直接暴露实体
- **服务**：`XxxService` 接口 + `XxxServiceImpl` 实现
- **SQL**：`backend/sql/<模块>-<功能>-<主表>.sql`，幂等（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`）

## 平台配置的多来源存储

配置**元数据**（key、来源、平台、应用、服务、类型、备注）永远在 MySQL `tb_sys_config`，
**值**按 `config_source` 落到不同存储：

| 来源 | 存储 | source_ref | 实现 |
| --- | --- | --- | --- |
| `DB` | `tb_sys_config.config_value` | — | `DbConfigStore` |
| `REDIS` | Hash（field = 配置键） | Hash 键，默认 `gw:config:platform` | `RedisConfigStore`（`StringRedisTemplate`） |
| `NACOS` | properties 文本（dataId 级） | `dataId|groupName`，默认 `gw-platform-config.properties|DEFAULT_GROUP` | `NacosConfigStore`（v3 admin OpenAPI + token 缓存） |

- 写入单 key 到 Nacos 时先读整份 dataId 内容、替换目标行再整体发布，不会覆盖同文件其他配置
- 远端不可达 / key 被外部删除：接口返回 MySQL 快照并置 `valueState=STALE`，控制台提示"来源不可达 · 显示最后写入值"
- 连接参数：`spring.data.redis.*`、`gateway.config-store.nacos.*`（`NACOS_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` 可覆盖）
- 存量库补列由 `ConfigSchemaInitializer` 用 `DatabaseMetaData` 判断后 ALTER（MySQL 5.7 无 `ADD COLUMN IF NOT EXISTS`）
