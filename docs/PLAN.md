# 项目计划与决策记录（PLAN）

> 维护约定：任何"要不要做、为什么不做、下一步做什么"的结论都记在这里。
> 回答"项目还有什么没做的"时以此文档为准，并以代码现状为准绳（文档落后于代码时先改文档）。
>
> 最后更新：2026-11-05

---

## 一、项目与模块现状

| 模块 | 端口 | 职责 | 状态 |
| --- | --- | --- | --- |
| `jv-api-gateway-proxy` | 28090 | Spring Cloud Gateway：路由转发、统一鉴权、调用日志、熔断占位 | 可用 |
| `jv-api-gateway-user` | 28081 | 账号 / 登录 / 分组权限 / API Key / 开放认证 | 可用 |
| `jv-api-gateway-config` | 28085 | 平台配置统一存储与实时下发（`tb_sys_config`） | 可用 |
| `jv-api-gateway-overview` | 28082 | 总览看板、统计接口 | 可用（前端部分对接） |
| `jv-api-gateway-discovery` | 28083 | 服务注册表、接口清单、心跳 | 可用（控制台未全接） |
| `jv-api-gateway-trace` | 28084 | 调用日志 / span / 拓扑聚合 | 可用（检索页部分对接） |
| `jv-api-gateway-common` | — | `R<T>`、`BizException`、`JwtUtil`、`UserContext`、全局异常 | 可用 |
| `jv-api-gateway-client-starter` | — | 业务方接入 SDK（注册 + 心跳 + trace 上报） | 可用 |
| 前端 `apps/portal` + `modules/*` | 28080 | Vue3 + Element Plus 控制台（8 个一级菜单模块） | 可用 |

数据存储：MySQL `api_gateway`（业务表）、Redis（鉴权缓存，可选）。SQL 全部收敛在 `backend/sql/`。

---

## 二、关键决策记录（ADR 摘要）

### ADR-001 不新增 `auth` 模块

- **结论**：认证能力留在 `jv-api-gateway-user`，不再单独建 `auth` 服务。
- **理由**：注册 / 登录 / 退出 / JWT 签发 / token_version 失效已在 user 闭环；网关侧验签裁决在 proxy 的 `AuthGlobalFilter`。再拆一个只做"转发登录"的服务，只会增加部署面与跨服务事务成本。
- **触发再评估的条件**：出现多身份源（LDAP / OAuth2 / SAML 同时在线且需要独立演进）、登录 QPS 与业务库负载需要物理隔离、需要独立灰度发布认证策略。

### ADR-002 暂不新增 `authz` 模块

- **结论**：鉴权裁决留在 proxy，权限数据留在 user，不单独建 `authz` 服务。
- **理由**：当前模型足够 —— API Key + JWT 双通道、路由级鉴权、分组权限点并集。独立 authz 的价值出现在"策略引擎化"（scope / 配额 / 路由级 RBAC / ABAC / 策略热更新）之后，现在拆会把一个简单查表变成一次远程调用。
- **触发再评估的条件**：需要按租户 / 按接口配额限流、需要策略 DSL 与可视化策略编排、需要跨网关实例共享策略并独立发布。

### ADR-003 新增 `config` 模块（已实现）

- **结论**：平台级配置统一存 `tb_sys_config`，由 `jv-api-gateway-config`（28085）提供读写，其他模块短周期轮询实现"改完即生效"。
- **理由**：菜单状态标识、鉴权缓存模式、开放认证开关这类开关需要**运行时热切换**且要能被控制台编辑；直接读 Nacos 会引入配置中心强依赖，且 Nacos 变更无法从控制台可视化回写。
- **当前能力**：`GET /configs`（需登录）、`GET /configs/public`（免登录公开开关）、`POST /configs`、`PUT /configs/{key}`、`DELETE /configs/{key}`；3 个种子配置（`platform.menu.health.enabled`、`platform.auth.cache.mode`、`platform.open.auth.enabled`）。
- **多来源托管（ADR-003 补充）**：**元数据永远在 MySQL**（key / 来源 / 平台 / 应用 / 服务 / 类型 / 备注），值可落三种存储：
  | 来源 | 存储形态 | 定位（source_ref） | 说明 |
  | --- | --- | --- | --- |
  | `DB` | `tb_sys_config.config_value` | 无需 | 默认，零依赖 |
  | `REDIS` | Hash，field = 配置键 | Hash 键（默认 `gw:config:platform`） | 多实例共享，读写走 `spring.data.redis` |
  | `NACOS` | properties 文本（dataId 级文件） | `dataId|groupName`（默认 `gw-platform-config.properties|DEFAULT_GROUP`） | 走 Nacos 3.x v3 admin OpenAPI，改单 key 时读整份文件再整体发布，不覆盖同文件其他配置 |
