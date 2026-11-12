# 开发计划与进度计划

> 项目：API 网关平台（project_api-gateway）
> 数据来源：`docs/PLAN.md` 变更记录、git 提交历史（50 次提交）、2026-11-06 功能完成度盘点
> 版本节奏：v0.1 为首个可演示版本；后续版本按 P0 → P1 → P2 缺口优先级排期，时间点待定

---

## 一、版本总览

| 版本 | 状态 | 开始时间 | 结束时间 | 参与人员 | 功能概要 | Bug 概要 |
| --- | --- | --- | --- | --- | --- | --- |
| **v0.1** | ✅ 已完成 | 2026-11-04 | 2026-11-06 | zengguiwan | 网关核心、平台微服务、三端 SDK、服务发现、可观测、网关总览、认证安全、配置中心、前端控制台、交付运维——**10 大块 32 项**，21 个菜单上线 13 个 | 修复 **9 项**：离线检测、SDK 接入三连修、采集口径、下拉重复、菜单映射、布局根因、数据归属清理等 |
| **v0.2** | 🚧 进行中（已交付 9 项） | 待定 | 待定 | zengguiwan | P0 缺口 **8 项** + 增值项，共 **20 项**：网关限流 ✅、令牌与会话 ✅、安全策略 ✅、登录方式 ✅、会话接真、安全策略 / 登录方式接真、鉴权精细豁免（新菜单）、开放认证约束、菜单权限过滤 + 账号平台授权 ✅、变更审计、列表数据导出、主机运行指标上报、部署关联与跨主机调用统计、拓扑页双视图、鉴权风控管理、日志打印管控、单点登录（SSO）、前端微前端化、分布式调度中心（清理 + 分区预建）✅、WebSocket 转发与会话采集 ✅、列表分页条统一（每页条数 + 上下页）✅、平台配置模糊搜索 ✅ | —— |
| **v0.3** | 📋 计划中 | 待定 | 待定 | zengguiwan | P1 **11 项**：调用量报表、发现控制台增强、追踪前端深化、告警体系、接入向导、角色收敛、统计导出（SSO 已提前至 v0.2）、工程规范落地（P3C/ESLint/约定式提交）、存量代码规范审查、AI 开发规范与流程、RPC 日志采集（2026-11-12 新增） | —— |
| **v0.4+** | 📋 长期规划 | 待定 | 待定 | zengguiwan | P2 **6 项**：灰度发布、多租户、Prometheus 可观测、KMS 密钥托管、测试与 CI/CD、正式多语言 SDK | —— |

> 各版本功能与 Bug 的详细清单见下文对应小节。

---

## 二、v0.1（已完成，2026-11-04 ~ 2026-11-06）

### 2.1 完成功能清单（10 大块 32 项）

**一、网关核心（jv-api-gateway-proxy）**

1. Spring Cloud Gateway 代理转发，统一鉴权 `AuthGlobalFilter`（token 验签、豁免路径、Redis/MySQL 双层缓存）
2. 调用链采集 `TraceGlobalFilter`：req/res 报文、耗时、入站/出站字节数双端采集
3. DB 动态路由：`tb_gw_route` CRUD + `DbRouteDefinitionLocator` 每 30 秒热刷新（改规则免重启）
4. 鉴权豁免全局控制位 `platform.auth.exemption.control`（CLIENT / PLATFORM）

**二、平台微服务（28081~28085）**

5. 五个业务模块：user / overview / config / discovery / trace，模块统一命名 `jv-api-gateway-*`
6. 系统配置服务：DB / Redis / Nacos 多来源托管，展示来源与平台/应用/服务归属，公开配置接口
7. 服务标识体系：服务ID（英文唯一）+ 服务名（中文显示），对齐应用ID/应用名模式，SDK 三端同步

**三、接入 SDK 三端**

8. Java client-starter：注册 / 心跳 / trace 采集，注册失败心跳周期自愈
9. Python SDK 补齐（协议与 Java 一致）
10. Node.js SDK（jv-gateway，零第三方依赖）：GatewayClient / TraceReporter / traceExpress + Express 示例
11. SDK 安全双门：上报共享密钥 `X-Gateway-Client-Token`（伪造注册/上报 401）+ SDK 入口鉴权验签（回调 verify + 缓存、豁免 / fail-open 可配）

**四、服务发现**

12. 注册表：register / heartbeat / deregister，30s 心跳、90s 判离线、HeartbeatMonitor 每 60s 巡检
13. 服务列表：全部应用 / 全部服务双下拉过滤、接口数与调用量列
14. 健康状态三态（健康 / 离线 / 异常）与颜色区分

**五、可观测三件**

15. 日志追踪（阶段一）：跨服务调用链采集与存储（trace_log / trace_span 按天分区 + 索引优化）、报文详情、应用/服务级联过滤
16. 服务拓扑（阶段二）：调用边预聚合（tb_gw_service_edge）+ 慢链路 TopN、全局拓扑 + 应用/服务过滤
17. 数据统计：请求量 / QPS / 入站出站流量三趋势、方法分布、状态码分布、Top 服务 / Top 接口（含服务名、所属应用归属维度）

**六、网关总览**

18. 核心指标卡、请求量 / QPS / 流量三张趋势折线图（各自整行）、运行时指标
19. 已接入服务实例按近 5 分钟调用量排名

**七、认证与安全**

20. 分组与权限：分组管理 / 部门组织 / 平台功能与菜单，permCodes 权限点下发
21. API Key 全生命周期：签发 / 启停 / 轮换 / 续期 / 吊销 + 操作审计（tb_gw_api_key_log）+ 统计看板（今日签发/续期等）
22. 开放认证：register / login / logout / token verify、双类型校验

**八、配置中心**

23. tb_sys_config 配置项管理 + 公开配置接口（无需登录读取开关）
24. 控制台自动刷新频率配置化 `platform.ui.refresh.interval`（总览/统计/发现接入，改值 30 秒生效，默认 10 秒）

**九、前端控制台**

25. 8 大模块 21 个菜单；认证与安全五个子菜单收敛为页内 Tab、接入中心菜单排序调整
26. TrendChart 折线图组件：Y 轴刻度、悬浮 tooltip、双序列（双折线 + 图例 + 双 Y 轴）
27. 四页统一时间统计维度（1 分钟 ~ 30 天共 10 档）、PaneTabs 分段控件、全局响应式适配
28. 侧边栏菜单健康标识：状态点 + 悬浮提示（后端服务 / 运行状态含延迟 / 功能上线标记绿灰着色）

**十、交付运维与文档**

29. 日志统一切分、一键构建启动脚本（scripts/start.sh / stop.sh / restart.sh）
30. docs/modules-deployment.md：服务拆解与按需启动、按模块售卖组装方案
31. Maven 私服一键部署（Nexus 3，init-nexus.sh）+ file:// 仓库 / 云制品库分发替代方案
32. 接入文档（client-integration.md）、配置信息一览（数据库/Redis/Nacos/JWT/环境变量）

