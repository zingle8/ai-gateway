# 项目计划与决策记录（PLAN）

> 维护约定：任何"要不要做、为什么不做、下一步做什么"的结论都记在这里。
> 回答"项目还有什么没做的"时以此文档为准，并以代码现状为准绳（文档落后于代码时先改文档）。
>
> 最后更新：2026-11-06

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
| 2026-11-12 | 当日完成 **v0.2 第 9 项：列表数据导出（日志追踪 / 服务发现 CSV）**——① 后端 TraceController 新增 GET /trace/logs/export、DiscoveryController 新增 GET /discovery/services/export，参数与列表接口一致（沿用当前筛选），固定上限 10000 行防拖库；② CSV 带 UTF-8 BOM + text/csv 响应头 + Content-Disposition 中文文件名，字段统一 csvCell 转义（引号/逗号/换行）；③ 每行追加「操作人 + 导出时间」留痕，操作人优先级 X-Username 头 → UserContext → uid: → system；日志 10 列（实体无 method 字段故不输出方法列）、服务 15 列；④ 前端两页筛选项区新增「下载 CSV」按钮（fetch + Authorization + X-Username → blob 落盘，文件名带日期）；⑤ 实测：直连导出 10001 行含表头、响应头正确、操作人=admin，控制台点击弹「已导出当前筛选结果（最多 10000 行）」；⑥ 边界：仅 CSV 单格式、同步全量生成（无异步任务/进度条）、操作人头部可伪造（仅运维留痕，审计证据以第 8 项为准）、导出行为本身不落审计事件 |
| 2026-11-12 | 当日完成 **v0.2 第 5 项：鉴权精细豁免管理（新菜单）**——① 新表 tb_gw_auth_exempt（路径 / 方法 / 类型 LOGIN·RATE·ALL / 到期时间），种子 1 条停用示例；② config 侧 AuthExemptController + Service（/auth-exempts CRUD + 启停，路径/类型/到期时间校验），路由 /api/auth-exempts/**；③ 网关 AuthExemptRepository（5 秒缓存）+ AuthGlobalFilter / RateLimitGlobalFilter 追加放行，yml 白名单不受影响；④ 控制台新增「鉴权豁免」菜单（表格 + 对话框 + 高危二次确认 + 过期标记 + 分页），权限点 auth.exempt.view/edit 种入并绑定网关运维组；⑤ 实测：无 token 访问 /api/users 401 → 新增 LOGIN 豁免 → 200 → 停用 → 401，全程 6 秒内生效 |
| 2026-11-12 | 当日完成 **v0.2 第 3、4 项：安全策略 / 登录方式接真**——① 两组策略 JSON 落库 tb_sys_config（platform.security.policy / platform.login.methods），config_value 扩 TEXT；② 新增 PolicyController（/policies/security、/policies/login-methods 读写）+ 路由 /api/policies/**；③ 登录流程 LoginPolicyService 实时读取：失败锁定阈值与时长动态化、MFA 范围（NONE/ADMIN/ALL）回传 mfaRequired、username 因子关闭即拒绝密码登录（400）；④ 前端两页接真（加载 / 保存 / 即时生效），去掉设计稿标记；⑤ 实测：mfaScope=ALL → mfaRequired=true；关闭密码因子 → 登录 400，恢复即正常；策略 424 字节完整回显；⑥ 边界：TOTP / step-up / 验证码通道随 v0.3 |
| 2026-11-12 | 当日完成 **v0.2 第 1 项：网关限流（令牌桶）**——① 新增 tb_gw_rate_limit（GLOBAL/ROUTE/API_KEY/IP 四维 + replenishRate/burstCapacity/requestedTokens，种子全局 1000 次/秒、演示路由 20 次/秒）；② 网关 RateLimitGlobalFilter（鉴权之后）+ TokenBucketLimiter：Redis Lua 原子令牌桶、多实例共享、Redis 不可用回落本地（30 秒重试）、5 秒规则缓存；③ 429 返回 Retry-After 与 X-RateLimit-Limit/Remaining/Reset；④ 控制台新增「接入中心 → 限流策略」页（增删改查 + 启停 + 搜索 + 分页），权限点 onboard.ratelimit.view/edit 种入并绑定网关运维组；⑤ 实测：IP 5 次/秒桶 5 → 200×5 + 429×5 + 补充后 200 + 429；ROUTE 1 次/秒桶 1 → 30 并发 1×200 + 29×429；停用 5 秒内恢复 |
| 2026-11-12 | 当日完成 **v0.2 第 7 项后半：账号-平台（应用）授权**（第 7 项整体完成）——① 新增 tb_gw_open_platform（平台登记，种子 3 个演示平台）+ tb_gw_account_platform（账号-平台授权，uk_user_app 唯一）两表，PermSchemaInitializer 启动建表；② 后端 OpenPlatformController：GET /open/platforms、GET|PUT /open/accounts/{id}/platforms（全量覆盖、忽略不存在 appId、空数组清空、事务）；③ OpenVerifyRequest 增可选 appId，/open/auth/token/verify 带 appId 时校验平台存在且启用 + 账号已授权，未授权 valid=false「账号未授权访问平台「xxx」」，不传保持原行为；④ 前端用户管理页操作列新增「平台授权」对话框（多选、停用置灰、回显）；⑤ 实测：未授权拒绝 / 授权后通过 / 未授权平台拒绝 / 不存在平台 403 / 关系表落库与回显正确；⑥ 边界：应用维度数据视图过滤未涉及（当前无按 appId 切分的数据视图），待数据链与 SSO 落地 |
| 2026-11-12 | 规划变更：**v0.3 新增第 11 项「RPC 日志采集（Dubbo / gRPC / 出站 HTTP body）」**。背景：经排查，SDK 现仅覆盖 HTTP 系采集（入站 Servlet Filter 含 req/res body、WS 帧、SSE），RPC 链路无采集（tb_gw_trace_log.protocol 恒为 HTTP/1.1，全库无 Dubbo/gRPC 依赖，出站 CLIENT span 亦不记 body）。方案四点：① Dubbo Filter 采集 invocation 参数与 Result（JSON 化，失败降级元数据）；② gRPC ServerInterceptor + protobuf JsonFormat；③ 扩展 TracePropagationInterceptor 补出站 req_body/res_body（tb_gw_trace_span 加两列）；④ protocol 增加 DUBBO/GRPC/HTTP 取值并同步前端筛选。依赖与量级排期时评审，采样/限速/截断/脱敏沿用 v0.2 第 14 项策略 |
| 2026-11-11 | 当日完成 **v0.2 第 20 项：平台配置模糊搜索（系统设置）**——① 系统设置「平台配置」卡片头部新增搜索框（放大镜图标 + 可清空），输入实时模糊过滤、不区分大小写；② 覆盖 8 个字段：配置名 key、配置参数值 value、配置描述 remark、平台名、应用名、服务名、值类型、来源标签；③ 与来源筛选叠加（先按来源再按关键词），搜索词变化自动回第 1 页，总数显示「共 N 项（已筛选）」，无结果展示「没有匹配的配置项」空态；④ 纯前端过滤（/configs 全量返回，DB/Redis/Nacos 的值均可搜到），后端接口与表结构无变更；实测：schedule → 1 项、open → 1 项、网关 → 6 项、zzzz → 0 项 + 空态 |
| 2026-11-11 | 当日完成 **v0.2 第 19 项第二批：分页条接入 7 个列表**——① 令牌与会话-在线会话、② 令牌与会话-令牌吊销记录（两个 Tab 各自独立分页）、③ API Key 列表、④ 系统接入-配置项、⑤ 路由配置、⑥ 系统设置-配置项、⑦ 调度计划-调度任务；均为前端切片分页（接口保持全量拉取，页面维护 page/size 按页 slice），筛选变化自动回第 1 页，总计以「共 N 条」展示；系统接入/路由配置分页条以负 margin 抵消 gw-card-bd 内边距并与卡片左右对齐，调度计划分页条右对齐于表格下方；实测：系统接入 13 项 2 页（第 2 页 3 行、切 20 条/页 → 1 页 13 行）、路由配置 40 条 4 页、API Key 6 个 / 系统设置 6 项 / 调度计划 2 个任务 / 吊销记录 3 条均 1 页且上下页按钮正确置灰 |
| 2026-11-11 | 当日完成 **v0.2 第 19 项：列表分页条统一增强（每页条数切换 + 上/下一页）**——① 新增 `@zimo/ui/GwPager` 共享分页组件：每页条数下拉（10/20/50/100 个/页）、上一页/下一页按钮（首/末页自动禁用）、页码「第 x / y 页」按 ceil(total/size) 计算，切换条数回第 1 页重新请求，样式沿用 --gw-* 设计变量；② 接入全部 4 个带分页页面，替换原 el-pagination（仅有页码、无条数选择）并清除「每页 N 条」旧文案：日志追踪-调用日志、日志追踪-WebSocket 会话、用户管理、服务发现；各页新增 onSizeChange（改条数 → current=1 → load）；③ 实测：调用日志 147984 条 / 14799 页翻页与切 20・50 条每页页码同步刷新，用户管理 1 页时上下页正确置灰，服务发现 4 页正常，全项目无 el-pagination 残留。提交 cac2af1 |
| 2026-11-09 | 当日完成 **v0.2 第 18 项：WebSocket 转发与会话采集（proxy 增强）**——① 实测确认网关对 SSE 转发正常（逐条实时到达/42 秒长连接/40 秒静默不断流），并修复 SSE 响应体不入追踪日志问题（BodyCaptureResponse 补 writeAndFlushWith 旁路捕获）；② WebSocket 转发验证：路由 upstream_url 填 http:// 即可，SCG 握手自动 http→ws 转换，实测文本 3 轮 round-trip + 二进制帧 echo 正常；③ **会话级旁路采集**：@Primary 自定义 WebSocketService 装饰 HandshakeWebSocketService，CapturingSession 包装入站会话，receive()/send() 双向旁路统计消息数/字节数 + 双向首帧样本（文本前 N 字节/二进制标记，gateway.trace.ws-sample-bytes 可配），纯观察不重写；④ 新表 tb_gw_ws_session（起止/时长/双向计数与字节/样本/关闭状态，trace_id 关联日志追踪），WsSessionSink 队列+每秒批量落库（与 TraceSink 同模式）；⑤ 编译期适配：MyBatis 接口归位 mapper 包、补齐 WebSocketHandler/WebSocketSession 全部接口方法。实测记录：in 4 条/89 字节、out 4 条/89 字节、NORMAL 关闭，trace_id 关联成功；⑥ **帧级明细（同日增强）**：WsFrameRecord/WsFrameMapper/WsFrameSink + Recorder 每帧记录（方向/类型/payload 样本/字节数/时间），配置 gateway.trace.ws-frame-log / ws-frame-sample-bytes / ws-frame-max-per-session，新表 tb_gw_ws_frame（session_key 关联）；⑦ 日志追踪页新增「WebSocket 会话」视图（WsSessions.vue：会话列表 + 帧明细抽屉，二进制 hex 分组展示），trace 服务新增 GET /trace/ws/sessions、/trace/ws/frames。实测：8 帧（IN/OUT×3 文本 + 1 二进制 hex 89504e4700）完整落库并在页面展示；⑧ **WS 采集表纳入每日优化**：tb_gw_ws_session / tb_gw_ws_frame 加入调度中心清理清单 tb_scheduler_clean_table（保留 7 天，与 trace_log/span 同策略）；tb_gw_ws_frame 重建为按天分区表（PK 含分区列 recv_time，与 trace_span 同模式）并加入分区预建配置 tb_scheduler_partition_config（提前 2 天）；SSE 数据存于 tb_gw_trace_log.res_body，天然在既有清理/分区内。实测：手动触发 partition-daily 自动为帧表生成 p20261111 分区、data-clean 含 6 张采集表执行 SUCCESS |
| 2026-11-06 | 服务标识体系拆分落地：新增「服务ID（英文唯一标识）+ 服务名（中文显示名）」对齐应用ID/应用名模式——DB 加 service_id（uk 唯一）、注册/心跳按 serviceId 幂等（旧 SDK 回退 serviceName）、SDK 三端（Java/Python/Node）加 serviceId+中文 serviceName、前端发现/追踪页拆列展示、种子 SQL 与文档同步 |
| 2026-11-06 | 修复采集归属口径错位：Java SDK GatewayTraceFilter 上报 serviceName 用中文名导致 trace 表混入「配置中心」等中文值、服务发现 QPS/调用次数聚合落空。修：Properties 增 effectiveServiceId()，注册与采集统一取该值；TraceLogDTO/SpanRecordDTO/SpanDTO 增 serviceId；trace 落库统一写服务ID；历史数据按注册表回填清零；trace filters 联 tb_gw_service 返回 serviceId+中文名；前端追踪列表/详情/拓扑/下拉同时展示服务ID与服务名。regtech 侧 gateway-bridge 采集上报同步对齐（已实测） |
| 2026-11-06 | 修复筛选项「应用」下拉重复：同一 appId 历史改名（app-gateway 网关平台→API网关平台、app-smoke Node/Python冒烟、app-pay 支付中心/支付网关），字典 SQL DISTINCT app_id+app_name 产出多行。修：trace/discovery/overview 三处字典按 app_id 取最新一条，名称优先取注册表 tb_gw_service；数据侧 trace_log/trace_span 的 app_name 对齐注册表、app-smoke 统一「冒烟测试」、app-pay 归并支付中心。实测追踪页 5→3、发现页 5→2 |
| 2026-11-06 | 数据统计页布局优化：「请求方法分布」与「状态码分布」并排一行（各占半宽），请求量趋势改为整行。根因修复：`.grid-2` 缺基础样式（仅有页面内 1400px 的 media query 覆盖），实际一直是堆叠的——在 shared/components.css 补 `display:grid; repeat(2,minmax(0,1fr)); gap:12px; align-items:start`，并抵消 `.gw-card + .gw-card` 的纵向间距、断点降到 900px；overview 页同步受益（趋势/QPS 趋势并排）。CDP 实测两卡片同位同高（top=638、h=150、各 490px） |
| 2026-11-06 | 需求登记（排期 v0.2）：服务发现 / 日志追踪两页筛选区「重置」按钮后新增「下载」按钮，按当前筛选导出 Excel / CSV / TSV / HTML 四种格式（含导出上限防全量拖库，实现方式评审后定）；已写入 DEVELOPMENT-PLAN.md v0.2 第 9 项 |
| 2026-11-06 | 技术值彻底更名 ai-gateway → api-gateway：JWT 密钥默认值（user/config/proxy 三个 application.yml）、logback 日志文件名 pattern（6 个模块 logback-spring.xml）、scripts/_common.sh 控制台日志文件名、configuration.md 两处默认值文档；restart.sh --build 重启后验证：登录/鉴权 200（新密钥签发正常）、新日志文件名为 日期--小时--api-gateway--…、旧文件 458 个按滚动策略自然过期。注意：密钥变更使旧 token 全部失效，浏览器需重新登录 |
| 2026-11-07 | 五页面接口性能优化（实测驱动）：① 覆盖索引 3 组（trace_log: idx_time_cost / idx_svc_cover，span: idx_time_trace_cost）同步固化进 backend/sql；② 分位数算法重写——P95/P99 由「ORDER BY cost LIMIT 1 OFFSET」每分位一次 filesort 改为一次覆盖索引扫描 + Java 排序共用；③ 多 SQL 并行化——总览指标卡 7 路、统计页 8 路、发现列表 4 路改 CompletableFuture 并行。实测：总览指标卡 1361→192ms(-86%)、实例排名 390→69ms、统计 summary 569→190ms、24h 趋势 523→164ms、发现列表 614→78ms、慢链路 506→208ms |
| 2026-11-07 | 需求登记（排期 v0.3）×3：① 第 8 项工程规范落地——Java 引入阿里 P3C 规则集（SpotLess+Checkstyle 绑 Maven 构建）、前端 ESLint+Prettier+.editorconfig、约定式提交、版本收尾规约门禁；② 第 9 项存量代码规范审查——后端 5 模块+SDK 三端按《阿里 Java 开发手册（黄山版）》扫描，违规分级（Blocker/Critical/Major）修复；③ 第 10 项 AI 开发规范与流程（元规范）——将本项目确立的规划规则（需求登记/版本分界/缺口审查/诚实边界）、AI 编码规范、测试验收规范（重启验证/实测渲染/验收清单）、文档生成规范（三文档维护/四处同步/与实际行为一致）固化为开发手册。v0.3 7→10 项，README 同步 |
| 2026-11-07 | 需求登记（排期 v0.2 第 16 项）：前端微前端化（Module Federation，方案 A）——8 模块独立构建独立部署、portal 运行时按模块地址表动态加载、shared 依赖单例（登录态全局唯一）、单模块故障占位兜底；与后端模块化部署配套实现按模块交付；代价：shared/ui 升级需全模块重发（严格 pin 版本）。总览 15→16 项，README 简表同步 |
| 2026-11-07 | 当日完成 **v0.2 第 17 项：分布式调度中心 jv-api-gateway-scheduler**（端口 28087）——① 任务定义/分布式锁(MySQL 兜底)/执行计划/执行记录/清理清单/分区配置 6 张新表（backend/sql/jv-api-gateway-scheduler-tables.sql，含网关路由与菜单初始化）；② 双任务：每日历史数据清理（物理删除 7 天外 tb_gw_trace_log/trace_span/api_key_log/service_edge，分批 5000 防长锁）、分区表每日分区预建（提前 2 天落 tb_scheduler_plan 计划并执行 ADD PARTITION，幂等跳过已存在分区）；③ 分布式锁与调度时间配置 Redis 优先 + MySQL 兜底双存储（Redis 断连自动降级验证通过）；④ 新菜单「系统管理→调度计划」（任务列表+弹框：任务状态详情/执行计划/执行记录+手动触发）、系统设置页新增「调度时间」专用时间选择器（MySQL+Redis 双写立即生效，实测 03:30 双端一致）；⑤ **公共配置收敛**（按用户要求）：common 模块新增 zimo-common.yml（datasource/redis/nacos/mybatis/logging/context-path 基线）与 constant 包（GwConfigKeys/GwRedisKeys），user/overview/trace/config/discovery/scheduler 6 服务 spring.config.import 接入、yml 累计瘦身约 200 行；proxy 为 WebFlux 不依赖 common，配置自持（注意：其 MyBatis mapper-locations 与数据源不可删，调试中发现并已恢复）；scheduler 代码常量分门别类（SchedulerConstants + common GwConfigKeys/GwRedisKeys）。全量重启后 8/8 服务运行中，经网关冒烟：登录/总览/发现/调度/追踪全 200 |
| 2026-11-07 | 需求登记（排期 v0.2 第 17 项，当天开发）：新增子模块 jv-api-gateway-scheduler（分布式调度中心，端口 28087）——① 分布式锁与配置 Redis 优先 + MySQL 兜底双存储；② 每日清理任务（系统设置页新增「调度时间」配置项，默认 02:00，物理删除 7 天外采集日志/记录表：trace_log/trace_span/api_key_log/service_edge，清单配置化）；③ 分区预建任务（为 trace_log/trace_span 提前 2 天生成每日分区计划并执行 ADD PARTITION，配置入 tb_scheduler_partition_config）；④ 新菜单「系统管理 → 调度计划」——任务列表 + 弹框详情（任务状态/执行计划/执行记录）+ 手动触发。总览 16→17 项。同步开发并在本日完成实现 |
| 2026-11-07 | 第 2 项补「多粒度强制下线（踢人）」：会话登记带平台（appId）与设备维度，四种踢人粒度——单会话（删键+token 失效）、按平台（切 appId，其他平台不受影响）、按端（UA/设备指纹筛选）、全端（token_version 自增）；SSO 后业务平台会话随 IdP 踢人自动失效（一处操作处处下线）；踢人操作留痕进第 8 项审计 |
| 2026-11-07 | 单点登录（SSO）自 v0.3 提前至 v0.2 第 15 项：网关平台作 IdP（/authorize、/token、/userinfo、元数据发现 + jwks，id_token 即 JWT 复用现有密钥体系）；接入方登记页接真（client_id/secret/回调，配置加密）；换 token 校验第 7 项账号平台授权（认证≠能用）；统一登录页复用第 4 项四形态；单点登出=IdP 会话失效+token_version。v0.3 明细删除该条并重编号为 7 项，总览表 v0.2 14→15、v0.3 8→7，README 版本简表同步（v0.2 概要此前为旧 9 项文本一并修正） |
| 2026-11-07 | 平台级共享/授权设计（按用户澄清「平台=业务应用/系统」）：① 第 7 项扩为「菜单权限过滤 + 账号平台授权」——账号授权 N 个业务平台（appId 列表 + 新授权关系表），单平台账号即授权一个，未授权平台入口与数据不可见；② 第 6 项补「Key 平台/服务作用域」——Key 可用服务白名单，网关校验越界 403，防 Key 泄漏横向滥用；③ 跨平台共享登录=SSO，维持 v0.3 计划（认证共享与授权范围分层不冲突） |
| 2026-11-07 | 登录控制设计定稿（登记 v0.2）：① 第 2 项补「多端登录控制」——平台级策略（单端互踢 / 多端共存+上限，默认共存上限 5），会话登记时校验、超限踢最早会话，会话页展示并发端与手动下线；② 第 13 项定 API Key 设计口径——机器凭证不做端数控制，控制走限流+白名单+调用熔断，补「最后使用时间+最近来源 IP」记录供长期未用预警；开放认证 token 同口径 |
| 2026-11-07 | 第 4 项扫码登录扩为两种验证形态（可配置切换）：① 演示确认流（二维码→轮询→确认，mock 实现）；② 新增「扫码取码 + 手动输入」流——二维码内容即 6 位动态码（30 秒自动更换，与弹框动态码同源机制），手机任意扫码工具读码后在旁边手动输入框输入完成验证，零外部 App 回调与连通性依赖 |
| 2026-11-07 | 第 4 项登录方式实现口径细化（按用户澄清）：密码登录验证码两形态可配——随机图片（角度矫正/扭曲/干扰线）与弹框动态码（6 位不可复制、30 秒自动更换、下方手动输入）；短信验证码功能链路完整开发（按钮/倒计时/校验/有效期）但发送通道「未接入」状态（验证码落平台日志，不接网关不做连通性测试，后续仅换发送实现）；扫码登录完整开发（二维码生成→轮询→确认）但确认端用平台内演示/mock，不依赖外部扫码 App |
| 2026-11-07 | v0.2 缺口审查并补依赖标注：① 第 4 项登录方式补通道澄清——短信验证码/扫码无外部通道，v0.2 开关仅落库拦截（返回「通道未接入」），不伪装可用，通道接入排 v0.3+；② 第 6 项限流复用第 1 项组件；③ 第 13 项补前置依赖（第 2 项会话、第 8 项审计）与新增登录记录表 tb_login_log（异常登录判定 + 第 2 项「最近登录」共用）；④ 清单尾部新增「实现顺序与依赖」小节与三条协同约定（新页面同步登记权限点纳入第 7 项、登录记录表两处消费、新表随条目出脚本并入配置文档） |
| 2026-11-06 | 需求登记（排期 v0.2）×2：① 鉴权风控管理（第 13 项）——用户登录失败策略/异常登录识别/处置动作、API Key 调用量与错误率异常自动停用、风控事件统一列表与处置留痕；② 日志打印管控与处理（第 14 项）——关键节点打印策略（采样限速）、日志级别动态热调、敏感字段脱敏、日志量管控、SDK 侧关键节点打印开关，ERROR 日志为 v0.3 告警预留数据源。写入 DEVELOPMENT-PLAN.md（总览概要 12→14 项） |
| 2026-11-06 | 需求登记（排期 v0.2）：服务拓扑页双视图——「服务调用拓扑」（现有）与「主机 pod 调用拓扑」（新增：节点为主机/pod、边为跨主机聚合调用、节点详情含实例清单），Tab/分段控件切换，依赖第 10/11 项数据；写入 DEVELOPMENT-PLAN.md v0.2 第 12 项（总览概要 11→12 项） |
| 2026-11-06 | 第 11 项更名并划界：「部署关联与真实网络链路统计（物理拓扑）」→「部署关联与跨主机调用统计」——平台无机柜号/交换机路由器等网络设备数据源，物理网络拓扑与 3D 机房展示无法成立且属伪造数据可视化，明确不做（写入条目边界）；保留三产出：部署清单、按源→目标 IP 的跨主机调用聚合、单点影响面分析 |
| 2026-11-06 | 需求登记（排期 v0.2）：部署关联与真实网络链路统计（物理拓扑数据层）——服务实例↔主机/pod↔调用链路三方关联（tb_gw_trace_log 已有 req_ip/res_ip 两端地址，hostname 由第 10 项补齐）；产出部署清单视图、跨主机调用统计、单点风险评估；拓扑图可视化可顺延 v0.3。写入 DEVELOPMENT-PLAN.md v0.2 第 11 项（总览概要 10→11 项） |
| 2026-11-06 | v0.2 第 10 项指标清单扩充为六大类：CPU / GPU / 内存 / 磁盘 / 网络 / IO——补 GPU（数量、利用率、显存、温度，pynvml 或 nvidia-smi，无卡自动跳过）、网络（收发字节与包速率、错误丢包、TCP 连接数）、磁盘 IO（吞吐、IOPS、设备利用率）；Java 需引 OSHI（CPU/磁盘/网络），Python 全程 psutil，Node 解析 /proc；单机上报可开关（metrics.enabled），静态维度注册上报、动态指标 60s 采样落 tb_gw_host_metrics |
| 2026-11-06 | 需求登记（排期 v0.2）：接入方主机与运行指标上报·历史趋势方案——SDK 独立 60s 定时上报（hostname/OS/CPU 核数/内存总量 + CPU 使用率/进程堆内存），discovery 新增 metrics/batch 端点 + tb_gw_host_metrics 按天分区表，数据统计页出主机趋势图，为 v0.3 告警铺数据；写入 DEVELOPMENT-PLAN.md v0.2 第 10 项（总览概要 9→10 项） |
| 2026-11-06 | README 开头增加开发说明一行：「本平台由 zengguiwan 使用 CodeBuddy 独自开发完成。」 |
| 2026-11-06 | 列宽优化三处：网关总览「已接入服务实例」、数据统计「Top 服务」「Top 接口」——数字列收紧（QPS/请求数/错误率/占比 80、平均耗时/区间调用 100、归属应用 120、最后心跳 165），弹性余量由单列改双列分摊（实例表：服务名+实例地址；Top 服务：服务ID+服务名；Top 接口：接口+所属服务），消除单列独吞余量导致的大片空白。实测三表均无横向溢出、0 控制台错误 |
| 2026-11-06 | 平台名称统一：文档中「AI 网关平台（ai-gateway）」全部改为「API 网关平台（api-gateway）」——涉及 README / frontend README / backend README / DEVELOPMENT-PLAN / auth-design / 两个 SDK README 共 7 个文件 9 处；JWT 密钥默认值（ai-gateway-platform-jwt-secret-…）与日志文件名格式（日期--小时--ai-gateway--…）为技术值不改，避免文档与实际行为不符 |
| 2026-11-06 | README 新增「开发计划与进度」章节（默认账号与文档索引之间）：四版本进度简表（v0.1 ✅ 完成 + 三档计划概要）与三条明细入口（DEVELOPMENT-PLAN / PLAN / 未上线页面接真优先级），README 达 336 行 14 章 |
| 2026-11-06 | README「接入与配置」章首新增「接入架构总览」：ASCII 架构图区分三条线路（① 业务流量调用方→网关→业务系统、② SDK 实例状态上报→discovery、③ SDK 调用数据上报→trace，统一落 MySQL 由控制台消费）+ 三行线路说明表 |
| 2026-11-06 | README「接入与配置」章新增同级小节「接入与采集上报机制」：ASCII 全链路图（注册/心跳/logs/spans 四个上报端点 → 共享密钥校验 → 落库 → 巡检判离线）+ 五行机制表（注册幂等自愈、心跳判活、异步采集、traceId 串联、安全双门），并说明网关 TraceGlobalFilter 与 SDK 两套采集入口同表按 traceId 串联 |
| 2026-11-06 | README 面向使用者增强：①开头重写平台定位（一句话定位 + 四类痛点/方案/入口对照 + 典型使用流程）；②快速开始补验证步骤（status.sh）、启动顺序、日志查看与「种子数据开箱即用」说明；③新增「接入与配置」章：三端 SDK 接入三步对照表（含不接 SDK 的替代路径）、配置看哪里五类去向；README 达 249 行 / 12 章 |
| 2026-11-06 | README 新增「平台组成与子模块职责」「控制台菜单（8 大模块 21 项）」两章：前者按模块列职责与关键机制（proxy 转发+采集+热刷新+健康探测、user 账号权限 Key、overview 聚合、discovery 注册路由、trace 可观测、config 配置中心、三端 SDK），后者 21 菜单逐一列出路径/内容/上线状态（✅13 + 未上线 8，关联开发计划文档）；顺带补端口表漏掉的 config(28085) |
| 2026-11-06 | README 文档索引修复与扩充：修复表格中混入列表格式导致的渲染破损（PLAN/modules-deployment/maven-nexus 三条被折断）；按「计划与进度 / 部署与运维 / 设计与接入」分组重排，新增 DEVELOPMENT-PLAN.md（开发计划与进度计划）与 Node.js SDK 接入指南两条链接 |
| 2026-11-06 | DEVELOPMENT-PLAN.md 结构重构：版本总览大表只留一行式功能/Bug 概要，32 项功能与 9 项 Bug 细分移入各版本小节（v0.1 功能按 10 大块文字清单、Bug 用 9 行明细表，v0.2~v0.4+ 各自独立清单小节） |
| 2026-11-06 | DEVELOPMENT-PLAN.md 细化：依据 git 全量 50 次提交把 v0.1 功能列表扩为 10 大块 32 条详细事项、Bug 列表扩为 9 条（均含技术要点）；v0.2~v0.4+ 各计划项补充实现要点说明 |
| 2026-11-06 | 新增 docs/DEVELOPMENT-PLAN.md 开发计划与进度计划：7 列版本表（v0.1 已完成 2026-11-04~06：10 大功能块 + 8 项 Bug 修复；v0.2 = P0 缺口 8 项；v0.3 = P1 7 项；v0.4+ = P2 6 项，时间点均待定），版本划分与 PLAN 的 P0/P1/P2 对齐 |
| 2026-11-06 | 菜单悬浮的功能上线标记着色：[功能已上线] 绿色（#4ADE80）、[功能未上线] 灰色（rgba(255,255,255,.5)）；el-tooltip 由 content 纯文本改为 #content 插槽（一级菜单与分组子菜单两处），tipOf 拆为 tagOf（三态带色）+ statusTextOf；颜色写死而非 CSS 变量（tooltip 挂 body 下，深色底保证可读）。CDP 实测 computed color 生效、0 控制台错误 |
| 2026-11-06 | 侧边栏菜单悬浮提示增加功能上线标记，格式改为「菜单名[功能已上线/功能未上线] ｜ 后端服务 ｜ 运行状态」：portal 新增 MENU_FEATURE_STATUS 映射（21 项逐一盘点——占位组件或零接口调用判未上线：系统接入、上线发布、开放认证、登录方式、单点登录、令牌与会话、安全策略、角色管理；其余 13 项已上线；文档类页面不登记即不显示标记），Shell 透传 featureStatus、Sidebar.tipOf 三态拼接。CDP 实测 8 个菜单悬浮均按预期显示、0 控制台错误 |
| 2026-11-06 | 修复侧边栏「服务拓扑」缺少状态点与悬浮信息：该菜单 path 为 `/topology`（trace 模块下的独立一级路由），而 MENU_SERVICES 只登记了 `/trace`，最长前缀匹配不到 → dotOf 返回空 → 状态点不渲染、el-tooltip 被 disabled。补 `'/topology': 'jv-api-gateway-trace'` 后实测状态点为 UP、悬浮显示「服务拓扑 ｜ 后端服务：jv-api-gateway-trace ｜ 运行状态：运行正常（32ms）」 |
| 2026-11-06 | 网关总览与数据统计在 QPS 趋势后新增「入站 & 出站流量趋势」双折线图（各占整行）：后端 trendBuckets 增 SUM(req_bytes)/SUM(res_bytes)，TrendBuckets.build 输出 inboundValues/outboundValues（KB，1 位小数，与 QPS/请求量同一次查询）；TrendChart 扩展 values2/seriesName2（第二折线 + 图例 + tooltip 双行）与 scaleSecond 双 Y 轴（两序列量级差 3000 倍时小值序列不再贴底）——实测入站 ~1.5KB、出站 ~4000KB 两条线均清晰可读，图例/悬浮双值正常，0 控制台错误 |
| 2026-11-06 | 趋势图布局回调：网关总览与数据统计的「请求量趋势」「QPS 趋势」改为各自整行（此前并排一行图太窄），overview 页同时清掉无用的 `.grid-2` 残留样式；数据统计的方法分布 / 状态码分布仍保持并排 |
| 2026-11-06 | 数据统计页 Top 榜补归属维度：Top 服务增加「服务名」「所属应用」列，Top 接口增加「所属服务」「所属应用」列（StatsRow 增 appId/appName/serviceLabel；topServices 子查询取注册表中文名、topApis 联表 GROUP_CONCAT 去重多服务；新增 statsFiltersT 片段避免 JOIN 后列名歧义）。CDP 实测表头 6/7 列不换行、无横向溢出，首行「jv-api-gateway-overview / 网关总览 / API网关平台」 |
| 2026-11-06 | 数据统计页新增「QPS 趋势」：与「请求量趋势」并排一行（grid-2），共用一次查询——StatsController.trend 改 qps=true（values=QPS、requestValues=请求量，与网关总览同源同口径），前端请求量图取 requestValues、QPS 图取 values。实测两图并排显示、60 点、values=[0.0,0.2,0.1] / requestValues=[10,222,73] |
| 2026-11-06 | 应用编码再统一为 `app-api-gateway`（连字符风格）：5 个后端 yml、proxy 自注册硬编码、路由种子 SQL/注释示例，以及库内 5 张表（service/trace_log/trace_span/route/api）全部迁移；重启后注册表 6 服务 app_id=app-api-gateway 全健康、窗口期残留已清理 |
| 2026-11-06 | 应用编码 `app-gateway` → `app-api_gateway`（后统一为 `app-api-gateway`，见下条）：① 5 个后端 yml（user/overview/config/discovery/trace）；② proxy 自注册硬编码（顺带补 serviceId + 中文名「网关代理」，修复其名称被心跳刷回 ID）；③ 路由种子 SQL 与注释示例；④ 库内 5 张表（service/trace_log/trace_span/route/api）数据迁移。重启后新上报与注册表均为 app-api_gateway，三处 filters 显示「API网关平台（app-api_gateway）」，窗口期残留已清理 |
| 2026-11-06 | 冒烟测试数据归并到 regtech 应用：历史 app-smoke trace 记录迁入 app-regtech，service ID 对齐冒烟脚本现行值（node-smoke-service→test-node-smoke、py-smoke-service→test-py-smoke）；实测追踪页与服务发现页应用下拉完全统一（app-gateway + app-regtech） |
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