- **降级语义**：远端不可达或 key 被外部删除时，接口返回 MySQL 快照并置 `valueState=STALE`，控制台显示"来源不可达 · 显示最后写入值"，不会因中间件故障打不开系统设置页。

### ADR-004 鉴权缓存：Redis 优先、MySQL 兜底

- **结论**：MySQL 是唯一事实来源；Redis 与本地 Caffeine 都只是副本（TTL 30s），模式由 `platform.auth.cache.mode` 控制（`auto` / `redis` / `local`）。
- **理由**：网关热路径不能每次回源；Redis 提供多实例共享，本地缓存提供 Redis 故障时的兜底（网关不会因为 Redis 挂了整体 401）。
- **已知取舍**：停用 / 轮换 Key、退出登录最迟 30 秒生效（`token_version` 与 Key 状态走缓存）。

### ADR-005 统一结果与统一异常：保持现状，不重做

- **结论**：沿用 `common` 的 `R<T>`（code / message / data / success）+ `GlobalExceptionHandler` + 网关 401 统一 JSON。
- **理由**：8 个前端模块的请求层已按此格式解包；重做会全量破坏解析逻辑，收益仅是"更好看"。
- **仍需补的细节**：错误码细分（目前业务码较少）、网关侧超时 / 上游 5xx 的错误体统一（现在透传上游响应）。

### ADR-006 前端不引入运行时微前端

- **结论**：按一级菜单做**物理隔离 + 产物分包**（`module-*` chunk），不引入 qiankun / wujie。
- **理由**：控制台是内部平台，模块数量有限，运行时微前端带来的独立部署收益低于其运行时开销（沙箱、样式隔离、共享依赖、联调成本）。
- **触发再评估的条件**：模块需要独立团队独立发布、需要与外部系统嵌入同一外壳、单仓构建时间超过可接受阈值。届时 `modules/*` 可直接各升为一个 vite 应用，由 Shell 动态加载。

### ADR-007 目录与命名规范

- **目录**：仓库位于 `code_codex_project/project_api-gateway`（与 `project_ai-platfor` 同级）。
- **SQL**：全部在 `backend/sql/`，命名 `<模块名>-<功能名>-<主表名>.sql`；模块 pom 按前缀打包进 jar 的 `db/`，启动由 `*SchemaInitializer` 幂等执行。
- **Java 命名**：实体统一 `XxxEntity`（如 `UserEntity` / `ApiKeyEntity` / `GwRouteEntity`），Mapper 统一 `XxxMapper`，请求响应统一 `XxxVO` / `XxxRequest`。

---

## 三、已完成能力（可直接演示）

### 网关（proxy）
- 路由来自 `tb_gw_route`，每 30 秒热加载；`/api/**` 由数据库路由驱动，支持 strip / prefix 追加
- 统一鉴权双通道：`Authorization: Bearer <jwt>`、`X-Api-Key: gwak-<keyId>-<secret>`
- 白名单：`/api/auth/login`、`/api/auth/register`、`/api/open/**`、`/api/configs/public`、`/health/**`
- 鉴权缓存 `AuthCacheStore`：Redis（存在时）/ Caffeine（兜底），模式热切换
- 调用日志与 trace 上报（`TraceGlobalFilter`）
- 菜单健康探测：`GET /health/menu-services`（30 秒一轮，UP / DOWN / ERROR + 延迟）

