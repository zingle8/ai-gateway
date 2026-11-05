# AI 网关平台（ai-gateway）

统一接入网关 + 运营控制台的一体化平台：微服务在网关注册路由并对平台暴露管理能力，
网关转发时自动采集调用日志（请求/响应报文、Header、Query 参数），控制台提供
账号权限、服务发现、路由配置、日志追踪、数据统计等运营页面。

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
ai-gateway/
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
```

日常使用只需 `./scripts/start.sh` / `./scripts/stop.sh`，改了代码用 `./scripts/restart.sh backend --build`。

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

后端各服务统一 context-path `/api`；前端所有 `/api` 请求先到 proxy(28090)，
再按 `tb_gw_route` 表二次分发到各微服务 —— 即前端联调依赖 proxy 与路由表数据（种子数据已含）。

## 默认账号

| 账号 | 密码 | 角色 | 来源 |
| --- | --- | --- | --- |
| `admin` | `Admin@123` | ADMIN | SQL 种子数据；或用户表为空时由启动初始化器创建（用户名/密码可用 `gateway.default-admin.*` 配置覆盖） |
| `dev` / `ops` / `viewer` | `Dev@123` / `Ops@123` / `View@123` | OPERATOR / VIEWER | 仅当**用户表为空**时由 `AdminAccountInitializer` 创建；用 `init-db.sh` 初始化（种子已含 admin）后不会自动创建，可在「用户管理」页手工添加 |

生产环境请立即修改 admin 密码，并通过环境变量覆盖 JWT 密钥与数据库口令。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [backend/README.md](backend/README.md) | 后端权威文档：模块划分、命名规范、MyBatis 持久层约定、数据库/Redis/Nacos、接口清单、认证鉴权设计 |
| [frontend/README.md](frontend/README.md) | 前端权威文档：monorepo 结构、模块约定（新增页面三件套）、UI 规范、常用命令 |
| [docs/modules-deployment.md
- [Maven 私服部署指南](docs/maven-nexus-deployment.md) —— SDK 跨团队分发（Nexus 一键部署/发布/接入）
- [服务拆解与按需启动方案](docs/modules-deployment.md) —— 模块依赖矩阵、套餐组装、交付清单
- [docs/PLAN.md]](docs/PLAN.md) | **项目计划与决策记录**：模块取舍（auth/authz/config 为什么不拆/为什么拆）、已完成能力、未完成清单（P0/P1/P2） |
| [docs/configuration.md](docs/configuration.md) | **配置信息一览**：数据库/Redis/Nacos 账号密码与端口、JWT 密钥、环境变量清单 |
| [docs/auth-design.md](docs/auth-design.md) | 认证与鉴权：JWT、登录限流、账号解锁 |
| [docs/gateway-proxy.md](docs/gateway-proxy.md) | 网关转发与调用日志采集链路 |
| [docs/client-integration.md](docs/client-integration.md) | Java SDK 接入指南（自动注册 + 日志采集） |
| [sdk/python/README.md](sdk/python/README.md) | Python SDK 接入指南（Flask / FastAPI / WSGI / ASGI） |

## 常见问题

- **MySQL 起不来**：`./scripts/init-env.sh mysql` 会自动 `docker start mysql57`（容器不存在则创建）；也可以用本机 brew 安装的 MySQL，脚本按端口探测，不强依赖容器。
- **Nacos 挂了影响启动吗**：不影响。后端以 `optional:` 引入 Nacos 配置，只失去动态配置；服务注册同样可选。
- **Redis 挂了影响登录吗**：不影响。登录限流自动降级为单实例内存计数，日志出现 `降级为内存计数`。
- **演示账号 dev 登不上**：见「默认账号」——SQL 种子只含 admin，演示账号仅在用户表为空时自动创建。
- **前端页面 401 / 502**：确认 proxy(28090) 在运行且 `tb_gw_route` 有对应路由（`./scripts/status.sh`；路由在「路由配置」页维护，30 秒内自动刷新）。
- **端口冲突**：`lsof -i :<port>` 找到占用进程，或 `./scripts/stop.sh` 后重启。
