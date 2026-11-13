# 开发计划与进度计划

> 项目：API 网关平台（project_api-gateway）
> 数据来源：`docs/PLAN.md` 变更记录、git 提交历史（50 次提交）、2026-11-06 功能完成度盘点
> 版本节奏：v0.1 为首个可演示版本；后续版本按 P0 → P1 → P2 缺口优先级排期，时间点待定

---

## 一、版本总览

| 版本 | 状态 | 开始时间 | 结束时间 | 参与人员 | 功能概要 | Bug 概要 |
| --- | --- | --- | --- | --- | --- | --- |
| **v0.1** | ✅ 已完成 | 2026-11-04 | 2026-11-06 | zengguiwan | 网关核心、平台微服务、三端 SDK、服务发现、可观测、网关总览、认证安全、配置中心、前端控制台、交付运维——**10 大块 32 项**，21 个菜单上线 13 个 | 修复 **9 项**：离线检测、SDK 接入三连修、采集口径、下拉重复、菜单映射、布局根因、数据归属清理等 |
| **v0.2** | ✅ 已完成（20/20 项全部交付，2026-11-13） | 待定 | 待定 | zengguiwan | P0 缺口 **8 项** + 增值项，共 **20 项**：网关限流 ✅、令牌与会话 ✅、安全策略 ✅、登录方式 ✅、鉴权豁免 ✅、会话接真、安全策略 / 登录方式接真、鉴权精细豁免（新菜单）、开放认证调用方约束 ✅、菜单权限过滤 + 账号平台授权 ✅、变更审计 ✅、列表数据导出 ✅、主机运行指标上报 ✅、部署关联与跨主机调用统计 ✅、拓扑页双视图 ✅、鉴权风控管理 ✅、日志打印管控 ✅、单点登录（SSO，OIDC Provider）✅、前端微前端化（Module Federation，8 模块独立构建独立部署）✅、分布式调度中心（清理 + 分区预建）✅、WebSocket 转发与会话采集 ✅、列表分页条统一（每页条数 + 上下页）✅、平台配置模糊搜索 ✅ | —— |
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
4. **登录方式页接真（多因子登录）**（✅ 已完成 2026-11-13）：配置落库 `platform.login.methods`（factors[].enabled + captcha + qrcode + mail + sms），登录流程按开关实时生效（读库 10 秒缓存）。
    - **密码因子 + 验证码两种形态**：① **随机图片验证码**——后端 `BufferedImage` 自绘（旋转字符 / 干扰线 / 噪点，去 0O1IL 易混字符），base64 PNG 返回、120 秒有效、一次性；② **弹框动态码**——6 位数字内联展示（CSS `user-select:none` 不可复制）、30 秒有效且前端到点自动换新。形态在「登录方式 → 密码登录验证码」切换（image / dynamic），`GET /auth/captcha/image|dynamic` 下发 ticket
    - **短信 / 邮件动态码免密登录**：`POST /auth/otp/send`（channel=SMS|EMAIL）按账号绑定的手机号 / 邮箱生成 6 位码（5 分钟、一次性），目标脱敏返回（138****00 / a***@example.com）。**通道为模拟实现**——验证码写服务日志（`[SMS-MOCK]` / `[MAIL-MOCK]`），不发真实短信、不连 SMTP；接真实通道只需替换 `LoginFactorService#deliver`，校验与界面零改动
    - **扫码登录两种形态**：① **演示确认流**——`POST /auth/qr/create` 生成 ticket 与确认链接（`/login?qr=<ticket>`），登录页二维码（前端 `qrcode` 库生成）+ 2 秒轮询；手机端打开链接在确认页输账号密码 → `POST /auth/qr/confirm` 绑定 userId → 轮询到 CONFIRMED 自动签发令牌；② **扫码取码 + 手动输入流**——二维码内容即 6 位动态码（30 秒自动更换，`GET /auth/qr/code` 刷新），扫码读取后在登录页输入「账号 + 动态码」（`factor=QR`）完成登录
    - **登录接口多因子分派**：`POST /auth/login` 新增 `factor` / `ticket` / `code`，取值 PASSWORD（默认）/ SMS / EMAIL / QR / QR_CONFIRM；密码因子关闭时拒登（原有逻辑），其余因子关闭时对应入口与接口均拒绝；非密码因子通过后统一走 `UserService#loginByFactor`（状态校验 + 会话登记 + 令牌签发 + 登录历史标注「因子登录：SMS/QR…」）
    - **安全细节**：验证码 / OTP / 确认票据全部一次性消费（重放即失效）；扫码 ticket 180 秒有效期、确认后二次登录被拒；验证码错误不销毁 ticket（允许纠错重输），动态码错误按次校验；因子接口在 proxy `skip-paths` 与 user 服务拦截器双处放行；新增匿名公开端点 `GET /auth/login-methods`（只返回因子开关与形态，绝不带 SMTP / 短信凭据）
    - **前端**：登录页按配置动态渲染因子胶囊 Tab（账号密码 / 验证码登录 / 扫码登录），密码表单按需出现图形验证码或动态码面板；短信/邮件 Tab 带 60 秒重发倒计时与「模拟通道」提示；扫码 Tab 双形态（二维码轮询 / 动态码 + 手动输入）；`/login?qr=<ticket>` 展示确认面板；「登录方式」页新增验证码形态、扫码形态配置与通道测试按钮（发一条模拟码并提示去日志查收）
    - **实测**（经网关 28090 与浏览器全链路）：动态码错误 400 / 正确 200 / 同 ticket 重放 400；短信码登录 200、重放 400；邮件码登录 200；扫码确认 → QR_CONFIRM 登录 200、票据重放 400；取码流登录 200、错误码 400；关闭短信因子后 `otp/send` 400「登录方式已关闭」；图形验证码登录页渲染与点击刷新正常；密码直登（验证码关闭）回归正常，控制台 0 error
    - **边界（明确不做）**：短信 / 邮件通道为模拟（无真实网关与 SMTP，接入只换 sender）；TOTP 二次验证与「敏感操作二次验证」仍随 v0.3；无发送频率限流（仅前端 60 秒倒计时）、无图形验证码 OCR 强度评估；扫码确认端是控制台内演示页而非移动 App；配置生效有 10 秒缓存窗口
    - 关联：`LoginFactorService` / `LoginFactorController`（user 服务）、`LoginPolicyService`（因子开关 / 形态）、`WebMvcConfig` 与 proxy `skip-paths`（放行）、登录页与「登录方式」页