### 2.2 修复 Bug 清单（9 项）

| # | Bug | 现象 | 修法 |
| --- | --- | --- | --- |
| 1 | 服务离线状态检测不到 | 离线服务无颜色标识、进程被杀后状态长期停留「健康」 | dotClass 补「离线」映射；deregister 由「异常」改「离线」；心跳巡检覆盖异常/亚健康状态（kill 后 10 秒内显红） |
| 2 | SDK 接入三连修 | 启动顺序敏感时注册失败；SDK 上报被网关 401 拦截导致实例永久离线；SDK 默认连前端端口 | 注册失败后心跳周期自动补注册；网关白名单放行上报通道；默认 server-addr 改指网关 28090 |
| 3 | 采集归属口径错位 | trace 表混入「配置中心」等中文值；服务发现 QPS/调用次数聚合落空 | SDK 统一走 `effectiveServiceId()` 上报；TraceLogDTO/SpanRecordDTO/SpanDTO 增 serviceId |
| 4 | 应用下拉重复 | 同一 appId 历史改名产生多行字典（如「网关平台」→「API网关平台」） | trace/discovery/overview 三处字典 SQL 按 app_id 取最新一条，名称优先取注册表 |
| 5 | proxy 中文名被刷回 | 注册表中文名「网关代理」被心跳刷回英文服务 ID | ProxySelfRegistrar 自注册补 serviceId + serviceName |
| 6 | 统计页分布卡片堆叠 | 「方法分布 / 状态码分布」不并排 | 根因：`.grid-2` 缺基础样式（仅有 1400px 单列覆盖）——补两列 grid 基础样式并抵消卡片纵向间距副作用 |
| 7 | 「服务拓扑」菜单无状态点 | 悬浮提示与绿点缺失 | MENU_SERVICES 漏登记 `/topology`（不以 /trace 开头，最长前缀匹配落空）→ 补映射 |
| 8 | 数据归属混乱 | 冒烟测试数据散落、应用编码两套写法 | 冒烟数据归并 regtech 应用；app-gateway → app-api-gateway 统一（5 张表迁移 + 窗口期残留清理） |
| 9 | 文档与代码不一致 | 接入文档与实际行为偏差 | 接入文档修正；Web 页面与文件文档全量对齐服务ID/服务名体系 |

---

## 三、v0.2 计划清单（P0 —— 影响「能交付」的缺口）

1. **网关限流**（首位，网关生产底线）（✅ 已完成 2026-11-12）：新增 `tb_gw_rate_limit`（维度 GLOBAL / ROUTE / API_KEY / IP，参数 replenish_rate / burst_capacity / requested_tokens，种子全局兜底 1000 次/秒 + 演示路由 20 次/秒）；网关 `RateLimitGlobalFilter`（order = HIGHEST+20，排在鉴权之后）每 5 秒加载启用规则（Caffeine 缓存），逐条匹配并独立扣令牌，任一不足即 429 + `Retry-After` + `X-RateLimit-Limit/Remaining/Reset`；`TokenBucketLimiter` 用 Redis Lua 脚本原子扣减（多实例共享桶，键 `gw:rl:{ruleId}:{subject}`），Redis 不可用时 30 秒内回落本地令牌桶；控制台「接入中心 → 限流策略」页支持新增 / 编辑 / 启停 / 删除 + 搜索 + 分页（权限点 onboard.ratelimit.view/edit 已种入并绑定网关运维组）；实测：IP 规则 5 次/秒桶 5 → 连打 12 次得到 `200×5 + 429×5 + 200(补充后) + 429`，429 头 `X-RateLimit-Limit: 5 / Remaining: 0 / Retry-After: 1`；ROUTE 规则 1 次/秒桶 1 → 30 并发 `1×200 + 29×429`；停用后 5 秒内恢复全 200；表与种子随 config 启动自动建好
2. **令牌与会话页接真**（✅ 已完成 2026-11-12）：JWT 增 `sid` claim，登录时登记会话到 Redis（`gw:sess:{sid}` 明细 + `gw:sess:user:{userId}` ZSET 索引，含 IP / UA / 设备指纹 / 平台 appId / 登录时间）；新增 `SessionService` + `SessionController`：`GET /auth/sessions`（在线列表）、`GET /auth/sessions/revokes`（最近 200 条吊销记录）、`POST /auth/sessions/kick`（四粒度踢人）；**多端登录控制**：`platform.session.mode`（MULTI 默认 / SINGLE 单端互踢）+ `platform.session.max`（默认 5），登录登记时按策略踢旧会话 / 超限踢最早（已种入 tb_sys_config，系统设置页可见）；**四粒度强制下线**：① SESSION 单会话（写 `gw:sess:revoked:{sid}`，网关 `AuthVerifier` 验签后命中即 401「该会话已被强制下线」）；② PLATFORM 按平台（appId 过滤，其他平台不受影响）；③ DEVICE 按端（设备指纹过滤）；④ ALL 全端（token_version +1）；前端「令牌与会话」页接真：指标卡（在线会话 / 在线账号 / 接入平台 / 吊销记录）、在线会话表（账号 / 平台 / 设备 / IP / 登录时间 / 在线时长 + 单会话 / 按端 / 按平台 / 全端踢）、吊销记录表，两 Tab 各自分页，已去掉「设计稿」标记；实测：单会话踢后旧 token 立即 401；PLATFORM console → affected 5 且同 token 立即 401；DEVICE "curl / 未知系统" → affected 1 且立即 401；连续登录 6 次在 max=5 下自动保留 5 个会话（最早被踢，记录「并发上限 5 个，自动踢最早会话」）；全端踢后 token_version +1、旧 token 报「账号已退出登录」；边界：踢人留痕暂写 Redis 吊销记录（最近 200 条），DB 审计表随第 8 项落地；「登录方式」字段待第 3/4/15 项（MFA / 登录方式开关 / SSO）补齐；SSO 联动随第 15 项
3. **安全策略页接真**（✅ 已完成 2026-11-12）：策略整体以 JSON 落库 `tb_sys_config.platform.security.policy`（`config_value` 扩为 TEXT），新增 `PolicyController`（`GET|PUT /policies/security`，路由 `/api/policies/**` → config）；登录流程由 `LoginPolicyService` 实时读取（10 秒缓存）：**失败锁定阈值 / 锁定时长动态化**（原硬编码 5 次 / 30 分钟）、**MFA 强制范围**（NONE / ADMIN / ALL）判定并以 `mfaRequired` 回传登录结果、密码通道开关联动；前端「安全策略」页接真（进入即加载库中策略、保存后提示「登录流程实时生效」、去掉设计稿标记）。实测：`mfaScope=ALL` → 登录返回 `mfaRequired=true`；策略保存后 DB 为 424 字节完整 JSON，页面回显 5 次 / 30 分钟 / 3 会话；边界：TOTP 验证码校验与 step-up 一次性令牌需用户绑定密钥，随 v0.3 落地；「敏感操作二次验证」当前仅落库（开关值已存，拦截待 v0.3）
4. **登录方式页接真**（✅ 已完成 2026-11-12）：登录因子与通道配置以 JSON 落库 `tb_sys_config.platform.login.methods`，新增 `GET|PUT /policies/login-methods`；登录接口按 `factors` 中 `key=username` 的开关实时拦截——关闭后密码登录返回 400「密码登录通道已关闭，请使用其他登录方式」；前端「登录方式」页接真（8 个因子开关 + 邮件 SMTP / 短信通道配置读写、保存即生效、去掉设计稿标记）。实测：关闭「用户名 + 密码」因子后登录被拒 400，恢复后立即正常；页面 8 个开关与库中配置一致（6 开 2 关）。边界：邮箱 / 短信验证码的**发送与校验**依赖 SMTP / 短信网关通道，配置已落库，通道测试与免密登录随 v0.3；扫码登录与 SSO 因子依赖第 15 项
5. 五个业务模块：user / overview / config / discovery / trace，模块统一命名 `jv-api-gateway-*`
6. 系统配置服务：DB / Redis / Nacos 多来源托管，展示来源与平台/应用/服务归属，公开配置接口
7. 服务标识体系：服务ID（英文唯一）+ 服务名（中文显示），对齐应用ID/应用名模式，SDK 三端同步

