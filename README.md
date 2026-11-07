# API 网关平台（api-gateway）

**一句话**：开箱即用的「API 网关 + 运营控制台」一体化平台——业务系统以最小成本接入统一网关，
团队在控制台完成路由、账号、调用监控的全部运营工作。

> 本平台由 zengguiwan 使用 CodeBuddy 独自开发完成。

## 这个平台是干嘛的

面向「有多个业务系统 / 微服务，希望统一管理流量入口」的团队，解决四类问题：

| 痛点 | 平台方案 | 控制台入口 |
| --- | --- | --- |
| 流量入口分散，每个服务各开端口对外 | 统一网关转发，路由表 30 秒热生效（改规则免重启） | 接入中心 · 路由配置 |
| 出了问题不知道谁在调谁、慢在哪一跳 | 网关转发旁路自动采集报文 / Header / 耗时，跨服务串链路 | 可观测 · 日志追踪 / 服务拓扑 |
| 服务上下线状态不透明，靠人肉 ping | SDK 自动注册 + 30 秒心跳，90 秒无心跳判离线 | 服务发现 |
| 调用量、错误率、流量没有报表 | 请求量 / QPS / 入站出站流量趋势、方法与状态码分布、Top 服务与接口 | 数据统计 / 网关总览 |

配套的运营底座：JWT 登录认证、分组权限（permCodes 权限点）、API Key 签发 / 轮换 / 吊销（带审计）、
系统配置中心（DB / Redis / Nacos 多来源）。

**典型使用流程**