5. **鉴权精细豁免管理（新菜单）**（✅ 已完成 2026-11-12）：新增表 `tb_gw_auth_exempt`（名称 / Ant 路径 / 方法 / 类型 LOGIN·RATE·ALL / 状态 / 到期时间 / 备注）；config 服务 `AuthExemptController`（`/auth-exempts` 增删改查 + 启停，路由 `/api/auth-exempts/**` → config），`AuthExemptService` 校验路径前缀、类型枚举、到期时间格式（必须晚于当前）；网关 `AuthExemptRepository`（5 秒缓存）+ `AuthGlobalFilter` / `RateLimitGlobalFilter` 以「**追加放行**」方式应用——yml 内置白名单仍然有效，避免误配置把登录链路放死；控制台新增「认证与安全 → 鉴权豁免」菜单（表格 + 新增/编辑对话框 + 启停/删除 + 搜索 + 分页），启用与新增均弹**高危二次确认**（明确提示将放行的路径与类型），过期规则自动显示「已过期」；实测：无 token 访问 `/api/users` 基线 401 → 新增 LOGIN 豁免 `/api/users/**` 后 6 秒内变为 200 → 停用后 6 秒恢复 401 → 删除成功；页面 1 条种子规则（停用示例）与对话框字段正常；边界：豁免规则暂只覆盖「登录鉴权 / 限流」两类，数据权限豁免随 v0.3 行级权限方案
6. **开放认证调用方约束**（✅ 已完成 2026-11-12）：复用 API Key 作为调用凭证，四个维度落在 Key 上。① **Key 表扩 4 列**：`allowed_ips`（逗号分隔，支持精确 IP、CIDR 网段、`10.20.3.*` 前缀通配、`*` 全放开）、`allowed_services`（`tb_gw_route.service_name` 白名单）、`allowed_platforms`（`tb_gw_open_platform.app_id` 白名单）、`rate_limit_per_minute`（该 Key 单独每分钟上限，0 = 不单独限流）；存量库用 `information_schema` + `PREPARE` 动态 DDL 补列，脚本可反复执行（MySQL 5.7 无 `ADD COLUMN IF NOT EXISTS`）。② **管理接口**：`PUT /keys/{id}/scope` 全量覆盖约束（空串 = 不限），变更写一条审计（action = `SCOPE`，备注列出四项约束），控制台 API Key 页新增「调用方约束」列（IP / 服务 / 平台 / 限流的数量标签）与「约束」对话框。③ **网关校验**：`AuthVerifier` 比对 IP 白名单（不通过 **403**，凭证本身无效仍 **401**）；新增 `ApiKeyScopeFilter`（order `HIGHEST_PRECEDENCE + 15`）按最长前缀匹配路由，校验目标服务的 `service_name` 与所属 `app_id` 是否在 Key 授权范围内，越界 **403** 且不消耗限流令牌、不产生调用日志；路由表 10 秒缓存避免每请求查库。④ **每分钟限流**：`RateLimitGlobalFilter` 在既有规则之后叠加 Key 自带桶（容量 = N、每秒补充 ⌈N/60⌉，id = -1），复用同一 Redis 令牌桶实现，不另造一套；超限 429 + `Retry-After`。⑤ **开放接口强制 Key**：新增开关 `platform.open.auth.require-key`（系统设置 / 开放认证页，10 秒缓存），开启后 `/api/open/**` 不再走白名单，必须携带 `X-Api-Key`（不接受用户令牌），否则 401；平台内部服务用共享密钥头（`X-Gateway-Client-Token`）调用验签接口时按内部调用放行，避免「开约束 → 下游服务全部验签失败」的自伤。⑥ **控制台**：「开放认证」页由静态说明页升级为半接真——新增「调用方约束」区块（开关 + Key 约束清单表：IP / 服务与平台白名单 / 每分钟限流 / 状态），接口文档与 curl 示例保留。**实测**：错误 IP → 403「调用方 IP 127.0.0.1 不在该 API Key 的 IP 白名单内」；改为 127.0.0.1 → 200；服务白名单填 `jv-api-gateway-trace` → `/api/users` 403、`/api/trace/logs` 200；平台白名单填 `zimo-erp` → 403「平台「API网关平台」不在授权范围」，改 `app-api-gateway` → 200；限流 2 次/分 → 前 2 次 200、后续 429；开关开启 → 外部无 Key 401、带 Key 200、内部验签 200；控制台保存约束落库正确、审计出现 `SCOPE` 记录。**边界（明确不做）**：① 约束生效有 **30 秒**延迟（鉴权缓存 TTL），不做主动推送失效；② IP 白名单只比对 `X-Forwarded-For` 首地址 / TCP 远端地址，不防伪造（前置代理需自行清洗该头）；③ 每分钟限流是「容量 N、速率 ⌈N/60⌉/秒」的令牌桶近似，不是严格滑动窗口；④ Key 与账号是两套凭证，Key 不做「最后使用 IP 告警 / 自动熔断」（属第 13 项风控）
7. **菜单按权限动态渲染 + 账号平台授权**（✅ 已完成 2026-11-12）：permCodes 已下发到前端，补菜单过滤逻辑（当前全量可见，纯前端改动）；**① 菜单按权限动态渲染（✅ 已完成 2026-11-12）**——8 个模块的 menu 声明补 `perm` 字段（18 处，与 `tb_perm_item` 的 DISPLAY 权限码一致：`/auth/user→auth.user.view`、`/route-config→onboard.route.view`、`/auth/api-keys→open.key.view` 等）；portal 装配层新增 `MENU_PERM_MAP`（path → 权限码）与 `filterMenusByPerms`（未声明 perm 的菜单默认可见，组内可见子项为空则整组剔除）；user store 新增 `permCodes` 缓存与 `loadPerms()`（登录会话内拉一次 `/auth/my-perms`，失败不置位、下次导航重试，登出清空）；路由守卫每次导航 `await loadPerms`，目标菜单声明 perm 且用户无权限 → 重定向到首个有权限的菜单（无任何权限则回登录页）。实测：admin（超级管理员 builtin 分组，44 个权限码）26 项菜单全量可见；只读观察者分组仅授 overview / stats / trace / discovery 四码，登录后菜单收窄为 10 项（认证与安全、分组与权限整组隐藏，系统管理组仅剩无权限点管理的调度计划），直接访问 `/auth/user`、`/perm/menu` 均重定向 `/overview`；测试账号已清理。
    **② 账号-平台（应用）授权（✅ 已完成 2026-11-12）**——新增 `tb_gw_open_platform`（平台登记，种子 3 个演示平台：齐墨ERP / 数据中台 / 报表达人）与 `tb_gw_account_platform`（账号-平台授权，`uk_user_app` 唯一）两张表，由 PermSchemaInitializer 启动建表；后端 `OpenPlatformController`：`GET /open/platforms`（清单）、`GET /open/accounts/{id}/platforms`（已授权）、`PUT /open/accounts/{id}/platforms`（全量覆盖，不存在的 appId 自动忽略、空数组清空、`@Transactional`）；`OpenVerifyRequest` 增可选 `appId`，`/open/auth/token/verify` 带 appId 时校验「平台存在且启用」+「账号已授权」，未授权返回 `valid=false`（reason「账号未授权访问平台「xxx」」），不传 appId 保持原行为（向后兼容）；前端用户管理页操作列新增「平台授权」按钮与对话框（平台清单多选、停用项置灰、回显已授权）。实测：未授权 zimo-erp → valid=false；PUT 授权 zimo-erp + bi-report（not-exist 被忽略）→ verify 通过并返回用户身份与权限点；data-center 未授权仍拒绝；不存在 appId → 403「目标平台不存在」；关系表按账号正确落库、重开对话框勾选回显正确。边界：**应用维度数据可见性过滤（未授权平台的数据视图不可见）暂未涉及**——当前各页面没有按 appId 切分的数据视图，待第 10/11 项数据链与第 15 项 SSO 落地时统一接入