**三、接入 SDK 三端**

8. Java client-starter：注册 / 心跳 / trace 采集，注册失败心跳周期自愈
9. Python SDK 补齐（协议与 Java 一致）
10. Node.js SDK（jv-gateway，零第三方依赖）：GatewayClient / TraceReporter / traceExpress + Express 示例
11. SDK 安全双门：上报共享密钥 `X-Gateway-Client-Token`（伪造注册/上报 401）+ SDK 入口鉴权验签（回调 verify + 缓存、豁免 / fail-open 可配）

**四、服务发现**

12. 注册表：register / heartbeat / deregister，30s 心跳、90s 判离线、HeartbeatMonitor 每 60s 巡检
13. 服务列表：全部应用 / 全部服务双下拉过滤、接口数与调用量列
14. 健康状态三态（健康 / 离线 / 异常）与颜色区分

**五、可观测三件**

15. 日志追踪（阶段一）：跨服务调用链采集与存储（trace_log / trace_span 按天分区 + 索引优化）、报文详情、应用/服务级联过滤
16. 服务拓扑（阶段二）：调用边预聚合（tb_gw_service_edge）+ 慢链路 TopN、全局拓扑 + 应用/服务过滤
17. 数据统计：请求量 / QPS / 入站出站流量三趋势、方法分布、状态码分布、Top 服务 / Top 接口（含服务名、所属应用归属维度）

**六、网关总览**

18. 核心指标卡、请求量 / QPS / 流量三张趋势折线图（各自整行）、运行时指标
19. 已接入服务实例按近 5 分钟调用量排名

**七、认证与安全**

20. 分组与权限：分组管理 / 部门组织 / 平台功能与菜单，permCodes 权限点下发
21. API Key 全生命周期：签发 / 启停 / 轮换 / 续期 / 吊销 + 操作审计（tb_gw_api_key_log）+ 统计看板（今日签发/续期等）
22. 开放认证：register / login / logout / token verify、双类型校验

**八、配置中心**

23. tb_sys_config 配置项管理 + 公开配置接口（无需登录读取开关）
24. 控制台自动刷新频率配置化 `platform.ui.refresh.interval`（总览/统计/发现接入，改值 30 秒生效，默认 10 秒）

**九、前端控制台**

25. 8 大模块 21 个菜单；认证与安全五个子菜单收敛为页内 Tab、接入中心菜单排序调整
26. TrendChart 折线图组件：Y 轴刻度、悬浮 tooltip、双序列（双折线 + 图例 + 双 Y 轴）
27. 四页统一时间统计维度（1 分钟 ~ 30 天共 10 档）、PaneTabs 分段控件、全局响应式适配
28. 侧边栏菜单健康标识：状态点 + 悬浮提示（后端服务 / 运行状态含延迟 / 功能上线标记绿灰着色）

**十、交付运维与文档**

29. 日志统一切分、一键构建启动脚本（scripts/start.sh / stop.sh / restart.sh）
30. docs/modules-deployment.md：服务拆解与按需启动、按模块售卖组装方案
31. Maven 私服一键部署（Nexus 3，init-nexus.sh）+ file:// 仓库 / 云制品库分发替代方案
32. 接入文档（client-integration.md）、配置信息一览（数据库/Redis/Nacos/JWT/环境变量）

### 2.2 修复 Bug 清单（9 项）

| # | Bug | 现象 | 修法 |
| --- | --- | --- | --- |
| 1 | 服务离线状态检测不到 | 离线服务无颜色标识、进程被杀后状态长期停留「健康」 | dotClass 补「离线」映射；deregister 由「异常」改「离线」；心跳巡检覆盖异常/亚健康状态（kill 后 10 秒内显红） |
| 2 | SDK 接入三连修 | 启动顺序敏感时注册失败；SDK 上报被网关 401 拦截导致实例永久离线；SDK 默认连前端端口 | 注册失败后心跳周期自动补注册；网关白名单放行上报通道；默认 server-addr 改指网关 28090 |
| 3 | 采集归属口径错位 | trace 表混入「配置中心」等中文值；服务发现 QPS/调用次数聚合落空 | SDK 统一走 `effectiveServiceId()` 上报；TraceLogDTO/SpanRecordDTO/SpanDTO 增 serviceId |
| 4 | 应用下拉重复 | 同一 appId 历史改名产生多行字典（如「网关平台」→「API网关平台」） | trace/discovery/overview 三处字典 SQL 按 app_id 取最新一条，名称优先取注册表 |
| 5 | proxy 中文名被刷回 | 注册表中文名「网关代理」被心跳刷回英文服务 ID | ProxySelfRegistrar 自注册补 serviceId + serviceName |
| 6 | 统计页分布卡片堆叠 | 「方法分布 / 状态码分布」不并排 | 根因：`.grid-2` 缺基础样式（仅有 1400px 单列覆盖）——补两列 grid 基础样式并抵消卡片纵向间距副作用 |
| 7 | 「服务拓扑」菜单无状态点 | 悬浮提示与绿点缺失 | MENU_SERVICES 漏登记 `/topology`（不以 /trace 开头，最长前缀匹配落空）→ 补映射 |
| 8 | 数据归属混乱 | 冒烟测试数据散落、应用编码两套写法 | 冒烟数据归并 regtech 应用；app-gateway → app-api-gateway 统一（5 张表迁移 + 窗口期残留清理） |
| 9 | 文档与代码不一致 | 接入文档与实际行为偏差 | 接入文档修正；Web 页面与文件文档全量对齐服务ID/服务名体系 |