### 用户与权限（user）
- 登录 / 注册（注册即登录）/ 退出（token_version 自增）/ 当前用户权限点
- 用户 CRUD、状态、重置密码
- 分组权限：ORG 部门树 + PERM 权限组、成员、权限点全量替换、权限字典
- API Key：签发 / 启停 / 轮换 / **续期** / 删除，明文仅返回一次，库内只存 SHA-256
- **API Key 续期**：`POST /keys/{id}/renew`（`renewDays` 1~3650）只延长有效期、不换密钥；未过期顺延、已过期从当前时间起算、永久 Key 拒绝
- **API Key 统计看板**：总数、启用/停用、今日新增/轮换/续期/吊销/删除/启用、7 日内到期；`tb_gw_api_key_log` 审计表 + `GET /keys/logs`
- **开放认证**：`/api/open/auth/{register,login,logout,token/verify}`，verify 同时支持用户 JWT 与 API Key

### 配置（config）
- 3 个平台开关的读写；公开开关免登录；变更 10~30 秒内在各模块生效
- **多来源托管**：配置项可指定落 DB / Redis Hash / Nacos（properties），列表展示来源、来源定位（连接串+Hash/dataId）、所属平台 / 应用 / 服务
- 新增 / 编辑 / 删除均同步目标存储；远端不可达回落快照并标记 `STALE`
- 控制台：来源统计卡片（可点击筛选）、新增对话框（来源 + 归属 + 定位）、删除二次确认

### 前端
- 登录 / 注册双 Tab；分组与权限三页接真实接口；API Key 管理页（含统计看板）
- 侧边栏菜单状态点（绿/灰/红）+ 悬浮信息（菜单名｜服务ID｜状态｜延迟），开关在系统设置
- 系统设置页（3 项配置可视化编辑，保存即生效）
- 接入中心 → 开放认证文档页（4 个接口 + 5 个调用示例）

---

## 四、未完成 / 计划中（按优先级）

### P0 —— 影响"能交付"的缺口

| 项 | 现状 | 计划 |
| --- | --- | --- |
| 路由配置页对接真实接口 | 前端 `route-config` 仍是设计稿；`tb_gw_route` 只有种子数据 | user 或 discovery 增加 `/apis/routes` CRUD，前端接真实数据 + 发布后 30 秒生效提示 |
| 安全策略页接真（假页面优先 1） | MFA 范围 / TOTP 偏移 / step-up 开关仅前端本地状态，刷新即还原 | 配置落库（tb_sys_config 或独立策略表）+ 登录流程读取生效：强制 MFA 范围、敏感操作二次验证真正拦截 |
| 登录方式页接真（假页面优先 2） | 密码/验证码/扫码/SSO 因子开关纯展示 | 开关落库 + 登录接口按开关启用/禁用对应方式（密码关掉时返回明确错误） |
| 令牌与会话页接真（假页面优先 3） | 在线会话列表是写死的演示数据 | JWT 无状态 → 登录时 Redis 会话登记（token → 用户/IP/UA/时间），登出、改密、禁用时清除；页面改读 Redis；踢人复用 token_version 自增（已有） |
| 单点登录页接真（假页面优先 4） | OIDC/SAML 配置表单不落库 | 工作量最大：OIDC Provider 对接 + 回调路由 + 账号映射 + 配置加密存储；视对接方需求排期 |
| 系统接入页 | 静态说明页（定位即文档向导） | 保留说明定位，或升级为真实接入向导（选应用 → 生成依赖坐标与 yml → 校验注册结果） |
| 角色管理页 | Placeholder（角色实际由分组权限承担） | 明确"角色 = PERM 分组"的语义后合并到分组页，或补角色 CRUD |
| 开放认证的调用方约束 | 免令牌开放，仅总开关 | 增加调用方白名单 / IP 限制 / 每分钟限流（复用 API Key 作为调用凭证） |
| 网关限流 | 未实现 | `RequestRateLimiter` + Redis，按 Key / IP / 路由维度 |
| 鉴权豁免精细管理（平台端） | 全局控制位已就位（系统配置 `platform.auth.exemption.control`：CLIENT/PLATFORM），SDK 侧只有整体开关与豁免路径列表 | 新增「鉴权管理」菜单：按 应用 / 服务 / 接口 维度单独配置校验豁免（加白理由、有效期、审计人），平台端落库并下发（client 配置中心拉取或网关下发）；控制位切 PLATFORM 时以平台配置为准，切 CLIENT 时以各业务系统 yml 为准 |