8. **配置 / 路由变更审计**（✅ 已完成 2026-11-12）：新增 `tb_gw_change_log`（biz_type CONFIG / POLICY / ROUTE、biz_key、action CREATE·UPDATE·DELETE、before_value、after_value、operator、operator_id、request_ip、create_time，索引 biz_type+biz_key+create_time），复用 `tb_gw_api_key_log` 的「操作人 + 动作 + 备注」模式并补上改前改后值。① **写入点**：config 服务 `ChangeLogService` 挂在配置增删改（`ConfigServiceImpl`）、安全策略 / 登录方式 / 开放认证开关（`PolicyController`）、动态日志级别（`LogPolicyController`）；discovery 服务 `RouteAuditWriter` 挂在路由新增 / 编辑 / 启停 / 删除（同一张表，只写影响转发的字段快照）。② **操作人取数**：优先 `UserContext`（有登录拦截器的 config 服务），其次控制台携带的 `X-Username`（discovery / 无拦截器场景），都没有记 `system`；`@zimo/shared` 请求拦截器已全局附带 `X-Username`。③ **脱敏与截断**：敏感配置键（password / secret / token / key）的改前改后值落库前走 `LogMask.configValue` 遮罩，单值超 4000 字符截断。④ **查询**：`GET /change-logs?bizType=&bizKey=&page=&size=`（config 服务，网关路由 `/api/change-logs/**` → 28085），按 id 倒序，bizType / bizKey 均可选。⑤ **控制台**：「系统设置」每行配置与「路由配置」每行路由新增「变更记录」按钮，弹窗展示时间 / 动作 / 操作人 / 来源 IP / 改前 → 改后。⑥ **实测**：改 `platform.ui.refresh.interval` 60 → 30000，审计落库 operator=admin、operatorId=1、before/after 正确；新建路由 `/api/audit-test/**` 记 CREATE（含 after 快照），删除同路由再记 DELETE，按 bizKey 查询返回 2 条；页面弹窗正常渲染。**边界（明确不做）**：① 审计写入失败只告警不阻断业务（可用性优先），无「写失败即回滚」；② 不做变更回滚 / 一键还原，也不做变更审批流；③ 只覆盖 CONFIG / POLICY / ROUTE 三类，用户、API Key、角色权限的变更仍用各自已有日志表（未统一到本表）；④ 无保留期清理任务（长期累积，后续随第 17 项调度中心统一清理）；⑤ 日志级别等策略的「改前值」为 JSON 文本快照，未做逐字段 diff 展示
9. **列表数据导出（服务发现 / 日志追踪）**（✅ 已完成 2026-11-12）：采用**后端流式下载 + 前端 Blob 落盘**方案（不引入 Excel 依赖，统一 CSV，Excel/WPS 可直接打开）。后端 `TraceController` 新增 `GET /trace/logs/export`、`DiscoveryController` 新增 `GET /discovery/services/export`，参数与列表接口完全一致（沿用当前筛选条件），**导出上限 10000 行**（请求列表时固定 `size=10000`，防全量拖库）；CSV 输出带 UTF-8 BOM（Excel 中文不乱码）、`Content-Type: text/csv;charset=UTF-8`、`Content-Disposition: attachment; filename*=UTF-8''...`；字段值统一 `csvCell` 转义（引号、逗号、换行）；**每行追加「操作人 + 导出时间」两列**留痕，操作人取值优先级 `X-Username` 请求头 → `UserContext` → `uid:` → `system`（trace / discovery 模块无登录拦截器，由控制台前端携带当前用户名头）。日志导出列：调用时间 / 应用 / 服务 / 接口 / 状态码 / 耗时 / 协议 / 错误类型 / 操作人 / 导出时间（实体无 method 字段，未输出「方法」列）；服务导出列 15 项：服务名 / 应用 / 实例 IP / 端口 / 语言 / 版本 / 状态 / QPS / 平均耗时 / 调用量 / 接口数 / 实例数 / 注册时间 / 操作人 / 导出时间。前端两页筛选区「下载 CSV」按钮（`exportCsv()`：`fetch` + `Authorization` + `X-Username` 头 → `blob` → `a.download`），文件名带日期。实测：直连导出 10001 行（含表头）、响应头正确、操作人列显示 `admin`；控制台点击按钮弹出「已导出当前筛选结果（最多 10000 行）」。**边界（明确不做）**：① 仅 CSV 一种格式，不做 xlsx / TSV / HTML 多格式（需要复杂样式或公式的导出不在本期）；② 导出为同步全量生成，不做异步任务队列与进度条，10 万级以上导出需后续换流式游标；③ 操作人取自前端携带的头，非网关侧强校验身份（trace/discovery 无鉴权拦截器），可被伪造，仅作运维留痕不作为审计证据——真正不可抵赖的审计依赖第 8 项变更审计；④ 导出行为本身不写审计事件（未记录谁导出了多少行）
10. **接入方主机与运行指标上报**（✅ 已完成 2026-11-12）：SDK 新增独立定时采集线程，每 60 秒上报一次（`zimo.gateway.client.metrics.enabled / interval / gpu-enabled` 可配，默认开启、60 秒、采集 GPU）。① **采集项**：CPU 使用率与核数、1 分钟负载；系统内存总量 / 使用率、进程 RSS；JVM 堆使用率、GC 次数与耗时、线程数、fd 数；磁盘总量 / 使用率；网卡收 / 发速率（两次采样累计差）、TCP 已建立连接数；有 `nvidia-smi` 时附带 GPU 利用率与显存（无 GPU 自动跳过）。② **上报通道**：`POST /api/discovery/metrics/batch`（单批 ≤ 500 点，hostname 必填、缺 reportTime 补当前时间），网关白名单已加入该路径（与 register / heartbeat 同级），SDK 默认排除自身上报链路的 trace 采集。③ **存储**：`tb_gw_host_metrics`（28 字段，主键 id+report_time），按天 `TO_DAYS` RANGE 分区 + `p_max` 兜底（MySQL 5.7 不允许 `UNIX_TIMESTAMP(列)` 作分区键，与 `tb_gw_trace_span` 口径一致）。④ **查询**：`GET /discovery/metrics/hosts`（窗口内每主机 CPU 均值 / 峰值、内存、堆、磁盘、网络、GPU、最近上报）、`GET /discovery/metrics/trend`（单主机按 1 / 5 分钟分桶时间序列）、`GET /discovery/metrics/hostnames`。⑤ **控制台**：「数据统计」页新增「主机资源」卡片 —— 时间窗口（15 分钟 / 1 小时 / 6 小时 / 24 小时）、主机下拉、概览表（CPU 均值·峰值、内存、堆、磁盘、网络入·出、GPU、最近上报）、资源使用率折线（CPU / 内存 / 堆 / 磁盘切换）与网络速率双线图。⑥ **三语言 SDK 齐备**：Java（`jv-api-gateway-client-starter`，JDK MXBean + `/proc`）、Python（`sdk/python/jv_gateway/metrics.py`，psutil 可选、无依赖可退化）、Node（`sdk/nodejs/src/metrics.js`，`os` + `/proc`）。⑦ **实测**：Python / Node 各上报一次成功落库；user 服务重启后 Java SDK 60 秒内自动上报（heap 0.31%、线程 76、GC 12、磁盘 98.51%）；趋势接口与折线图（6 个演示点）渲染正常。**边界（明确不做）**：① 纯 JDK 拿不到磁盘读写吞吐 / IOPS（需 OSHI 等原生库），字段留空展示为「-」；② macOS / Windows 无 `/proc`，内存总量、网络速率、RSS 为空，不插值；③ GPU 只识别 NVIDIA（`nvidia-smi`），AMD / Apple Silicon / K8s GPU 共享计数未做；④ 指标不落告警、不做异常检测（属 v0.3）；⑤ ~~分区预建未接入调度中心~~（✅ 2026-11-13 增补完成：调度中心 partition-daily 已按 TO_DAYS 口径为 `tb_gw_host_metrics` 按天预建分区，并兼容存量 `p_max` 兜底分区，详见下方第 25 项）；⑥ 多副本不去重（同主机多实例各上报一行，概览按 hostname 聚合）；⑦ 磁盘只取根分区，未做多分区明细
11. **部署关联与跨主机调用统计**（✅ 已完成 2026-11-13）：基于第 10 项主机数据建立三方关联——服务实例（`tb_gw_service` 的 ip:port）↔ 主机/pod（hostname，K8s 下 hostname 即 pod name、IP 即 pod IP）↔ 调用记录（`tb_gw_trace_log` 已有 req_ip/res_ip 两端地址）。① **SDK 上报 hostname**：`ServiceInstanceDTO` 增 hostname 字段，`ServiceRegistrar` 注册时自动取本机主机名（`InetAddress.getLocalHost()`，失败回退 `HOSTNAME` 环境变量），注册 / 心跳落库 `tb_gw_service.hostname`（Mapper cols / INSERT / UPDATE 同步扩展）；存量列由 `sql/jv-api-gateway-discovery-deployment-host.sql` 动态补列（`information_schema` 判断存在才执行 `PREPARE` DDL）并回填平台自身。② **三个查询接口**（discovery 服务）：`GET /discovery/deployments`（部署清单：每台主机 → 实例清单 + 资源水位，水位取 `tb_gw_host_metrics` 每主机最新一条指标 INNER JOIN `MAX(report_time)`）、`GET /discovery/cross-host`（跨主机调用：req_ip → res_ip 聚合调用量 / 平均耗时 / 错误率，LIMIT 200，hostname 子查询回填）、`GET /discovery/impact`（单点影响面：指定主机的实例数 / 服务数 / 受影响调用量 / 全站占比）。③ **控制台**：「服务发现」新增 `/deployment` 部署页（三卡片：部署清单 expand 表——主机 / 实例 / 应用 / 端口 / 语言 / 状态 / CPU / 内存 / 磁盘水位；跨主机调用表——源主机·IP → 目标主机·IP / 调用量 / 平均耗时 / 错误率；单点影响面汇总 + 明细表），路由与 portal 菜单映射已接入。④ **实测**：三接口返回真实数据（跨主机 `127.0.0.1 → 127.0.0.1` calls 5851；单点影响 `localhost-28081` 受影响调用量全站占比 21.26%）；页面部署清单 8 台主机、跨主机调用与影响面均正常渲染。
    **边界（明确不做）**：① 不做物理网络拓扑——平台不采集机柜号、交换机 / 路由器等网络设备与连线数据，没有机房 CMDB 数据源之前不做伪造的可视化；② hostname 缺失的实例以 IP 兜底展示（本机测试期间 discovery 未重启前用 `UPDATE tb_gw_service SET hostname=...` 临时对齐，SDK 心跳重启后自动上报真实主机名）；③ 错误率口径为 HTTP 5xx + 网关错误类型（`err_type` 落库时成功调用写「无」而非 NULL，统计必须排除「无」，否则错误率恒为 100%——本次实测发现并已修正）；④ 资源水位为「最近一条上报」，不做窗口聚合（趋势在数据统计页主机资源卡片已有）；⑤ discovery 服务需重启加载新 jar 后 SDK hostname 上报才生效（旧进程运行期间 jar 被覆盖导致进程损坏，本次验证以直连接口 + DB 对齐完成）