1. 运维用 scripts 一键起平台（见[快速开始](#快速开始)）；
2. 管理员在「路由配置」登记业务系统转发规则；业务系统按需接入 SDK（见[接入与配置](#接入与配置)）——注册、心跳、调用日志上报全自动；
3. 团队日常在控制台看总览与统计、查调用链、管 API Key；
4. 需要裁剪交付时按模块组装（见 [docs/modules-deployment.md](docs/modules-deployment.md)）。

```
浏览器 ──> 控制台前端 (Vite, 28080)
                │ /api 反向代理
                ▼
         jv-api-gateway-proxy (Spring Cloud Gateway, 28090)
                │ 按 tb_gw_route 匹配转发，旁路采集调用日志
                ▼
 ┌──────────────┬────────────────┬────────────────┬──────────────┐
 │ user 28081   │ overview 28082 │ discovery 28083│ trace 28084  │
 │ 账号/权限    │ 总览/统计      │ 服务/路由/API  │ 日志追踪     │
 └──────────────┴────────────────┴────────────────┴──────────────┘
        │               │                │
   MySQL 3306      Nacos 8848      Redis 6379
   （业务数据）  （服务发现/配置，可选）（登录限流/账号解锁，可选）
```

业务系统也可作为**客户端**接入：引入 `jv-api-gateway-client-starter` 自动注册实例并上报调用日志，
或使用 Python SDK（Flask / FastAPI / 裸 WSGI、ASGI），详见 [文档索引](#文档索引)。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | JDK 17 · Spring Boot 3.2.12 · Spring Cloud 2023.0.3（Gateway / Nacos）· **原生 MyBatis**（Mapper + XML，无 MyBatis-Plus / JPA / JdbcTemplate）· jjwt |
| 前端 | Vue 3.5 · Vite 5 · Element Plus 2.8 · Pinia · Vue Router（npm workspaces monorepo，模块经 Vite alias 编译期装配） |
| 存储 | MySQL 5.7（唯一权威数据源）· Redis 7（限流 / 账号解锁，不可用时自动降级）· Nacos 2（注册发现 / 配置，optional 引入，挂了不影响启动） |
| SDK | Java（client-common / client-starter，Spring Boot 3 自动装配）· Python（Flask / FastAPI / WSGI / ASGI） |

环境要求：**JDK 17+**、Maven 3.8+、**Node.js ≥ 20**、Docker（用于拉起 MySQL / Nacos / Redis 容器）、mysql 客户端（初始化数据库用）。

## 目录结构

```
api-gateway/
├── backend/                  # Maven 多模块后端（8 个模块，5 个可启动）
│   ├── jv-api-gateway-common/        # R 响应体、分页、全局异常、JWT 工具
│   ├── jv-api-gateway-user/          # 28081 账号、角色、登录（JWT）、Redis 限流
│   ├── jv-api-gateway-overview/      # 28082 网关总览、数据统计（聚合 SQL 全部走 MyBatis）
│   ├── jv-api-gateway-discovery/     # 28083 服务实例、路由配置、API 清单
│   ├── jv-api-gateway-trace/         # 28084 调用日志分页 / 详情 / 字典
│   ├── jv-api-gateway-proxy/         # 28090 网关转发 + 调用日志旁路采集（WebFlux）
│   ├── jv-api-gateway-client-common/ # SDK 公共模型（纯 POJO，可复用）
│   ├── jv-api-gateway-client-starter/# SDK Spring Boot Starter（自动注册 + 采集上报）
│   └── sql/                      # 全部建表与种子脚本（模块-功能-主表 命名，见 sql/README.md）
├── frontend/                 # npm workspaces：portal 主应用 + @zimo/module-* 业务模块
│   ├── apps/portal/              # 主应用（Vite 28080，/api 代理到 28090 网关）
│   ├── modules/{user,overview,discovery,trace,stats}/   # 每模块 = 菜单 + 路由 + api.js + views
│   └── packages/ui/              # 侧边栏 / 布局等公共组件
├── docs/                     # 专题文档（项目计划、认证设计、SDK 接入、网关转发）
├── scripts/                  # 全部运维脚本（见下表）
└── sdk/python/               # Python SDK（jv_gateway 包）
```

## 快速开始

```bash
# 0. 首次准备：安装脚本执行权限
chmod +x scripts/*.sh

# 1. 拉起依赖中间件（幂等：容器存在则 start，不存在则按默认参数创建）
./scripts/init-env.sh

# 2. 初始化数据库（建库 api_gateway + 建表 + 演示数据，仅需一次）
./scripts/init-db.sh

# 3. 一键启动：自动构建 jar（缺失时）→ 依次拉起 5 个后端服务 → 启动前端
./scripts/start.sh

# 4. 打开控制台，使用默认账号登录
#    http://127.0.0.1:28080   admin / Admin@123

# 5. 验证：六个后端服务状态应全部 up
./scripts/status.sh
```

说明：

- **启动顺序**：start.sh 内部已按依赖编排（MySQL 可达性检查 → user/overview/discovery/trace/config → proxy → 前端），
  单个业务服务启动失败不影响其他服务，可用 `./scripts/start.sh backend --build` 重试；
- **日常使用**：启停用 `./scripts/start.sh` / `./scripts/stop.sh`；改了代码用 `./scripts/restart.sh backend --build`；
- **看日志**：`./scripts/logs.sh jv-api-gateway-proxy -n 200`（其余服务同理）；
- **默认已配置好**：数据库种子含 11 条转发路由（控制台各页面走网关互通）与 3 个演示服务，
  启动后无需任何手工配置即可浏览全部已上线页面。

## 运维脚本

| 脚本 | 说明 |
| --- | --- |
| `scripts/init-env.sh` | 准备依赖中间件容器（MySQL / Nacos / Redis），幂等；`--check` 只检查 |
| `scripts/init-db.sh` | 初始化数据库（建库 + 建表 + 种子数据）；`--drop` 删库重建 |
| `scripts/start.sh` | 一键启动（`all` / `backend` / `frontend`，`--build` 强制重新构建；MySQL 不可达会自动尝试拉起容器） |
| `scripts/stop.sh` | 一键关闭（先按 PID 优雅停止，再按端口兜底） |
| `scripts/restart.sh` | 重启（= stop + start，参数同 start） |
| `scripts/status.sh` | 各服务运行状态一览 |
| `scripts/logs.sh` | 查看 / 跟踪日志（`logs.sh` 跟踪全部，`logs.sh jv-api-gateway-proxy -n 200` 看单个服务） |
| `scripts/build-sdk-jar.sh` | 构建 Java SDK 分发包到 `dist/sdk-jar/`（含 install.sh） |

端口被占用且无本脚本 PID 记录时，脚本会跳过并提示排查命令，不会强杀其他进程。
日志与 PID 统一在 `logs/` 目录。

## 端口与服务

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| 控制台前端 | 28080 | Vite dev server，`/api` 全部代理到 28090 |
| jv-api-gateway-proxy | 28090 | 流量入口：按路由表转发 + 采集调用日志 |
| jv-api-gateway-user | 28081 | 账号 / 角色 / 登录认证 |
| jv-api-gateway-overview | 28082 | 网关总览 / 数据统计 |
| jv-api-gateway-discovery | 28083 | 服务实例 / 路由配置 / API 清单 |
| jv-api-gateway-trace | 28084 | 调用日志查询与详情 |
| jv-api-gateway-config | 28085 | 系统配置管理（DB/Redis/Nacos 多来源托管） |

后端各服务统一 context-path `/api`；前端所有 `/api` 请求先到 proxy(28090)，
再按 `tb_gw_route` 表二次分发到各微服务 —— 即前端联调依赖 proxy 与路由表数据（种子数据已含）。

## 平台组成与子模块职责

平台 = 1 个流量入口（proxy）+ 5 个业务微服务 + 三端 SDK。所有 `/api` 请求统一走 proxy，
按 `tb_gw_route` 转发，转发时旁路采集调用日志（不经手业务逻辑）。

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| jv-api-gateway-proxy | 28090 | **网关流量入口**：按路由表转发；`AuthGlobalFilter` 统一鉴权（token 验签 + Redis/MySQL 双层缓存 + 豁免控制）；`TraceGlobalFilter` 双端采集调用日志；DB 动态路由每 30 秒热刷新（改规则免重启）；侧边栏菜单健康探测（直连各模块业务接口） |
| jv-api-gateway-user | 28081 | **账号与安全**：登录认证（JWT）、用户管理、分组与权限（permCodes 权限点下发）、API Key 全生命周期（签发/轮换/续期/吊销 + 审计）、开放认证（register / login / token verify） |
| jv-api-gateway-overview | 28082 | **运营数据**：网关总览指标卡与运行时指标；数据统计（请求量 / QPS / 入站出站流量趋势、方法与状态码分布、Top 服务 / Top 接口），趋势查询一次聚合三套序列 |
| jv-api-gateway-discovery | 28083 | **注册与路由**：服务注册表（SDK 注册 / 心跳 / 90 秒判离线巡检）、转发路由 CRUD（tb_gw_route）、API 清单（tb_gw_api） |
| jv-api-gateway-trace | 28084 | **可观测查询**：调用链日志分页 / 报文详情、服务拓扑（调用边预聚合）、慢链路 TopN |
| jv-api-gateway-config | 28085 | **配置中心**：tb_sys_config 系统配置，支持 DB / Redis / Nacos 多来源托管，提供无需登录的公开配置接口（如自动刷新频率、健康标识开关） |
| jv-api-gateway-common | — | 公共库：R 响应体、分页、全局异常、JWT 工具、趋势桶对齐（TrendBuckets） |
| SDK（Java starter / Python / Node.js） | — | 业务系统接入：自动注册实例 + 心跳 + 调用日志上报；Java 走 Spring Boot Starter 自动装配，Python 支持 Flask / FastAPI / WSGI / ASGI，Node 零依赖带 Express 示例 |

## 控制台菜单（8 大模块 21 项）

登录后左侧菜单按模块分组。悬浮任意菜单可查看「后端服务 ｜ 运行状态（延迟）｜ 功能上线标记」，
绿点 / 灰点实时反映对应微服务健康状态（每 30 秒刷新）。

| 模块 | 菜单 | 路径 | 页面内容 | 状态 |
| --- | --- | --- | --- | --- |
| 网关总览 | 网关总览 | `/overview` | 指标卡、请求量 / QPS / 流量三张趋势折线图、运行时指标 | ✅ |
| 数据统计 | 数据统计 | `/stats` | 三趋势 + 方法 / 状态码分布 + Top 服务 / Top 接口（含归属应用与服务） | ✅ |
| 服务发现 | 服务发现 | `/discovery` | 注册表列表（健康 / 离线 / 异常三态）、应用与服务双下拉过滤 | ✅ |
| 可观测 | 日志追踪 | `/trace` | 跨服务调用链检索、报文详情 | ✅ |
| 可观测 | 服务拓扑 | `/topology` | 服务调用关系图、慢链路 TopN | ✅ |
| 接入中心 | 系统接入 | `/system-onboard` | 接入方式说明（文档向导） | 静态页 |
| 接入中心 | 自动接入 | `/auto-onboard` | 已注册实例列表（SDK 自动注册） | ✅ |
| 接入中心 | 路由配置 | `/route-config` | 转发规则 CRUD，30 秒内网关热生效 | ✅ |
| 接入中心 | 上线发布 | `/go-live` | 灰度与正式发布（规划中） | 占位 |
| 接入中心 | 开放认证 | `/open-auth` | 免令牌开放接口的调用方约束（规划中） | 静态页 |
| 认证与安全 | 用户管理 | `/auth/user` | 平台账号 CRUD、启停 | ✅ |
| 认证与安全 | API Key | `/auth/api-keys` | Key 签发 / 启停 / 轮换 / 续期 / 吊销 + 审计看板 | ✅ |
| 认证与安全 | 登录方式 | `/auth/login-methods` | 密码 / 验证码 / 扫码 / SSO 因子开关 | 设计稿 |
| 认证与安全 | 单点登录 | `/auth/sso` | OIDC / SAML Provider 配置 | 设计稿 |
| 认证与安全 | 令牌与会话 | `/auth/sessions` | 在线会话管理、强制下线 | 设计稿 |
| 认证与安全 | 安全策略 | `/auth/policy` | MFA 范围、TOTP 偏移、step-up 开关 | 设计稿 |
| 分组权限 | 分组管理 | `/perm/group` | 权限分组与成员、permCodes 维护 | ✅ |
| 分组权限 | 部门组织 | `/perm/org` | 部门树维护 | ✅ |
| 分组权限 | 平台功能与菜单 | `/perm/menu` | 菜单与功能点登记 | ✅ |
| 系统管理 | 角色管理 | `/system/role` | 角色定义（规划中，暂由分组权限承担） | 占位 |
| 系统管理 | 系统设置 | `/system/settings` | 系统配置项（刷新频率、健康标识开关等） | ✅ |

> ✅ = 已上线（有真实后端交互，共 13 项）；设计稿 / 占位 / 静态页 = 未上线（共 8 项，接真计划见 [开发计划](docs/DEVELOPMENT-PLAN.md)）。

## 接入与配置

### 接入架构总览

业务流量与数据上报走同一条网关入口，但方向相反：**① 业务请求进来被转发，②③ SDK 把实例状态与调用日志报回来**，
两边的数据最终都落在 MySQL，由控制台各页面消费。

```
   调用方（App / 前端 / 第三方系统）
        │
        │ ① 业务请求（带 token，网关鉴权后按 tb_gw_route 转发）
        ▼
┌───────────────────────────────────────────────────────────┐
│              jv-api-gateway-proxy 网关 (28090)             │
│    AuthGlobalFilter 统一鉴权 ｜ TraceGlobalFilter 转发侧采集 │
└────────┬──────────────────────────────────┬───────────────┘
         │ ① 转发                            │ ②③ SDK 上报通道
         ▼                                  │    （网关白名单放行 + 共享密钥校验）
┌───────────────────────────┐               ▼
│      业务系统 ×N           │   ┌────────────────────────────────────┐
│  Java / Python / Node.js  │   │      平台微服务（28081~28085）       │
│  （内嵌 SDK，自动接入）      │──►│  discovery：注册表 + 心跳巡检        │
│  · 启动注册 + 30s 心跳      │ ② │  trace：调用日志 / span 落库         │
│  · 内部调用旁路采集         │ ③ │  user / config / overview：运营数据  │
└───────────────────────────┘   └───────────────┬────────────────────┘
                                                │ 统一落库
                                                ▼
                                     MySQL api_gateway
                              （tb_gw_service / tb_gw_trace_log / …）
                                                ▲
┌───────────────────────────────────────────────┴────────────┐
│  控制台前端 (28080) ── /api ──► proxy ──► 各微服务读库展示     │
│  服务发现 · 日志追踪 · 服务拓扑 · 数据统计 即消费以上数据        │
└────────────────────────────────────────────────────────────┘
```

| 线路 | 方向 | 内容 |
| --- | --- | --- |
| ① 业务流量 | 调用方 → 网关 → 业务系统 | 鉴权 → 转发（网关侧旁路采集，免业务系统改造） |
| ② 实例状态 | 业务系统 SDK → 网关 → discovery | 注册 + 心跳（控制台「服务发现」页的数据源） |
| ③ 调用数据 | 业务系统 SDK → 网关 → trace | 系统内部调用的日志 / span 批量上报（与网关侧采集按 traceId 串联） |

### 接入与采集上报机制（SDK 内部做了什么）

业务系统引入 SDK 后**零代码侵入**：注册、心跳、采集、上报全部由 SDK 在后台自动完成，
控制台与服务发现页看到的数据都来自这条链路。

```
业务系统（引入 SDK，配置三要素：server-addr / app-id / service-id）
   │ 启动
   ├─ POST /api/discovery/services/register    注册实例（按 serviceId 幂等，状态=健康）
   ├─ POST /api/discovery/services/heartbeat   每 30s 心跳，刷新 last_heartbeat_time
   ├─ POST /api/trace/logs/batch               调用日志批量上报（异步，失败不阻塞业务）
   └─ POST /api/trace/spans/batch              调用链 span 批量上报
        │  全部打向网关 28090，网关白名单放行上报通道（不带登录 token）
        ▼
   proxy 校验上报共享密钥（X-Gateway-Client-Token，防伪造注册/上报，失败 401）
        ▼
   discovery 落库 tb_gw_service ｜ trace 落库 tb_gw_trace_log / tb_gw_trace_span
        ▼
   HeartbeatMonitor 每 60s 巡检 → 超 90s（3 个心跳周期）无心跳 → 标记「离线」
```

| 机制 | 说明 |
| --- | --- |
| 注册 | 启动时调用 register，按 serviceId 幂等（重复注册只更新）；注册失败后心跳周期自动补注册（启动顺序敏感场景自愈） |
| 心跳判活 | 每 30s 一次；90s 无心跳判「离线」，心跳恢复即自动回「健康」；优雅停机走 deregister 标「异常」 |
| 采集 | SDK 侧请求旁路采集 req/res 报文、Header、耗时、出入站字节数，异步批量上报，不影响业务线程 |
| 链路串联 | `X-Gateway-Trace-Id` / `traceparent` 跨服务透传，网关侧与业务侧日志串成一条调用链 |
| 安全双门 | ① 上报共享密钥（伪造注册/上报直接 401）；② SDK 入口鉴权（回调 `/open/auth/token/verify` 验签 + 缓存，豁免路径 / fail-open 可配） |

> 网关自己的转发侧采集（`TraceGlobalFilter`）与 SDK 采集是两套入口、同一张表：
> 经过网关的流量由网关记，SDK 只补业务系统内部调用，两边按 traceId 串联。

### 业务系统接入（三端 SDK 任选其一）

| 步骤 | Java（Spring Boot 3） | Python | Node.js |
| --- | --- | --- | --- |
| 1. 引入 SDK | Maven 依赖 `jv-api-gateway-client-starter`（自动装配） | `sdk/python`（jv_gateway 包，Flask / FastAPI / WSGI / ASGI） | `sdk/nodejs`（jv-gateway，零第三方依赖，附 Express 示例） |
| 2. 写配置 | yml 三要素：`server-addr`（网关 28090）、`app-id`、`service-id` + 中文名 | 同左（配置对象） | 同左（普通对象） |
| 3. 启动即接入 | 自动注册实例 → 30 秒心跳 → 请求自动采集上报（免代码侵入） | 同左 | 同左 |

- 详细步骤、全部参数（豁免路径、上报开关、fail-open 等）→ [docs/client-integration.md](docs/client-integration.md) / [sdk/python/README.md](sdk/python/README.md) / [sdk/nodejs/README.md](sdk/nodejs/README.md)
- 依赖从哪拿 → 私服 [docs/maven-nexus-deployment.md](docs/maven-nexus-deployment.md)（Nexus 一键部署），或免私服的 file:// / 云制品库方案；
- **不想接 SDK 也可以**：只在「路由配置」页登记转发规则，网关即可直接转发（代价：服务发现页看不到实例状态，统计页无调用归属）。

### 配置看哪里

| 想改什么 | 去哪里 |
| --- | --- |
| 数据库 / Redis / Nacos 连接、JWT 密钥、端口 | 各服务 `application.yml` 或环境变量，全量账号密码与端口清单见 [docs/configuration.md](docs/configuration.md) |
| 平台运行时开关（页面刷新频率、菜单健康标识开关等） | 控制台「系统设置」页（落 `tb_sys_config`，改值 30 秒内生效，无需重启） |
| 转发路由 | 控制台「路由配置」页（30 秒热生效） |
| SDK 端参数 | 各语言接入文档（心跳间隔、采集开关、鉴权豁免、fail-open） |
| 账号与权限 | 控制台「用户管理」「分组管理」页 |

## 默认账号

| 账号 | 密码 | 角色 | 来源 |
| --- | --- | --- | --- |
| `admin` | `Admin@123` | ADMIN | SQL 种子数据；或用户表为空时由启动初始化器创建（用户名/密码可用 `gateway.default-admin.*` 配置覆盖） |
| `dev` / `ops` / `viewer` | `Dev@123` / `Ops@123` / `View@123` | OPERATOR / VIEWER | 仅当**用户表为空**时由 `AdminAccountInitializer` 创建；用 `init-db.sh` 初始化（种子已含 admin）后不会自动创建，可在「用户管理」页手工添加 |

生产环境请立即修改 admin 密码，并通过环境变量覆盖 JWT 密钥与数据库口令。

## 开发计划与进度

当前进度：**v0.1 已完成**（2026-11-04 ~ 11-06，参与人 zengguiwan），后续版本已排入计划、时间点待定。

| 版本 | 状态 | 周期 | 内容概要 |
| --- | --- | --- | --- |
| **v0.1** | ✅ 已完成 | 2026-11-04 ~ 11-06 | 10 大块 32 项功能（网关核心 / 微服务 / 三端 SDK / 服务发现 / 可观测 / 总览 / 认证安全 / 配置中心 / 控制台 / 交付运维）+ 修复 9 项 Bug；21 个菜单上线 13 个 |
| **v0.2** | 📋 计划中 | 待定 | P0 缺口 8 项 + 增值项，共 15 项：网关限流、会话接真、安全策略 / 登录方式接真、鉴权精细豁免、开放认证约束（含 Key 作用域）、菜单权限过滤 + 账号平台授权、变更审计、列表导出、主机运行指标上报、部署关联统计、拓扑双视图、鉴权风控、日志打印管控、单点登录（SSO）、前端微前端化（模块独立部署） |
| **v0.3** | 📋 计划中 | 待定 | P1 7 项：调用量报表、发现控制台增强、追踪前端深化、告警体系、接入向导、角色收敛、统计导出 |
| **v0.4+** | 📋 长期规划 | 待定 | P2 6 项：灰度发布、多租户、Prometheus、KMS 密钥托管、测试与 CI/CD、正式多语言 SDK |

- **各版本功能与 Bug 明细** → [docs/DEVELOPMENT-PLAN.md](docs/DEVELOPMENT-PLAN.md)（含每项的技术要点与实现说明）
- **逐日变更与架构决策（ADR）** → [docs/PLAN.md](docs/PLAN.md)
- **未上线页面**：菜单表中标注「设计稿 / 占位 / 静态页」的 8 项，接真优先级见 v0.2 / v0.3 清单

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [backend/README.md](backend/README.md) | 后端权威文档：模块划分、命名规范、MyBatis 持久层约定、数据库/Redis/Nacos、接口清单、认证鉴权设计 |
| [frontend/README.md](frontend/README.md) | 前端权威文档：monorepo 结构、模块约定（新增页面三件套）、UI 规范、常用命令 |
| **计划与进度** | |
| [docs/DEVELOPMENT-PLAN.md](docs/DEVELOPMENT-PLAN.md) | **开发计划与进度计划**：版本总览表（v0.1 已完成 / v0.2~v0.4+ 计划）、各版本功能与 Bug 明细清单、时间点与参与人 |
| [docs/PLAN.md](docs/PLAN.md) | **项目计划与决策记录**：架构决策（ADR，auth/authz/config 为什么不拆/为什么拆）、逐日变更记录、未完成清单（P0/P1/P2） |
| **部署与运维** | |
| [docs/modules-deployment.md](docs/modules-deployment.md) | 服务拆解与按需启动方案：模块依赖矩阵、套餐组装、交付检查清单 |
| [docs/maven-nexus-deployment.md](docs/maven-nexus-deployment.md) | Maven 私服部署指南：Nexus 一键部署 / 发布 / 业务方接入 |
| [docs/configuration.md](docs/configuration.md) | **配置信息一览**：数据库/Redis/Nacos 账号密码与端口、JWT 密钥、环境变量清单 |
| **设计与接入** | |
| [docs/auth-design.md](docs/auth-design.md) | 认证与鉴权：JWT、登录限流、账号解锁 |
| [docs/gateway-proxy.md](docs/gateway-proxy.md) | 网关转发与调用日志采集链路 |
| [docs/client-integration.md](docs/client-integration.md) | Java SDK 接入指南（自动注册 + 日志采集） |
| [sdk/python/README.md](sdk/python/README.md) | Python SDK 接入指南（Flask / FastAPI / WSGI / ASGI） |
| [sdk/nodejs/README.md](sdk/nodejs/README.md) | Node.js SDK 接入指南（Express 示例） |

## 常见问题

- **MySQL 起不来**：`./scripts/init-env.sh mysql` 会自动 `docker start mysql57`（容器不存在则创建）；也可以用本机 brew 安装的 MySQL，脚本按端口探测，不强依赖容器。
- **Nacos 挂了影响启动吗**：不影响。后端以 `optional:` 引入 Nacos 配置，只失去动态配置；服务注册同样可选。
- **Redis 挂了影响登录吗**：不影响。登录限流自动降级为单实例内存计数，日志出现 `降级为内存计数`。
- **演示账号 dev 登不上**：见「默认账号」——SQL 种子只含 admin，演示账号仅在用户表为空时自动创建。
- **前端页面 401 / 502**：确认 proxy(28090) 在运行且 `tb_gw_route` 有对应路由（`./scripts/status.sh`；路由在「路由配置」页维护，30 秒内自动刷新）。
- **端口冲突**：`lsof -i :<port>` 找到占用进程，或 `./scripts/stop.sh` 后重启。