### P1 —— 体验与运维

| 项 | 说明 |
| --- | --- |
| 调用量报表页 | `/open/usage` 仍是 Placeholder；trace 表已有数据，可做按 Key / 路由 / 状态码聚合 |
| 数据统计页 | 接口具备（`/api/stats/**`），前端深化图表与导出 |
| 日志追踪检索页 | 后端 span / log / 拓扑已就绪，前端筛选、链路详情、慢调用定位待完善 |
| 服务发现控制台 | 注册表与心跳已有，缺实例下线 / 权重 / 健康剔除的可视化 |
| 菜单按权限动态渲染 | 现有权限点已下发 `permCodes`，前端菜单尚未按权限过滤（全部可见） |
| 配置变更审计 | `tb_sys_config` 只有 `update_time`，缺"谁改的、改前值"；建议复用 `tb_gw_api_key_log` 的审计表模式 |
| Nacos 复杂格式 | 当前 NACOS 来源只处理 properties 扁平键值；YAML 多层级配置仍建议在 Nacos 控制台维护、平台只读接入 |
| 告警 | 总览页有告警设计稿，后端无规则引擎与通知渠道 |

### P2 —— 长期演进

| 项 | 说明 |
| --- | --- |
| 灰度发布 / 蓝绿 | `tb_gw_route` 无 version / weight 字段，需扩展路由模型 |
| 多租户 | 账号、Key、路由、统计均未带 tenant_id |
| 运行时微前端 | 见 ADR-006，触发条件满足再评估 |
| 多语言 SDK | 现只有 Java client-starter |
| 自动化测试 | 目前主要靠脚本 + CDP 人工验收；缺单测 / 集成测试基线 |
| CI/CD | 构建与发布仍为本地脚本 `scripts/restart.sh` |
| 可观测性 | 缺 Prometheus 指标、链路采样率配置、日志聚合 |
| JWT 密钥托管 | 当前走环境变量 / 配置文件，未接 KMS 或密钥轮换机制 |
| 审计合规 | 登录日志、操作日志（谁在何时改了路由 / 分组）未落库 |

### 明确不做（避免反复讨论）

- 不单独拆 `auth` / `authz` 服务（ADR-001、ADR-002）
- 不重做统一响应结构（ADR-005）
- 不引入运行时微前端框架（ADR-006）
- 不用 JWT 承载权限点（权限点实时性要求高，走 `permCodes` 接口 / 缓存）

---

## 五、环境与操作备忘

```bash
bash scripts/restart.sh backend --build   # 后端构建并重启（含建表种子）
bash scripts/restart.sh frontend          # 前端 dev server（28080）
bash scripts/init-db.sh                   # 手工执行 backend/sql 全部脚本
```

- 内置账号：`admin / Admin@123`（超管）、`dev / Dev@123`、`ops / Ops@123`、`viewer / View@123`
- 关键配置：`platform.menu.health.enabled`、`platform.auth.cache.mode`、`platform.open.auth.enabled`
- 排查顺序：`logs/<服务>.log` → `GET /health/menu-services` → `GET /api/configs/public`

---

## 六、变更记录