12. **服务拓扑页双视图：服务调用拓扑 / 主机 pod 调用拓扑**（✅ 已完成 2026-11-13）：服务拓扑页（/topology）卡片头部新增分段控件（`el-radio-group`），「服务调用拓扑」为原视图不变；「主机 pod 调用拓扑」节点为主机 / pod（hostname，缺失以 IP 兜底）、边为「源主机 → 目标主机」聚合调用（调用量 / 平均耗时 / 错误率），数据取第 11 项 `GET /discovery/deployments`（节点 + 实例清单 + 资源水位）与 `GET /discovery/cross-host`（边）。① **二次聚合**：同一主机对可能有多组 IP 组合（如 IPv4 / IPv6 回环），前端口径合并为一条边——调用量累加、平均耗时按调用量加权、失败数由 `errorRate × calls` 折算；② **时间窗复用**：沿用页面统一时间选择器，按 `timeRangeOf(range).seconds / 60` 换算 discovery 接口的 `minutes` 参数（24 小时 → 1440）；③ **节点详情**：悬浮提示展示主机名 / IP / 实例数 / CPU·内存·磁盘水位 + 前 5 个实例（服务名 + ip:port）；④ **单机过滤**：下拉选主机后只保留该主机的边与直连邻居节点；⑤ **影响面下钻**：点击节点调 `GET /discovery/impact`，弹窗展示实例数 / 涉及服务数 / 受影响调用量 / 全站占比 + 明细表（服务 / 实例 / 应用 / 状态 / 窗口调用量 / 占比）；⑥ **顺带修两处拓扑渲染缺陷**：自环边（同主机内部调用、服务自调用）原先从节点底部出发再回到底部，起终点重合退化成叠线只剩一个大箭头——改为从节点右侧绕行，并在布局宽度上为自环预留 76px 避免被 viewBox 裁切；卡片标题加 `nowrap` 防止分段控件挤压换行。⑦ **实测**：服务视图正常（proxy → 6 个下游服务边与调用量），主机视图渲染真实数据（`wanzengguideMacBook-Pro.local` 节点 入 18436 / 出 18405、自环 18421 次·321 败，`0:0:0:0:0:0:0:1` 等 IP 兜底节点），单主机过滤后只剩 2 节点 1 边，点击节点弹窗显示实例 6 个 / 受影响调用 18530 次 / 全站占比 99.89% 与逐服务明细；干净会话控制台 0 错误。
    **边界（明确不做）**：① 节点仅两级——主机 / pod 与其实例，不画机房、机柜、交换机等物理设备（无数据源）；② 跨主机边来自 `tb_gw_trace_log` 的 req_ip → res_ip，经网关转发的调用才有边，主机内部非网关流量不体现；③ 错误率口径同第 11 项（5xx + err_type ≠ 无）；④ 主机视图不做自动布局算法升级（沿用分层布局，节点多时靠滚动）；⑤ 无 pod 级别 CPU / 内存的 K8s 指标（需 cAdvisor / metrics-server 数据源），水位取部署主机上的 SDK 上报