---

## 三、v0.2 计划清单（P0 —— 影响「能交付」的缺口）

1. **网关限流**（首位，网关生产底线）（✅ 已完成 2026-11-12）：新增 `tb_gw_rate_limit`（维度 GLOBAL / ROUTE / API_KEY / IP，参数 replenish_rate / burst_capacity / requested_tokens，种子全局兜底 1000 次/秒 + 演示路由 20 次/秒）；网关 `RateLimitGlobalFilter`（order = HIGHEST+20，排在鉴权之后）每 5 秒加载启用规则（Caffeine 缓存），逐条匹配并独立扣令牌，任一不足即 429 + `Retry-After` + `X-RateLimit-Limit/Remaining/Reset`；`TokenBucketLimiter` 用 Redis Lua 脚本原子扣减（多实例共享桶，键 `gw:rl:{ruleId}:{subject}`），Redis 不可用时 30 秒内回落本地令牌桶；控制台「接入中心 → 限流策略」页支持新增 / 编辑 / 启停 / 删除 + 搜索 + 分页（权限点 onboard.ratelimit.view/edit 已种入并绑定网关运维组）；实测：IP 规则 5 次/秒桶 5 → 连打 12 次得到 `200×5 + 429×5 + 200(补充后) + 429`，429 头 `X-RateLimit-Limit: 5 / Remaining: 0 / Retry-After: 1`；ROUTE 规则 1 次/秒桶 1 → 30 并发 `1×200 + 29×429`；停用后 5 秒内恢复全 200；表与种子随 config 启动自动建好
2. **令牌与会话页接真**（✅ 已完成 2026-11-12）：JWT 增 `sid` claim，登录时登记会话到 Redis（`gw:sess:{sid}` 明细 + `gw:sess:user:{userId}` ZSET 索引，含 IP / UA / 设备指纹 / 平台 appId / 登录时间）；新增 `SessionService` + `SessionController`：`GET /auth/sessions`（在线列表）、`GET /auth/sessions/revokes`（最近 200 条吊销记录）、`POST /auth/sessions/kick`（四粒度踢人）；**多端登录控制**：`platform.session.mode`（MULTI 默认 / SINGLE 单端互踢）+ `platform.session.max`（默认 5），登录登记时按策略踢旧会话 / 超限踢最早（已种入 tb_sys_config，系统设置页可见）；**四粒度强制下线**：① SESSION 单会话（写 `gw:sess:revoked:{sid}`，网关 `AuthVerifier` 验签后命中即 401「该会话已被强制下线」）；② PLATFORM 按平台（appId 过滤，其他平台不受影响）；③ DEVICE 按端（设备指纹过滤）；④ ALL 全端（token_version +1）；前端「令牌与会话」页接真：指标卡（在线会话 / 在线账号 / 接入平台 / 吊销记录）、在线会话表（账号 / 平台 / 设备 / IP / 登录时间 / 在线时长 + 单会话 / 按端 / 按平台 / 全端踢）、吊销记录表，两 Tab 各自分页，已去掉「设计稿」标记；实测：单会话踢后旧 token 立即 401；PLATFORM console → affected 5 且同 token 立即 401；DEVICE "curl / 未知系统" → affected 1 且立即 401；连续登录 6 次在 max=5 下自动保留 5 个会话（最早被踢，记录「并发上限 5 个，自动踢最早会话」）；全端踢后 token_version +1、旧 token 报「账号已退出登录」；边界：踢人留痕暂写 Redis 吊销记录（最近 200 条），DB 审计表随第 8 项落地；「登录方式」字段待第 3/4/15 项（MFA / 登录方式开关 / SSO）补齐；SSO 联动随第 15 项
3. **安全策略页接真**（✅ 已完成 2026-11-12）：策略整体以 JSON 落库 `tb_sys_config.platform.security.policy`（`config_value` 扩为 TEXT），新增 `PolicyController`（`GET|PUT /policies/security`，路由 `/api/policies/**` → config）；登录流程由 `LoginPolicyService` 实时读取（10 秒缓存）：**失败锁定阈值 / 锁定时长动态化**（原硬编码 5 次 / 30 分钟）、**MFA 强制范围**（NONE / ADMIN / ALL）判定并以 `mfaRequired` 回传登录结果、密码通道开关联动；前端「安全策略」页接真（进入即加载库中策略、保存后提示「登录流程实时生效」、去掉设计稿标记）。实测：`mfaScope=ALL` → 登录返回 `mfaRequired=true`；策略保存后 DB 为 424 字节完整 JSON，页面回显 5 次 / 30 分钟 / 3 会话；边界：TOTP 验证码校验与 step-up 一次性令牌需用户绑定密钥，随 v0.3 落地；「敏感操作二次验证」当前仅落库（开关值已存，拦截待 v0.3）
4. **登录方式页接真**（假页面优先 2）：密码 / 验证码 / 扫码 / SSO 因子开关落库，登录接口按开关启用禁用（密码关掉时返回明确错误码）。**各方式实现口径（功能完整开发，外部通道不联调）**：
    - **密码登录验证码（两种形态，可配置切换）**：① 随机图片验证码——随机字符生成图片，带角度矫正 / 扭曲 / 干扰线防机器识别；② 弹框动态码——弹框上方展示 6 位随机动态码（前端渲染为不可复制、不可选中，**30 秒自动更换**，过期/刷新即重生成），弹框下方为手动输入框；两种形态均可由登录方式页配置选择
    - **短信验证码**：完整开发功能链路（发送按钮 + 60s 倒计时、输入校验、验证码生成与有效期管理）；**发送通道以「未接入」状态存在——验证码落入平台日志（开发联调可见），不接短信网关、不做连通性测试**；后续接入真实通道只需替换发送实现，界面与校验逻辑零改动
    - **扫码登录（两种验证形态，可配置切换）**：① **演示确认流**——登录页生成带时效的二维码 → 轮询扫码状态 → 确认后登录，扫码确认端以平台内演示 / mock 实现（不依赖外部扫码 App，不验证连通性）；② **扫码取码 + 手动输入流**——登录页二维码内容即 6 位动态码（**30 秒自动更换**，与弹框动态码同源机制），用户用手机设备任意扫码工具扫描读取动态码，在二维码**旁边的手动输入框输入该动态码完成验证**——无需任何外部 App 回调与连通性依赖，纯「读码 + 输入」闭环；两种形态均由登录方式页配置选择
    - **邮件验证码**：走 SMTP（可配，无配置时该开关灰显）
