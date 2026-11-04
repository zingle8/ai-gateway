# AI 网关平台 · 后端工程（com.zimo）

Spring Boot 3.2.12 + 原生 MyBatis + MySQL + Nacos，Maven 多模块，
按业务域拆分为 5 个可独立启动的微服务。

> 持久层统一使用 **原生 MyBatis**（`mybatis-spring-boot-starter` + XML SQL），
> 不使用 MyBatis-Plus，不依赖任何 ORM 分页插件。规范见「[持久层](#持久层)」。

## 模块划分

| 模块 | 说明 | 端口 | 包名 |
| --- | --- | --- | --- |
| `jv-gateway-common` | 平台内部公共模块：统一响应、异常、分页封装（`Page` / `PageResult`）、JWT、IP 工具、密码器配置 | — | `com.zimo.gateway.common` |
| `jv-gateway-client-common` | **对外公共模块**：上报模型、服务实例模型、常量、错误枚举（不依赖 Spring，业务系统可安全引入） | — | `com.zimo.gateway.client.common` |
| `jv-gateway-client-starter` | **网关子模块（Spring Boot Starter）**：自动接入注册、请求与响应采集、异步批量上报 | — | `com.zimo.gateway.client` |
| `jv-gateway-user` | 用户模块：登录 / 退出 / 当前用户、账号增删改查、鉴权拦截器、内置管理员初始化 | 28081 | `com.zimo.gateway.user` |
| `jv-gateway-overview` | 网关总览：指标汇总、QPS 趋势、运行时信息、网关实例 | 28082 | `com.zimo.gateway.overview` |
| `jv-gateway-discovery` | 服务发现：注册服务列表、服务详情（端口 / 实例数 / 注册时间）、心跳巡检、转发路由（`tb_gw_route`）增删改查 | 28083 | `com.zimo.gateway.discovery` |
| `jv-gateway-trace` | 日志追踪：调用日志分页查询、请求/响应报文详情 | 28084 | `com.zimo.gateway.trace` |
| `jv-gateway-proxy` | 流量代理：按 `tb_gw_route` 转发并记录调用详情（WebFlux，不依赖 common） | 28090 | `com.zimo.gateway.proxy` |

所有服务统一 `context-path: /api`，路径前缀与前端模块的 `/api/<service>` 一一对应。

### 流量入口

前端所有 `/api` 请求（含控制台自身的登录、列表）统一打 **`jv-gateway-proxy`（28090）**，
再由 `tb_gw_route` 转发到后端各微服务：

```
浏览器 / 外部调用方
      ↓  /api/**
jv-gateway-proxy :28090   按 tb_gw_route 匹配 path_prefix → upstream_url
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
| user / overview / discovery / trace | 引入 `jv-gateway-client-starter`，`zimo.gateway.client.*` 配置后自动注册 + 心跳（30 秒） |
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
| `jv-gateway-client-common` | 公共模块：DTO / 常量 / 枚举，供所有项目复用，避免重复定义 | 无 Spring 依赖 |
| `jv-gateway-client-starter` | 网关子模块：自动装配，零编码完成接入与采集 | Spring Boot 3 |

- 业务系统只引 `jv-gateway-client-starter` 即可；它传递依赖 `jv-gateway-client-common`。
- 只想要模型（例如自己实现上报逻辑）时，单独引 `jv-gateway-client-common`。
- 网关平台本身也是微服务，其他项目通过 `GatewayPlatformClient` 调用平台能力。

详见 [../docs/client-integration.md](../docs/client-integration.md)。

## 命名规范

- Maven groupId：`com.zimo`
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
| `jv-gateway-user` / `jv-gateway-discovery` / `jv-gateway-trace` | `*Mapper` + XML | 增量维护 |
| `jv-gateway-overview` | `OverviewMapper` + `OverviewMapper.xml` | 总览、数据统计、趋势的所有聚合 SQL |
| `jv-gateway-proxy` | `RouteMapper` / `TraceLogMapper` + XML | WebFlux 下由 `TraceSink` 每秒批量 INSERT，SqlSession 随调用开启关闭，不跨线程 |

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
- 建表与示例数据：`sql/api_gateway.sql`

主要表：

- `tb_sys_user`：平台账号（密码 BCrypt 存储）
- `tb_gw_service`：注册服务（含 `port`、`instances`、`register_time`、`last_heartbeat_time` 等）
- `tb_gw_route`：转发路由（`path_prefix` → `upstream_url`，proxy 每 30 秒热加载）
- `tb_gw_api`：网关接口（接口个数统计）
- `tb_gw_trace_log`：网关调用日志（含 `req_params`、`req_header`、`req_body`、`res_header`、`res_body`、`err_type` 等）
  - `req_params`：URL 上的原始 query string；表单（`x-www-form-urlencoded`）参数原文在 `req_body`
  - `req_bytes` / `res_bytes`：真实流经字节数（不再用截断后字符数）

```bash
mysql -h127.0.0.1 -P3306 -uroot -p'Root@123456' < sql/api_gateway.sql
```

## Redis

`jv-gateway-user` 使用 Redis 保存**登录失败计数**，使连续失败锁定在多实例部署下同样生效。

配置（`jv-gateway-user/src/main/resources/application.yml`，均可用环境变量覆盖）：

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
  - `optional:nacos:api-jv-gateway-common.yaml`
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
./scripts/logs.sh jv-gateway-user       # 只跟踪指定服务
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

> 注意：`sql/api_gateway.sql` 的种子数据本身已包含 `admin`，因此用 `scripts/init-db.sh`
> 初始化后用户表非空，**演示账号不会自动创建**——需要演示账号时可在「用户管理」页手工添加，
> 或清空 `tb_sys_user` 后重启 user 服务让其自动写入。

可在 `application.yml` 的 `gateway.default-admin` 中修改。

## 认证与鉴权

采用 **JWT（无状态 Bearer Token）**，详见 [docs/auth-design.md](../docs/auth-design.md)。

- 登录成功后前端保存 `token`，后续请求统一带 `Authorization: Bearer <token>`
- 拦截器校验链路：签名 & 过期 → 账号存在且未禁用 → 令牌版本（`tb_sys_user.token_version`）
- 退出登录时 `token_version + 1`，该账号此前签发的所有令牌立即失效
- 连续输错 5 次锁定账号
- `/api/auth/logout` 需携带有效令牌；`/api/auth/login` 为唯一白名单接口

## 接口清单

### jv-gateway-user（28081）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回 token 与用户信息 |
| POST | `/api/auth/logout` | 退出登录 |
| GET | `/api/auth/info` | 当前登录用户信息 |
| GET | `/api/users` | 用户分页列表 |
| GET | `/api/users/{id}` | 用户详情 |
| POST | `/api/users` | 新增用户 |
| PUT | `/api/users/{id}` | 修改用户 |
| DELETE | `/api/users/{id}` | 删除用户（逻辑删除） |
| PUT | `/api/users/{id}/status` | 启用 / 禁用 |
| POST | `/api/users/{id}/unlock` | 解锁账号登录（清除连续失败计数） |

### jv-gateway-overview（28082）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/overview/summary` | 核心指标汇总（含 `avgRt` / `p95` / `p99`，均为今日调用，单位 ms） |
| GET | `/api/overview/trend` | QPS 趋势，`?step=` 粒度（分钟，可选 `1/5/10/30/60/120/1440`，默认 1，窗口固定 120 点） |
| GET | `/api/overview/runtime` | 运行时信息 |
| GET | `/api/overview/instances` | 网关实例列表 |
| GET | `/api/stats/filters` | 数据统计筛选项（应用 / 服务 / 接口 / 方法） |
| GET | `/api/stats/summary` | 数据统计聚合：指标卡（含 `avgRt` / `p95` / `p99`）+ 状态码/方法分布 + Top 服务/接口，支持 `appId`、`serviceName`、`api`、`method`、`hours`（1~24）过滤 |
| GET | `/api/stats/trend` | 请求量趋势，`?step=` 粒度与总览 QPS 趋势完全一致（7 档，固定 120 点），同样支持四个维度过滤；返回桶内请求量（总览返回 QPS） |

### jv-gateway-discovery（28083）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/discovery/services` | 服务分页列表（keyword / appId / **serviceName** / status / language 过滤） |
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

### jv-gateway-trace（28084）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/trace/logs` | 调用日志分页列表（含 `req_params`；支持 `appId` / `service` / `method` / `api` 精确过滤，`params` 模糊检索 query / 请求体） |
| GET | `/api/trace/logs/{id}` | 日志详情（含 `req_params`、Header、请求体、响应体） |
| GET | `/api/trace/logs/filters` | 筛选项字典（**应用** / 协议 / 方法 / 服务名（带 appId，供级联）/ 接口名 / 报文格式，取库中真实出现过的值） |
| POST | `/api/trace/logs/batch` | 批量上报调用日志（接入 SDK 使用） |

## 构建与启动

```bash
# 全量构建
mvn -DskipTests package

# 分别启动（需先启动 MySQL / Nacos，或使用 optional 回落）
java -jar jv-gateway-user/target/jv-gateway-user.jar
java -jar jv-gateway-overview/target/jv-gateway-overview.jar
java -jar jv-gateway-discovery/target/jv-gateway-discovery.jar
java -jar jv-gateway-trace/target/jv-gateway-trace.jar
java -jar jv-gateway-proxy/target/jv-gateway-proxy.jar      # 流量入口，需最后启动
```

> 说明：环境 JDK 为 25，而 Spring Boot 3.2.12 自带的 Lombok 1.18.36 在 JDK 21+ 上会抛
> `TypeTag::UNKNOWN`，因此父 POM 已将 `lombok.version` 覆盖为 `1.18.46`，
> 并在父 POM 统一配置 `maven-compiler-plugin` 的 `annotationProcessorPaths`，供所有子模块继承。