13. **鉴权风控管理（用户 / API Key）**（✅ 已完成 2026-11-13）：① **数据基础**——新增 `tb_login_log`（成功 / 失败各一条：账号 / IP / UA / 平台 / 失败原因 / 连续失败次数 / 风险类型·等级·说明）与 `tb_gw_risk_event`（USER 与 API_KEY 统一事件表，处置动作 / 处置人 / 备注 / 时间原地更新留痕），user 服务启动幂等建表（`sql/jv-api-gateway-user-risk-tb_gw_risk.sql`）并种入「认证与安全 → 风控中心」菜单、`auth.risk.view / auth.risk.handle` 权限点与网关 `/api/risk/**` 路由。② **用户维度**——登录链路接入 `RiskService.recordLogin`：连续失败达锁定阈值（实时读登录策略，默认 5 次）→ `LOGIN_BRUTE_FORCE` HIGH；该账号历史成功登录过、本次 IP 未出现 → `LOGIN_NEW_IP` MEDIUM；22:00–07:00 登录 → `LOGIN_OFF_HOURS` LOW；登录记录与事件写入异常一律不影响登录主流程。③ **Key 维度**——网关新增 `KeyCallStatsFilter`（order = HIGHEST+5，排在鉴权 / 限流**之前**，否则 401 / 429 短路后收不到状态码），按分钟把 calls / errors 累加进 Redis 哈希 `gw:risk:key:{keyId}:{yyyyMMddHHmm}`（2 小时过期，仅统计 API Key 通道）；user 服务新增每分钟巡检（`@Scheduled` 60 秒，以前 30 分钟均值为基线）：单分钟调用 ≥ 20 且超基线 3 倍 → `KEY_SPIKE`，单分钟调用 ≥ 10 且错误率 ≥ 30% → `KEY_ERROR_RATE`，命中即**自动停用 Key（熔断式）** + 写 `tb_gw_api_key_log`（action `AUTO_DISABLE`，操作人 system），不自动恢复。④ **预警清单**——`GET /risk/keys/warnings` 输出临期（≤7 天，含已过期）与长期未用（>30 天未调用且创建超 7 天）；网关 `AuthVerifier` 的最近使用回写补上 `last_used_ip`（原来只写时间），预警消费该字段。⑤ **处置**——`POST /risk/events/{id}/handle` 支持 `ACK / IGNORE / UNLOCK_USER / KICK_USER / DISABLE_USER / ENABLE_USER / DISABLE_KEY / ENABLE_KEY`：用户动作复用 token_version 与 `SessionService.kickAll` 会话体系（禁用同时清会话），Key 动作用状态位并写审计。⑥ **控制台**——「认证与安全 → 风控中心」（`/auth/risk`）三 Tab：风险事件（主体 / 状态 / 等级 / 关键字筛选 + 分页 + 处置弹窗）、登录历史（账号 / 成败 / 仅看风险 + 分页 + 风险标记）、Key 预警（类型 / 有效期 / 最近使用 / 最近来源 IP / 说明），顶部 5 张概览卡（今日事件 / 待处置 / 高风险 / 今日失败登录 / Key 预警）。⑦ **实测**：登录成功落历史；dev 连错 5 次 → `LOGIN_BRUTE_FORCE` HIGH，处置「解锁」后 dev 恢复正常登录且事件留痕（admin / 实测：人工解锁）；伪造 `X-Forwarded-For: 10.99.99.99` 登录 → `LOGIN_NEW_IP`；测试 Key 一分钟 25 次 → `KEY_SPIKE` + 自动停用 + 该 Key 调用 401 + `AUTO_DISABLE` 审计，人工 `ENABLE_KEY` 恢复；8 次/分限流 Key 打 15 次（9 成功 / 6 个 429 = 40% 错误率）→ `KEY_ERROR_RATE` + 自动停用；预警清单返回 3 个从未使用的 Key；页面三 Tab、处置弹窗实测渲染正常、控制台 0 错误。
    **边界（明确不做）**：① 不接 IP 归属地 / 设备指纹库，「陌生 IP」口径为该账号历史成功登录 IP 比对，「非工作时段」固定 22:00–07:00（未做成可配置）；② 失败后「验证码 / 延时加码」未实现——沿用既有连续失败锁定 + 剩余时间提示，验证码校验依赖登录页通道（第 4 项已配置、校验随 v0.3）；③ Key 熔断只停用不自动恢复，基线为前 30 分钟简单均值（不做同环比 / 季节性模型），Redis 不可用时无计数（宁可漏报不误判）；④ 处置留痕分两处：Key 动作写 `tb_gw_api_key_log`，用户动作写在事件行本身（user 服务未复用 config 的 `tb_gw_change_log`，避免跨服务写同一审计表口径混乱）；⑤ 不做账号「注销」删除，仍走用户管理页软删；⑥ 登录历史未纳入列表导出（导出仅覆盖服务发现 / 日志追踪，见第 9 项）；⑦ 非工作时段规则本次未实测（当前为工作时段），逻辑按代码实现