5. **鉴权精细豁免管理（新菜单）**：按应用 / 服务 / 接口维度配置校验豁免（加白理由、有效期、审计人），平台端落库并下发（client 配置中心拉取或网关下发）；CLIENT/PLATFORM 控制位切换时两侧配置生效关系明确
6. **开放认证调用方约束**：调用方白名单 / IP 限制 / 每分钟限流，复用 API Key 作为调用凭证；**每分钟限流复用第 1 项的限流组件（同一 Redis 令牌桶实现，只是作用对象为开放接口），不另造一套**；**Key 平台/服务作用域**——Key 表增加可用平台/服务白名单（沿用 appName 归属扩展），网关转发前校验目标服务是否在 Key 授权范围内，越界 403，防 Key 泄漏后跨平台横向滥用
7. **菜单按权限动态渲染 + 账号平台授权**（✅ 已完成 2026-11-12）：permCodes 已下发到前端，补菜单过滤逻辑（当前全量可见，纯前端改动）；**① 菜单按权限动态渲染（✅ 已完成 2026-11-12）**——8 个模块的 menu 声明补 `perm` 字段（18 处，与 `tb_perm_item` 的 DISPLAY 权限码一致：`/auth/user→auth.user.view`、`/route-config→onboard.route.view`、`/auth/api-keys→open.key.view` 等）；portal 装配层新增 `MENU_PERM_MAP`（path → 权限码）与 `filterMenusByPerms`（未声明 perm 的菜单默认可见，组内可见子项为空则整组剔除）；user store 新增 `permCodes` 缓存与 `loadPerms()`（登录会话内拉一次 `/auth/my-perms`，失败不置位、下次导航重试，登出清空）；路由守卫每次导航 `await loadPerms`，目标菜单声明 perm 且用户无权限 → 重定向到首个有权限的菜单（无任何权限则回登录页）。实测：admin（超级管理员 builtin 分组，44 个权限码）26 项菜单全量可见；只读观察者分组仅授 overview / stats / trace / discovery 四码，登录后菜单收窄为 10 项（认证与安全、分组与权限整组隐藏，系统管理组仅剩无权限点管理的调度计划），直接访问 `/auth/user`、`/perm/menu` 均重定向 `/overview`；测试账号已清理。
    **② 账号-平台（应用）授权（✅ 已完成 2026-11-12）**——新增 `tb_gw_open_platform`（平台登记，种子 3 个演示平台：齐墨ERP / 数据中台 / 报表达人）与 `tb_gw_account_platform`（账号-平台授权，`uk_user_app` 唯一）两张表，由 PermSchemaInitializer 启动建表；后端 `OpenPlatformController`：`GET /open/platforms`（清单）、`GET /open/accounts/{id}/platforms`（已授权）、`PUT /open/accounts/{id}/platforms`（全量覆盖，不存在的 appId 自动忽略、空数组清空、`@Transactional`）；`OpenVerifyRequest` 增可选 `appId`，`/open/auth/token/verify` 带 appId 时校验「平台存在且启用」+「账号已授权」，未授权返回 `valid=false`（reason「账号未授权访问平台「xxx」」），不传 appId 保持原行为（向后兼容）；前端用户管理页操作列新增「平台授权」按钮与对话框（平台清单多选、停用项置灰、回显已授权）。实测：未授权 zimo-erp → valid=false；PUT 授权 zimo-erp + bi-report（not-exist 被忽略）→ verify 通过并返回用户身份与权限点；data-center 未授权仍拒绝；不存在 appId → 403「目标平台不存在」；关系表按账号正确落库、重开对话框勾选回显正确。边界：**应用维度数据可见性过滤（未授权平台的数据视图不可见）暂未涉及**——当前各页面没有按 appId 切分的数据视图，待第 10/11 项数据链与第 15 项 SSO 落地时统一接入
8. **配置 / 路由变更审计**：复用 tb_gw_api_key_log 审计表模式，记录「谁在何时改的、改前值」，配置页与路由页增加变更记录查看
9. **列表数据导出（服务发现 / 日志追踪）**：两页筛选区「重置」按钮后新增「下载」按钮，按当前筛选条件导出列表，支持 Excel（.xlsx）/ CSV / TSV / HTML 四种格式；实现方式（前端 Blob 生成 或 后端流式下载接口）与导出上限（防止全量拖库）排期时评审
10. **接入方主机与运行指标上报（历史趋势方案）**：SDK 新增独立定时上报（60s）+ discovery 新增 `POST /api/discovery/metrics/batch` 端点 + 新表 `tb_gw_host_metrics`（按天分区，参照 trace_log 做法）；数据统计页新增主机资源趋势图，为 v0.3 告警阈值判断铺数据基础。**指标按六大类采集**：
    - **CPU**：使用率 %（系统/进程）、核数、架构、1 分钟负载均值
    - **GPU**（NVIDIA 场景可选上报）：GPU 数量、利用率 %、显存使用率 % 与总量、温度——Python 走 pynvml，Java/Node 通过 nvidia-smi 子进程解析，无 GPU 环境自动跳过
    - **内存**：总量、系统使用率 %、进程 RSS、JVM 堆使用率 % / GC 次数与耗时 / 线程数 / fd 数（Node 对应 event loop lag 与 heapUsed，Python 对应线程与 fd 数）
    - **磁盘**：分区总量与使用率 %、读写吞吐（B/s）、读写 IOPS、设备利用率 %（Java 用 OSHI，Python 用 psutil.disk_io_counters，Node 解析 /proc/diskstats）
    - **网络**：网卡收发字节数速率、收发包速率、收发错误/丢包计数、TCP 已建立连接数（Java 用 OSHI，Python 用 psutil.net_io_counters，Node 解析 /proc/net/dev）
    - **IO**：随磁盘 IOPS/吞吐覆盖；进程级 io 计数（Python psutil.Process.io_counters）作为增强项
    - 另含主机静态维度（hostname / OS / 版本）注册时上报、随心跳刷新；单机指标开关可配（`metrics.enabled`，默认开），无 GPU 自动降级不报
11. **部署关联与跨主机调用统计**：基于第 10 项的主机数据建立三方关联——服务实例（`tb_gw_service` 的 ip:port）↔ 主机/pod（hostname，K8s 下 hostname 即 pod name、IP 即 pod IP）↔ 调用记录（`tb_gw_trace_log` 已有 req_ip/res_ip 两端地址）；产出：① 部署清单视图——每台主机/pod 上跑了哪些服务实例（含资源水位），② 跨主机调用统计——按「源 IP → 目标 IP」聚合调用量/耗时/错误率，③ 单点风险评估——某主机故障时受影响的实例与服务清单（影响面分析）。
    **边界（明确不做）**：不做物理网络拓扑——平台不采集机柜号、交换机/路由器等网络设备与连线数据，也没有 3D 机房展示；没有机房 CMDB 数据源之前，任何「网络物理拓扑图」都是用调用关系伪造的，不做。本项产出的是「部署 + 调用」两个真实维度的统计视图，不做超出数据支撑的可视化