| 日期 | 内容 |
| --- | --- |
| 2026-11-05 | 初版：沉淀 auth/authz/config 决策；补 API Key 统计看板与开放认证 logout / 双类型校验；实体统一 `XxxEntity`；仓库迁移至 `project_api-gateway` |
| 2026-11-05 | SDK 安全双门落地：① 上报共享密钥 `X-Gateway-Client-Token` 两端实现（SDK 自动携带、网关对上报路径校验，伪造注册/上报 401）；② SDK 入口鉴权 `GatewayAuthFilter`（回调 /open/auth/token/verify 验签 + 缓存，豁免/fail-open 可配，SDK 数据上报不受影响）；③ Python SDK `sdk/python/jv_gateway` 补齐（注册/心跳/上报/中间件/入口鉴权，冒烟脚本全通过并真实落库）；④ 接入文档同步（client-integration.md、AutoOnboard 页、backend README）。修复：ConditionalOnMissingBean 按类型判重把 gatewayAuthFilter 挤掉（改按 bean 名）、豁免路径需剥离 context-path |
| 2026-11-05 | 新增 scripts/init-nexus.sh（Nexus 3 一键部署，Docker 端口 28095、数据卷 dist/nexus-data、幂等可重复执行）+ docs/maven-nexus-deployment.md（初始化/发布/业务方接入/版本升级与运维/方案对比表），1.2 节加一键部署链接——补齐私服分发路线的部署环节 |
| 2026-11-05 | 接入文档补「1.3 不走私服的分发替代方案」：A 项目内 file:// 仓库（deploy-file 到 file:// 生成布局，git 分发、CI 零配置）、B 云制品库（GitHub Packages/云效/CODING，Maven 协议兼容）、C CI 构建期自动安装；明确不推荐 systemPath 及原因 |
| 2026-11-05 | 接入文档补「1.2 多团队/多项目分发：Maven 私服」（deploy 三命令 + 业务方 repositories/Gradle 配置 + 无私服 CI 兜底），接入页方式二补私服提示 alert——install.sh 只装当前机器，跨团队接入走私服 |
| 2026-11-05 | 新增 docs/modules-deployment.md：服务拆解与按需启动方案（后端模块依赖矩阵与裁剪方法、前端构建期菜单裁剪、三档套餐组装示例、多 Pod 注意点、交付检查清单），支撑"客户按模块购买、交付时组装启动"的售卖模式 |
| 2026-11-05 | Node.js SDK 落地（sdk/nodejs，jv-gateway，零第三方依赖）：GatewayClient/TraceReporter/GatewayAuth/traceExpress/traceWrap + Express 示例 + 冒烟脚本，协议与 Java/Python 完全一致；接入文档（client-integration.md 第十章、backend README）与接入页新增 Node.js tab；系统配置新增豁免权全局控制位 platform.auth.exemption.control（CLIENT/PLATFORM 单选，安全策略组）；一键启动脚本 start.sh 默认先 mvn clean package 全子模块清理打包再运行（--skip-build 可跳过）；日志体系统一切分：logs/app/ 下按 日期--小时--ai-gateway--模块名--日志类型(运行/采集上报)--等级(INFO/WARN/ERROR/CONSOLE).txt 每小时滚动（logback-spring.xml，保留 7 天，ERROR 14 天） |
| 2026-11-05 | 页面数据真实性盘点：19 页中 14 页走真实接口（总览/统计/发现/追踪/用户/Key/分组/部门/菜单/接入/路由/设置），5 页为静态演示（登录方式/SSO/令牌会话/安全策略/系统接入）；接真计划与优先级已写入 P0 待办表 |
| 2026-11-05 | 服务离线状态修复：① `dotClass` 补「离线」映射（此前离线无颜色，与普通文字无异）；② 主动下线 deregister 由「异常」改为「离线」；③ 心跳巡检不再只处理 status=健康（异常/亚健康超时同样转离线）。实测 kill config → 页面 10 秒内红点红字「离线」 |
| 2026-11-05 | 控制台自动刷新频率配置化：新增 `platform.ui.refresh.interval`（公开配置），网关总览 / 数据统计 / 服务发现接入 `useAutoRefresh` 轮询（服务健康状态随轮询更新），改值 30 秒内生效；默认频率调整为 10 秒 |
| 2026-11-05 | 接入 SDK 修复：注册失败后心跳周期自动补注册（实测启动顺序敏感场景自愈）；网关白名单放行 SDK 上报通道（此前 401 导致实例永久离线）；默认 server-addr 由前端 28080 改为网关 28090 |
| 2026-11-05 | API Key 增加「续期」操作（`POST /keys/{id}/renew`），审计新增 `RENEW` 动作，看板增加「今日续期」；菜单「接入中心」移到「认证与安全」之后 |