14. **日志打印管控与处理**（✅ 已完成 2026-11-12）：① **traceId 贯穿** —— Servlet 服务新增 `TraceIdFilter`（`HIGHEST_PRECEDENCE`，透传 / 生成 32 位十六进制写入 MDC 并回写响应头），网关新增 WebFlux 版 `TraceIdWebFilter`（响应式线程切换后 MDC 会丢，拦截日志显式取 exchange 属性），6 个模块 `logback-spring.xml` 的 PAT 统一加 `[%X{traceId:-}]`；实测带 `X-Trace-Id: servlet-trace-0001` 的请求在 user 服务日志中完整出现。② **动态日志级别** —— 平台配置 `platform.log.levels`（JSON：logger → 级别，控制台「系统管理 → 日志管控」维护），`LogLevelSync`（common，Servlet 服务）与 `ProxyLogLevelSync`（proxy，响应式栈，不依赖 common）各自 10 秒轮询，经 Spring Boot `LoggingSystem` 在线调级免重启；配置中删除的 logger 自动回落 INFO；非法级别整单拒绝（TRACE / DEBUG / INFO / WARN / ERROR / OFF）。实测 PUT 后 10 秒内 user 日志出现「动态级别已应用：com.zimo.gateway.user → DEBUG」，清空配置后出现「动态级别已回落 → INFO」。③ **敏感信息脱敏** —— 新增 `LogMask`（token / 密钥保留前 6 后 4、手机号前 3 后 4、配置 key 命中 password / secret / token / key 时值整体遮罩），已用于管理员初始密码打印与配置变更日志；实测 `platform.test.password` 更新日志输出 `Anothe...8765`。④ **高频日志限速** —— `LogThrottle` 按 key 每秒窗口采样（common 与 proxy 各一份，避免 proxy 引入 Servlet 栈）：鉴权拦截 5 条/秒、限流拒绝 3 条/秒、IP 白名单拒绝 3 条/秒；实测连续 20 次 401 只落 5 条拦截日志。⑤ **日志量监控** —— `GET /policies/log-files` 扫描 `logs/app`（`gateway.log.home` 可配），按模块聚合文件数 / 占用 / ERROR 占用 / 最近批次；控制台页展示并附滚动策略（运行与采集 INFO 保留 168 小时、ERROR 336 小时、单类上限 2GB / 1GB，logback 原配置即已生效）；实测返回 6 个模块真实大小（user 148 文件 / 38.42MB 等）。**边界（明确不做）**：① 网关是响应式栈，MDC 仅在当前事件线程有效，跨线程串联以 `tb_gw_trace_log` 为准；② 日志量只做「读目录聚合」，不做实时采集与阈值告警（ERROR 告警属 v0.3）；③ 滚动清理沿用 logback 既有策略，平台不提供删除 / 清理按钮（避免远程误删生产日志）；④ 「SDK 侧打印开关」未单独实现：SDK 与平台共用 logback 级别体系，由动态级别统一控制；⑤ 脱敏是「调用点主动使用工具类」，不是全局 Appender 自动过滤，新日志不调用 `LogMask` 仍可能明文
15. **单点登录（SSO，OIDC Provider）**（自 v0.3 提前，✅ 已完成 2026-11-13）：网关平台作为认证中心（IdP），业务平台作为接入方（SP）——① **协议端点**（user 服务，context-path `/api`，经网关 `/api/oauth/**` 与 `/api/.well-known/**` 路由，两处 skip-paths 已放行）：`GET /oauth/authorize`（授权码发放，302 回 SP 回调；未登录 302 回控制台登录页并带 `?sso=<authorize 地址>`）、`POST /oauth/token`（code 换 id_token / access_token，兼容 query / form / JSON 三种编码）、`GET /oauth/userinfo`、`GET /.well-known/openid-configuration`、`GET /oauth/jwks`（HS256 对称密钥不发布，返回空键集）。② **接入方登记**（「单点登录」页接真）：新增 `tb_gw_oidc_client`（client_id / client_secret 的 SHA-256 摘要 / 回调地址（多值精确匹配）/ 关联 appId / scopes / 状态），签发与轮换时 secret 原文只返回一次，支持编辑 / 启停 / 删除 / 轮换。③ **授权校验**：authorize 与 token 两处都查第 7 项账号-平台授权关系（认证了 ≠ 能用），未授权平台 authorize 直接 `302 redirect_uri?error=access_denied`。④ **登录页复用**：授权端点以 `gw_token` Cookie 作为 IdP 会话（登录 / 注册成功后前端写入，Cookie 不分端口故 28080 与 28090 共享），未登录跳控制台登录页，登录完成自动回跳授权端点；复用第 4 项已接真的账号密码通道（验证码 / 扫码 / 短信通道本身仍待 v0.3）。⑤ **单点登出**：IdP 侧登出 / 被踢自增 token_version，`/oauth/userinfo` 实时比对 `tv`，失配立即 401——接入方每次取身份即感知，本地会话随之失效。⑥ **审计**：新增 `tb_gw_sso_log`，AUTHORIZE / TOKEN / USERINFO 全链路记录（账号 / 接入方 / IP / 结果 / 说明），页面「登录审计」Tab 可查。⑦ **实测**：元数据发现返回完整端点与 HS256 声明；签发接入方 → 匿名 authorize 302 到登录页（审计记 DENIED）→ 登录后自动回跳 → 302 回 SP 携 code+state（本地 9999 起的模拟 SP 收到回调）→ form POST token 换到 id_token / access_token → userinfo 返回 sub/username/userId/apps；错误 secret 401、未授权平台 `error=access_denied`；IdP 登出后同一 access_token 调 userinfo 立即 401；页面两 Tab 渲染真实数据。
    **边界（明确不做）**：① 签名沿用平台 HS256 对称密钥，不引入 RS256 密钥对与真实 JWKS（对称密钥发布即等于泄露，故 jwks 返回空集；SP 用共享 secret 验签或走 userinfo）；② 仅实现授权码模式（`response_type=code`、`grant_type=authorization_code`），不做 implicit / PKCE / refresh_token / device code；③ 授权页不做独立的「同意授权」页面，登录态 + 账号-平台授权关系即视为同意；④ IdP 会话只认 `gw_token` Cookie（同一控制台账号体系），不做多 IdP 联邦（企业微信 / 钉钉等外部 IdP 接入仍属设计稿范围，未实现）；⑤ 授权码存 Redis 5 分钟一次性，Redis 不可用时 authorize 直接报错（不降级到内存，避免多实例不共享）；⑥ 本环境实测 Tomcat 对 POST 表单体解析结果为空，token 端点已做 body 兜底解析（query / form / JSON 三通道可用），根因未深究；⑦ 登出目前由平台 `/auth/logout` 触发，未提供 RP-Initiated Logout 端点（`end_session_endpoint`）