12. **服务拓扑页双视图：服务调用拓扑 / 主机 pod 调用拓扑**：服务拓扑页（/topology）增加视图切换（Tab 或分段控件）——「服务调用拓扑」即现有视图（服务节点 + 调用边）；新增「主机 pod 调用拓扑」视图，节点为主机 / pod（K8s 下 hostname 即 pod name、IP 即 pod IP），边为「源主机 → 目标主机」的聚合调用（调用量 / 耗时 / 错误率），数据来自第 11 项的跨主机调用聚合与部署关联（每台主机/pod 上跑了哪些服务实例在节点详情展示）；单机 / 单 pod 保留过滤与影响面下钻
13. **鉴权风控管理（用户 / API Key）**：在现有登录限流、账号解锁基础上升级为风控体系——① 用户维度：登录失败策略（连续失败锁定、失败后验证码/延时加码）、异常登录识别（陌生 IP、非工作时段、高频尝试）打风险标记、处置动作（禁用、强制下线，复用 token_version 机制）；② API Key 维度：调用量突增 / 错误率异常监控，超阈值自动停用（熔断式）、临期与长期未用 Key 预警清单（Key 表补「最后使用时间 + 最近调用来源 IP」记录，预警消费）。**设计口径**：API Key 与开放认证 token 为机器凭证，天然多实例并行调用，不做「单端/多端登录」的端数控制——控制维度为限流（第 1 项）+ IP 白名单（第 6 项）+ 调用监控与熔断（本项），记录维度为调用审计（tb_gw_api_key_log）+ 最后使用时间；③ 风控事件列表：用户与 Key 的风险事件统一登记、查看、处置（解锁 / 恢复 / 注销），处置留痕进审计。**前置依赖与数据基础**：依赖第 2 项的会话登记（风控事件展示在线会话、强制下线复用同一会话体系）与第 8 项的审计基础设施（处置留痕复用同一审计表模式）；「异常登录识别（陌生 IP）」需要登录历史数据支撑，新增登录记录表（tb_login_log：用户 / IP / UA / 时间 / 成败 / 风险标记），登录成功与失败时同步落一条——该表同时是第 2 项会话页「最近登录」的数据源，一次建表两处消费
14. **日志打印管控与处理**：① 平台侧：统一日志规范（级别 / 格式 / traceId 贯穿），关键节点（鉴权、转发、采集、上报）日志打印策略——采样与限速防高频刷屏；② 日志级别动态调整：logback 配置热更新（按模块 / 包维度在线调级，免重启）；③ 敏感信息脱敏：token、手机号、密码等字段打印时掩码；④ 日志量管控：单模块日志量监控与滚动清理策略；⑤ SDK 侧：注册 / 心跳 / 上报等关键节点打印可开关（log.level 联动），ERROR 日志作为 v0.3 告警体系的数据源预留
15. **单点登录（SSO，OIDC Provider）**（自 v0.3 提前）：网关平台作为认证中心（IdP），业务平台作为接入方（SP）接入——① IdP 协议端点：`/authorize`（授权码发放）、`/token`（code 换 id_token/access_token）、`/userinfo`、`/.well-known/openid-configuration` 元数据发现 + jwks 公钥发布（id_token 即 JWT，签发验签复用现有密钥体系）；② 接入方登记（「单点登录」设计稿页接真）：client_id / client_secret / 回调地址登记与配置加密存储；③ 授权校验：换 token 时查第 7 项账号-平台授权关系（认证了 ≠ 能用，未授权平台拒绝）；④ 统一登录页复用第 4 项四形态（密码 + 图片/动态码验证码 + 扫码 + 短信）；⑤ 单点登出：IdP 会话失效（token_version 自增）→ 各平台本地会话随之失效
16. **前端微前端化：8 模块独立构建独立部署（Module Federation，方案 A）**：源码已 monorepo 化（modules/* 各自独立包 + 声明式 menu/routes），改造为运行时组装——① 各模块 vite 配置 federation exposes（暴露 `./module`，即现有 menu/routes 导出，模块源码基本不动）；② portal 从静态 import 改为运行时按「模块地址表」动态加载远程 remoteEntry.js，含加载失败占位兜底（单模块故障不白屏）；③ vue / vue-router / pinia / element-plus / @zimo/shared / @zimo/ui 配置 shared 单例（登录态全局唯一，总体积不增反降）；④ 部署形态：portal 壳 + 8 模块各自 dist 独立发布，改单模块只发单模块，与后端模块化部署配套实现按模块交付；⑤ 本地开发一键脚本（壳 + 模块多服务）。**代价与约定**：shared/ui 升级需全模块重新构建对齐（严格 pin 版本）；模块间保持 SPA 路由切换体验不变；与第 7 项菜单权限过滤兼容（菜单先本地声明、权限过滤在壳层做）
17. **分布式调度中心（新子模块 jv-api-gateway-scheduler，端口 28087）**（✅ 已完成 2026-11-07）：① **分布式锁与配置双存储**——Redis 为第一优先级（SETNX 分布式锁 + 配置缓存），MySQL 兜底（锁表 tb_scheduler_lock 过期重抢 + tb_sys_config 持久双写），Redis 不可用自动降级仍可单点调度；② **历史数据清理任务**——每天定时跑一次（执行时间=系统设置页新增「调度时间」配置项，tb_sys_config `scheduler.clean.run-time`，默认 02:00），**物理删除**保留期（默认 7 天）之外的采集日志与记录表数据：tb_gw_trace_log、tb_gw_trace_span、tb_gw_api_key_log、tb_gw_service_edge（清理清单配置化 tb_scheduler_clean_table，后续新增采集表只需加一行配置）；③ **分区预建任务**——每天为分区表（tb_gw_trace_log、tb_gw_trace_span）提前 2 天生成每日分区执行计划（落 tb_scheduler_plan）并执行 ADD PARTITION，分区配置（分区粒度/提前天数/保留策略）入 tb_scheduler_partition_config，与第 10 项主机指标新表自然衔接（入配置清单即纳入管理）；④ **新菜单「系统管理 → 调度计划」**——调度任务列表展示，点击记录弹框展示任务状态详情（上次执行时间/状态/行数）、执行计划列表、执行记录列表，支持手动触发；网关注册路由 /api/scheduler/** → 28087

18. **WebSocket 转发与会话采集（proxy 增强）**（✅ 已完成 2026-11-09）：① 路由表 upstream_url 填 http:// 即可代理 WebSocket——客户端携带 `Upgrade: websocket` 握手时 Spring Cloud Gateway 自动 http→ws 协议转换转发（实测文本/二进制帧双向收发正常）；② **会话级旁路采集**——自定义 @Primary WebSocketService（装饰 HandshakeWebSocketService）+ CapturingSession 包装入站会话：receive()（客户端→下游）/send()（下游→客户端）双向旁路统计消息数与字节数、保存双向首帧样本（文本帧取前 N 字节 UTF-8，二进制帧标记 [binary n bytes]，`gateway.trace.ws-sample-bytes` 可配，默认 2048），纯观察不重写不影响转发；③ **落库 tb_gw_ws_session**——连接起止时间/时长、双向计数与字节数、首帧样本、关闭状态（NORMAL/ABNORMAL），trace_id 关联握手请求的日志追踪记录（X-Gateway-Trace-Id），采集队列+每秒批量落库与 TraceSink 同模式（有界队列满即丢，不阻塞转发）；④ **帧级明细落库**——每帧一行（方向 IN/OUT、类型 TEXT/BINARY、payload 样本：文本 UTF-8 / 二进制 hex 前 N 字节、原始字节数、到达时间）写 tb_gw_ws_frame，按 session_key 关联会话；开关与上限可配（gateway.trace.ws-frame-log / ws-frame-sample-bytes / ws-frame-max-per-session，默认 true / 1024 / 200）；⑤ **日志追踪页新增「WebSocket 会话」视图**——会话列表（时间/路径/上游/双向帧数与字节/关闭状态）+ 帧明细抽屉（方向徽标、payload），后端查询接口 GET /trace/ws/sessions、/trace/ws/frames；⑥ 顺带修复 SSE 响应体不入追踪日志问题（BodyCaptureResponse 补 writeAndFlushWith 旁路捕获）；⑦ **采集表纳入每日优化（调度中心，衔接第 17 项）**——tb_gw_ws_session / tb_gw_ws_frame 加入清理清单 tb_scheduler_clean_table（保留 7 天，time_column：conn_start / recv_time），tb_gw_ws_frame 重建为按天分区表（PK 含分区列 recv_time，与 tb_gw_trace_span 同模式）并加入分区预建配置 tb_scheduler_partition_config（提前 2 天）；SSE 数据存于 tb_gw_trace_log.res_body，天然在既有清理/分区内。实测：手动触发 partition-daily 自动为帧表生成 p20261111 分区、data-clean 含 6 张采集表执行 SUCCESS。新建表见 backend/sql/jv-api-gateway-ws-session-tables.sql、jv-api-gateway-ws-frame-tables.sql（含调度配置 INSERT）

19. **列表分页条统一增强（每页条数切换 + 上/下一页）**（✅ 已完成 2026-11-11）：新增 `@zimo/ui/GwPager` 共享分页组件——① **每页条数下拉**（10 / 20 / 50 / 100 个/页），切换后自动回到第 1 页并重新请求；② **上一页 / 下一页按钮**，首页时「上一页」禁用、末页时「下一页」禁用，中间页可点；③ 页码「第 x / y 页」按 `ceil(total / size)` 实时计算，总数以「共 N 条」展示；④ 样式沿用平台 `--gw-*` 设计变量，作为共享组件由 `@zimo/ui` 统一出口导出。**第一批（后端分页，4 个页面）**：日志追踪-调用日志（trace/Index.vue）、日志追踪-WebSocket 会话（trace/WsSessions.vue）、用户管理（auth/user/Index.vue）、服务发现（discovery/Index.vue）——替换原 `el-pagination`（仅有页码、无条数选择，旧文案「每页 N 条」一并清除），各页新增 `onSizeChange`（改条数 → `current=1` → 重新请求）；**第二批（前端切片分页，7 个列表）**：令牌与会话-在线会话、令牌与会话-令牌吊销记录（两个 Tab 各自独立分页）、API Key 列表、系统接入-配置项、路由配置、系统设置-配置项、调度计划-调度任务——接口保持全量拉取不改后端分页参数，页面维护 `page / size` 并按页 `slice` 展示，总计以「共 N 条」呈现；关键词 / 来源筛选变化自动回到第 1 页；系统接入与路由配置的分页条位于 `gw-card-bd` 内，用负 margin 抵消 16px 内边距并补 `border-top`，与卡片左右边缘对齐；调度计划分页条右对齐于表格下方并显示「共 N 个调度任务」。实测：调用日志 147984 条（14799 页）翻页 / 切 20・50 条页码同步刷新；系统接入 13 个配置项 2 页（第 2 页 3 行、切 20 条 → 1 页 13 行）；路由配置 40 条 4 页翻页正常；用户管理 / 系统设置 / 调度计划 / API Key / 吊销记录仅 1 页时上下页正确置灰；全项目已无 `el-pagination` 与旧分页文案残留

20. **平台配置模糊搜索（系统设置）**（✅ 已完成 2026-11-11）：系统设置「平台配置」卡片头部新增搜索框（放大镜前缀图标 + 可清空），输入即实时模糊过滤、不区分大小写；搜索覆盖 8 个字段——**配置名（key）/ 配置参数值（value）/ 配置描述（remark）**，以及平台名、应用名、服务名、值类型、来源标签；与来源筛选（全部 / MySQL / Redis / Nacos）叠加生效（先按来源过滤，再按关键词匹配）；搜索词变化自动回到第 1 页，分页总数与「共 N 项（已筛选）」提示同步刷新；无结果时展示「没有匹配的配置项」空态（与「暂无该来源的配置」区分）。实测：输入 schedule → 仅剩 scheduler.clean.run-time（1 项）；输入 open → 仅剩 platform.open.auth.enabled；输入 网关 → 6 项全中（平台名命中）；输入 zzzz → 0 项 + 空态。实现为纯前端过滤——`/configs` 仍全量返回（配置值托管 DB / Redis / Nacos 均随列表返回，搜索无遗漏），后端接口与表结构无需变更

**实现顺序与依赖（保证 14 项协同工作，无缺口）**：

```
基础层先行：  8 变更审计 →（13 处置留痕复用）      1 限流 →（6 开放接口限流复用）
鉴权链：      3 安全策略（策略表）→ 4 登录方式（消费同一开关模型）；2 会话 → 13 风控（会话/强制下线复用）；15 SSO 依赖 2（会话即 IdP 会话）+ 7（平台授权校验）+ 4（统一登录页复用）
数据链：      10 主机指标 → 11 部署关联 → 12 拓扑双视图（严格顺序，后项消费前项数据）
独立可并行：  5 豁免管理、7 菜单过滤、9 列表导出、14 日志管控、16 前端微前端化（与 7 协同：菜单声明与权限过滤分层）
```

**协同约定**：① 第 5、13 项新增的页面（豁免管理、风控事件）必须在开发时同步登记菜单与权限点，纳入第 7 项的菜单按权限过滤体系，避免「新页面绕过权限」；② 第 13 项新建的登录记录表由第 2 项与第 13 项共用；③ 全部新表（tb_gw_host_metrics、tb_login_log、审计扩展）随对应条目出 ALTER/建表脚本，纳入 docs/configuration.md 数据表清单

---

## 四、v0.3 计划清单（P1 —— 体验与运维）

1. **调用量报表页**：按 Key / 路由 / 状态码聚合 trace 数据出报表（tb_gw_api 表已有，聚合接口与页面待建）
2. **服务发现控制台增强**：实例手动下线 / 权重调整 / 健康剔除的可视化操作（当前只能被动等心跳超时）
3. **日志追踪前端深化**：高级筛选组合、链路瀑布图详情、慢调用定位（后端 span/log/拓扑接口已就绪）
4. **告警体系**：规则引擎（QPS 阈值 / 错误率 / 服务离线触发）+ 通知渠道（邮件 / Webhook）；总览页已有告警设计稿
5. **系统接入页升级**：从静态文档向导改为真实接入向导（选应用 → 生成依赖坐标与 yml 片段 → 校验注册结果）
6. **角色管理语义收敛**：与分组权限（PERM 分组）合并，或补独立角色 CRUD（当前为占位页）
7. **数据统计导出**：报表导出 Excel/CSV
8. **工程规范落地（规约工具链）**：Java 引入《阿里巴巴 Java 开发手册》P3C 规则集（SpotLess + Checkstyle 绑定 Maven 构建，违规即编译警告）；前端 ESLint（Vue3 规则集）+ Prettier + `.editorconfig`；commit message 收敛为约定式提交（`type: 描述`）；版本收尾跑规约扫描作为发布冻结门禁（与第 7 项测试与 CI/CD 衔接）
9. **存量代码规范审查**：对后端 5 模块 + SDK 三端跑一轮《阿里巴巴 Java 开发手册（黄山版）》规约扫描，违规按 Blocker / Critical / Major 分级输出清单，Blocker 与 Critical 项修复，Major 项排期（当前项目无任何规范工具链，编码惯例隐式符合但无强制保障）
10. **AI 开发规范与流程（项目元规范）**：将本项目确立的 AI 协作开发方式固化为开发手册——① 项目规划规则：需求登记流程（提需求→方案与量级→拍板→写入版本清单并四处同步）、版本分界空提交、计划项缺口审查与依赖标注、诚实边界原则（不伪装可用、不做超出数据支撑的可视化）；② AI 编码规范：分层结构与命名、统一响应体、配置外置等隐式惯例显式化，文档改动验证落盘后提交；③ 测试验收规范：代码改动重启验证、UI 改动实测渲染、每版本功能验收清单；④ 文档生成规范：README / DEVELOPMENT-PLAN / PLAN.md 维护规则、变更登记四处同步、文档与实际行为一致性约束

11. **RPC 日志采集（Dubbo / gRPC / 出站 HTTP body）**（2026-11-12 新增）：当前 SDK 仅覆盖 HTTP 系采集（入站 Servlet Filter 含 req/res body、WebSocket 帧、SSE），RPC 链路完全无采集（`tb_gw_trace_log.protocol` 恒为 HTTP/1.1，全库无 Dubbo/gRPC 依赖）——① **Dubbo**：新增 `org.apache.dubbo.rpc.Filter`，调用前后取 `RpcInvocation` 参数与 `Result`，JSON 序列化为 req_body / res_body（序列化失败或大数据量降级为只记接口名 / 方法名 / 参数个数）；② **gRPC**：新增 `ServerInterceptor`，用 protobuf `JsonFormat` 转文本入库（无描述符反射能力时 body 退化为方法名 + hex 元数据）；③ **出站 HTTP 调用 body**：扩展现有 `TracePropagationInterceptor`（CLIENT span 目前只记 method + URI + status + cost，无 body）——请求 body 拦截器已持有 `byte[]`，响应体包装回填，`tb_gw_trace_span` 增加 req_body / res_body 两列；④ **协议标识统一**：`tb_gw_trace_log.protocol` 增加取值 `DUBBO / GRPC / HTTP`（当前恒存 HTTP/1.1），前端日志追踪页协议筛选与展示同步；RPC 客户端依赖（dubbo / grpc）是否引入及量级在排期时评审，遵循 v0.2 第 14 项日志管控的采样与限速策略，body 采集沿用 max-body-size 截断与脱敏规则

---

## 五、v0.4+ 长期规划（P2 —— 长期演进）

1. **灰度发布 / 蓝绿部署**：tb_gw_route 路由模型扩展 version / weight 字段，按比例分流
2. **多租户**：账号、Key、路由、统计全链路增加 tenant_id 隔离
3. **可观测性升级**：Prometheus 指标暴露（QPS/RT/错误率/连接数）、链路采样率配置、日志聚合（ELK/Loki）
4. **JWT 密钥托管**：KMS 接入与密钥定期轮换（当前走环境变量 / 配置文件）
5. **质量与工程化**：自动化测试基线（后端单测 / 集成测试、前端 E2E）、CI/CD 流水线（替代本地 restart.sh）
6. **多语言正式 SDK**：Python / Node 从冒烟级升级为正式发布版（含文档与版本管理）

---

## 六、说明

### 版本划分依据

- **v0.1（2026-11-04 ~ 2026-11-06，32 项功能 / 9 项 Bug）**：以「平台能跑通、能演示」为目标，git 50 次提交、PLAN.md 26 条变更记录归类。当前 21 个菜单中 13 个已上线（有真实后端交互）、8 个未上线（占位页 / 静态页，见悬浮灰标）。
- **v0.2**：对应 PLAN.md 的 **P0 缺口**（影响「能交付」的项），主线是两件事——网关生产底线能力（限流、会话管理）和把 4 个「假页面」接真；另含用户点名的增值项（列表导出、主机运行指标上报、部署关联与跨主机调用统计、拓扑页双视图、鉴权风控管理、日志打印管控、单点登录 SSO、前端微前端化、分布式调度中心）。
- **v0.3**：对应 **P1**（体验与运维），11 项（单点登录 SSO 已提前至 v0.2，RPC 日志采集 2026-11-12 新增）；另含工程规范化三件——规约工具链、存量代码审查、AI 开发规范与流程（元规范）。
- **v0.4+**：对应 **P2**（长期演进）。

### 明确不做（避免反复讨论，详见 PLAN.md）

- 不单独拆 auth / authz 服务（ADR-001、ADR-002）
- 不重做统一响应结构（ADR-005）
- 不引入运行时微前端框架（ADR-006）
- 不用 JWT 承载权限点（走 permCodes 接口 / 缓存）

### 维护约定

- 每个版本开发期间，对应小节的清单持续追加，发布时冻结；
- 版本时间点确定后更新总览表；变更明细仍以 `docs/PLAN.md` 变更记录为准。