16. **前端微前端化：8 模块独立构建独立部署（Module Federation，方案 A）**（✅ 已完成 2026-11-13）：源码本就是 monorepo（modules/* 各自独立包 + 声明式 menu/routes），本次改造为运行时组装——① **模块侧**：`frontend/tools/federation.js` 统一生成各模块 vite 配置（federation name 如 `mf_stats`，`exposes: { './module': './index.js' }` 即现有 `{ menu, routes }` 导出，模块源码零改动）；② **地址表** `frontend/mf-manifest.json` 为唯一事实来源（模块名 / 标题 / dev 端口 28091–28098 / remotePath），portal 与模块配置都从它派生；③ **宿主双模式**：默认单体模式（源码别名直连，行为与 v0.1/v0.2 完全一致，一次构建出全量产物），`VITE_MF=true` 切联邦模式（`mfRemotes()` 生成 remote 地址：dev 用 `http://localhost:<devPort>/remoteEntry.js`，构建用 `VITE_MF_BASE`||`/mf` 前缀），remote 加载器走别名 `mf-remote-loaders`（联邦模式指向真实实现、单体模式指向空存根，保证 `router/index.js` 一份代码两种模式都能构建）；④ **故障兜底**：remote 逐个 `try/catch`，失败的模块生成占位模块（菜单显示「XX（加载失败）」、路由渲染 `ModuleError.vue`，展示 remote 地址并支持重新加载 / 回总览），单模块故障不白屏、不拖垮其他模块；⑤ **shared 单例**：vue / vue-router / pinia / element-plus / @element-plus/icons-vue / @zimo/shared，登录态与路由实例全局唯一；⑥ **独立预览**：每模块自带 `index.html` + `dev-main.js`，build 后可单独打开预览挂载其首个页面；⑦ **脚本**：`npm run dev:mf`（模块 build + preview 到地址表端口 + 宿主 `VITE_MF=true` dev）、`npm run build:mf`（先构建宿主再汇总 8 模块产物到 `apps/portal/dist/mf/<模块>/`，产物可整体静态托管）；⑧ **实测**：单体模式 dev(28080) / 生产构建均 0 console error；联邦模式（宿主 dev 28101 + 7 个模块 preview）登录后总览、统计、部署关联、系统设置均渲染真实数据；故意不启动 system 模块时菜单出现「系统管理（加载失败）」+ 占位页（地址 `/mf/system/remoteEntry.js`），其余模块不受影响；联邦生产产物静态托管（28200）8 模块全部加载、样式正常、0 error。
    **边界与踩坑（均为实测发现，已在脚本/配置中固化）**：① `@originjs/vite-plugin-federation@1.3.7` 在 Vite 5 的 **dev server 不产出 remoteEntry**（只有 build 期生成），因此模块侧改为「build + vite preview」按地址表端口托管，改模块代码需重跑 `dev:mf`（脚本内已串好，非 dev server 直连）；② remoteEntry 内部对 chunk 用 `./assets/...`、对样式用裸 `style-*.css`，故 **remoteEntry.js 与 style-*.css 必须落在模块站点根**（放 assets/ 下会解析成 `assets/assets/` 双重路径 404），preview 还须开 `cors`（跨端口 import 否则被浏览器拦截）；③ `@zimo/ui` **不做 shared**：插件会把 Shell 的样式抽成独立 css 且宿主 `index.html` 不引用，导致侧边栏/布局丢样式；改为随宿主打包，代价是各 remote 内若用到 ui 组件会各打一份（纯展示组件，无共享状态，体积可接受）；④ 联邦模式产物要求 `target: esnext`，单体模式因装配用了顶层 await 需 `es2022`；⑤ vite build 默认 `emptyOutDir` 会清空 `portal/dist`，构建脚本必须「先宿主、后汇总模块」；⑥ 未做按模块灰度 / 版本协商（地址表就是一版映射），不做 remote 内部路由前缀改写（模块 path 与单体模式一致）；⑦ 与第 7 项菜单权限过滤兼容：模块仍本地声明 menu，权限过滤在壳层按 `MENU_PERM_MAP` 做
17. **分布式调度中心（新子模块 jv-api-gateway-scheduler，端口 28087）**（✅ 已完成 2026-11-07）：① **分布式锁与配置双存储**——Redis 为第一优先级（SETNX 分布式锁 + 配置缓存），MySQL 兜底（锁表 tb_scheduler_lock 过期重抢 + tb_sys_config 持久双写），Redis 不可用自动降级仍可单点调度；② **历史数据清理任务**——每天定时跑一次（执行时间=系统设置页新增「调度时间」配置项，tb_sys_config `scheduler.clean.run-time`，默认 02:00），**物理删除**保留期（默认 7 天）之外的采集日志与记录表数据：tb_gw_trace_log、tb_gw_trace_span、tb_gw_api_key_log、tb_gw_service_edge（清理清单配置化 tb_scheduler_clean_table，后续新增采集表只需加一行配置）；③ **分区预建任务**——每天为分区表（tb_gw_trace_log、tb_gw_trace_span）提前 2 天生成每日分区执行计划（落 tb_scheduler_plan）并执行 ADD PARTITION，分区配置（分区粒度/提前天数/保留策略）入 tb_scheduler_partition_config，与第 10 项主机指标新表自然衔接（入配置清单即纳入管理）；④ **新菜单「系统管理 → 调度计划」**——调度任务列表展示，点击记录弹框展示任务状态详情（上次执行时间/状态/行数）、执行计划列表、执行记录列表，支持手动触发；网关注册路由 /api/scheduler/** → 28087

18. **WebSocket 转发与会话采集（proxy 增强）**（✅ 已完成 2026-11-09）：① 路由表 upstream_url 填 http:// 即可代理 WebSocket——客户端携带 `Upgrade: websocket` 握手时 Spring Cloud Gateway 自动 http→ws 协议转换转发（实测文本/二进制帧双向收发正常）；② **会话级旁路采集**——自定义 @Primary WebSocketService（装饰 HandshakeWebSocketService）+ CapturingSession 包装入站会话：receive()（客户端→下游）/send()（下游→客户端）双向旁路统计消息数与字节数、保存双向首帧样本（文本帧取前 N 字节 UTF-8，二进制帧标记 [binary n bytes]，`gateway.trace.ws-sample-bytes` 可配，默认 2048），纯观察不重写不影响转发；③ **落库 tb_gw_ws_session**——连接起止时间/时长、双向计数与字节数、首帧样本、关闭状态（NORMAL/ABNORMAL），trace_id 关联握手请求的日志追踪记录（X-Gateway-Trace-Id），采集队列+每秒批量落库与 TraceSink 同模式（有界队列满即丢，不阻塞转发）；④ **帧级明细落库**——每帧一行（方向 IN/OUT、类型 TEXT/BINARY、payload 样本：文本 UTF-8 / 二进制 hex 前 N 字节、原始字节数、到达时间）写 tb_gw_ws_frame，按 session_key 关联会话；开关与上限可配（gateway.trace.ws-frame-log / ws-frame-sample-bytes / ws-frame-max-per-session，默认 true / 1024 / 200）；⑤ **日志追踪页新增「WebSocket 会话」视图**——会话列表（时间/路径/上游/双向帧数与字节/关闭状态）+ 帧明细抽屉（方向徽标、payload），后端查询接口 GET /trace/ws/sessions、/trace/ws/frames；⑥ 顺带修复 SSE 响应体不入追踪日志问题（BodyCaptureResponse 补 writeAndFlushWith 旁路捕获）；⑦ **采集表纳入每日优化（调度中心，衔接第 17 项）**——tb_gw_ws_session / tb_gw_ws_frame 加入清理清单 tb_scheduler_clean_table（保留 7 天，time_column：conn_start / recv_time），tb_gw_ws_frame 重建为按天分区表（PK 含分区列 recv_time，与 tb_gw_trace_span 同模式）并加入分区预建配置 tb_scheduler_partition_config（提前 2 天）；SSE 数据存于 tb_gw_trace_log.res_body，天然在既有清理/分区内。实测：手动触发 partition-daily 自动为帧表生成 p20261111 分区、data-clean 含 6 张采集表执行 SUCCESS。新建表见 backend/sql/jv-api-gateway-ws-session-tables.sql、jv-api-gateway-ws-frame-tables.sql（含调度配置 INSERT）

19. **列表分页条统一增强（每页条数切换 + 上/下一页）**（✅ 已完成 2026-11-11）：新增 `@zimo/ui/GwPager` 共享分页组件——① **每页条数下拉**（10 / 20 / 50 / 100 个/页），切换后自动回到第 1 页并重新请求；② **上一页 / 下一页按钮**，首页时「上一页」禁用、末页时「下一页」禁用，中间页可点；③ 页码「第 x / y 页」按 `ceil(total / size)` 实时计算，总数以「共 N 条」展示；④ 样式沿用平台 `--gw-*` 设计变量，作为共享组件由 `@zimo/ui` 统一出口导出。**第一批（后端分页，4 个页面）**：日志追踪-调用日志（trace/Index.vue）、日志追踪-WebSocket 会话（trace/WsSessions.vue）、用户管理（auth/user/Index.vue）、服务发现（discovery/Index.vue）——替换原 `el-pagination`（仅有页码、无条数选择，旧文案「每页 N 条」一并清除），各页新增 `onSizeChange`（改条数 → `current=1` → 重新请求）；**第二批（前端切片分页，7 个列表）**：令牌与会话-在线会话、令牌与会话-令牌吊销记录（两个 Tab 各自独立分页）、API Key 列表、系统接入-配置项、路由配置、系统设置-配置项、调度计划-调度任务——接口保持全量拉取不改后端分页参数，页面维护 `page / size` 并按页 `slice` 展示，总计以「共 N 条」呈现；关键词 / 来源筛选变化自动回到第 1 页；系统接入与路由配置的分页条位于 `gw-card-bd` 内，用负 margin 抵消 16px 内边距并补 `border-top`，与卡片左右边缘对齐；调度计划分页条右对齐于表格下方并显示「共 N 个调度任务」。实测：调用日志 147984 条（14799 页）翻页 / 切 20・50 条页码同步刷新；系统接入 13 个配置项 2 页（第 2 页 3 行、切 20 条 → 1 页 13 行）；路由配置 40 条 4 页翻页正常；用户管理 / 系统设置 / 调度计划 / API Key / 吊销记录仅 1 页时上下页正确置灰；全项目已无 `el-pagination` 与旧分页文案残留

20. **平台配置模糊搜索（系统设置）**（✅ 已完成 2026-11-11）：系统设置「平台配置」卡片头部新增搜索框（放大镜前缀图标 + 可清空），输入即实时模糊过滤、不区分大小写；搜索覆盖 8 个字段——**配置名（key）/ 配置参数值（value）/ 配置描述（remark）**，以及平台名、应用名、服务名、值类型、来源标签；与来源筛选（全部 / MySQL / Redis / Nacos）叠加生效（先按来源过滤，再按关键词匹配）；搜索词变化自动回到第 1 页，分页总数与「共 N 项（已筛选）」提示同步刷新；无结果时展示「没有匹配的配置项」空态（与「暂无该来源的配置」区分）。实测：输入 schedule → 仅剩 scheduler.clean.run-time（1 项）；输入 open → 仅剩 platform.open.auth.enabled；输入 网关 → 6 项全中（平台名命中）；输入 zzzz → 0 项 + 空态。实现为纯前端过滤——`/configs` 仍全量返回（配置值托管 DB / Redis / Nacos 均随列表返回，搜索无遗漏），后端接口与表结构无需变更

### v0.2 增补批次（2026-11-13，平台巡检与增强）

21. **多端会话管理与设备级踢下线**（✅ 已完成 2026-11-13）：会话登记与列表增加设备类型识别（由 User-Agent 判定 PHONE / TABLET / PC / SERVER，随会话明细落 Redis 与 tb_login_log，SessionVO.deviceType 下发），「令牌与会话」页在线会话显示设备标签（手机 / 平板 / 电脑 / 服务端）；DEVICE 粒度踢下线升级为**按设备类型**——下拉可仅踢某一端全部会话（如只踢手机端，电脑与服务端不受影响），单会话 / 按平台 / 全端三种粒度保持不变；实测：多端登录后按端踢下线，目标端会话立即 401、其余端不受影响
22. **上报黑白名单 ACL（新菜单「上报通道管控」）**（✅ 已完成 2026-11-13）：采集上报通道增加 IP + 服务名（X-Gateway-App-Id）双层黑白名单——网关 `ReportAclService`（5 秒缓存，配置存 tb_sys_config `platform.report.acl`，fail-open：配置缺失或存储不可用时放行，避免误伤采集链路）对 clientTokenPaths 范围内的上报请求校验：IP 黑名单直接 403；服务名白名单模式下未携带 appId 或不在白名单均 403，黑名单模式仅拒名单内；控制台「接入中心 → 上报通道管控」页（ReportAcl.vue，Filter 图标）：IP 名单增删、服务名模式切换（关闭 / 黑名单 / 白名单）与白名单编辑；实测 5 场景闭环：IP 黑名单 403 → 白名单拒未知 appId 403 → 拒空 appId 403 → 白名单内 appId 200 → 关闭 ACL 后 200
23. **风控增强：IP 封禁手动封禁 / 解封**（✅ 已完成 2026-11-13）：新增 tb_gw_ip_ban + user 服务 IpBanService（支持前缀通配 10.0.*）+ RiskController `/risk/ip-bans` 接口（清单 / 手动封禁 / 解封，写 tb_gw_risk_event 留痕）；网关 IpBanStore（Redis set `gw:ipban`，fail-open）在鉴权前拦截，登录层同步校验；风控中心新增「IP 封禁」Tab（封禁清单 + 历史 + 手动封禁按钮）；实测闭环：手动封禁 → 网关请求 403 → 解封 → 网关恢复 200。账号 / API Key / 会话（token）侧的风控沿用第 13 项既有能力：连续失败达阈值锁账号（阈值/时长由安全策略动态下发）、异常熔断停用 Key、强制下线踢会话，处置动作均可人工反向操作（启用 / 恢复 / 解锁）
24. **平台本地化配置集中化（infra）**（✅ 已完成 2026-11-13）：新增 config 服务 `InfraConfigController`（GET / PUT `/infra/config`）——平台本地化配置（DB、Redis、Nacos、各模块服务 URL、外部组件地址等）统一存 tb_sys_config `platform.infra.properties`，PUT 发布生成 `gw-platform-infra.properties`，7 个服务经 `spring.config.import`（optional:classpath:）加载，本地 yml 降级为兜底默认值；控制台「系统管理 → 基础设施配置」页（InfraConfig.vue，Coin 图标）：18 项配置展示与编辑、5 项敏感项（密码 / 密钥）掩码显示；**边界（明确不做）**：client 采集模块（client-starter）不纳入集中化——其他业务平台直接引用该模块，不依赖平台 config 服务
25. **主机指标日分区纳入调度中心**（✅ 已完成 2026-11-13）：调度中心 partition-daily 任务补 `tb_gw_host_metrics` 按天 TO_DAYS 分区预建（提前 2 天）；修复存量 `p_max` 兜底分区导致 ADD PARTITION 非法的问题——先查 information_schema 是否存在 p_max，存在则 `REORGANIZE PARTITION p_max INTO`（新日分区 + 保留 p_max），不存在则直接 ADD；data-clean 清理任务覆盖该表；实测手动触发：p20261115 创建成功、p_max 保留，data-clean SUCCESS（39814 行）
26. **菜单信息条全量覆盖 + 图标审计**（✅ 已完成 2026-11-13）：侧边栏菜单悬浮信息条（功能上线标记｜后端服务｜运行状态含延迟）补齐调度计划等缺失项——29 个菜单全部映射 MENU_SERVICES（脚本核对 0 缺失）；33 个菜单图标逐一查重 0 重复（新增菜单 上报通道管控=Filter、基础设施配置=Coin）

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
- **v0.2**：对应 PLAN.md 的 **P0 缺口**（影响「能交付」的项），主线是两件事——网关生产底线能力（限流、会话管理）和把 4 个「假页面」接真；另含用户点名的增值项（列表导出、主机运行指标上报、部署关联与跨主机调用统计、拓扑页双视图、鉴权风控管理、日志打印管控、单点登录 SSO、前端微前端化（Module Federation，8 模块独立构建独立部署）✅、分布式调度中心）。
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
