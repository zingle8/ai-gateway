# 开发计划与进度计划

> 项目：AI 网关平台（project_api-gateway）
> 数据来源：`docs/PLAN.md` 变更记录、git 提交历史（50 次提交）、2026-11-06 功能完成度盘点
> 版本节奏：v0.1 为首个可演示版本；后续版本按 P0 → P1 → P2 缺口优先级排期，时间点待定

---

## 版本总览

| 版本 | 状态 | 开始时间 | 结束时间 | 参与人员 | 已完成 / 计划功能 | 修复 Bug |
| --- | --- | --- | --- | --- | --- | --- |
| **v0.1** | ✅ 已完成 | 2026-11-04 | 2026-11-06 | zengguiwan | **一、网关核心（jv-api-gateway-proxy）**<br>1. Spring Cloud Gateway 代理转发，统一鉴权 `AuthGlobalFilter`（token 验签、豁免路径、Redis/MySQL 双层缓存）<br>2. 调用链采集 `TraceGlobalFilter`：req/res 报文、耗时、入站/出站字节数双端采集<br>3. DB 动态路由：`tb_gw_route` CRUD + `DbRouteDefinitionLocator` 每 30 秒热刷新（改规则免重启）<br>4. 鉴权豁免全局控制位 `platform.auth.exemption.control`（CLIENT / PLATFORM）<br><br>**二、平台微服务（28081~28085）**<br>5. 五个业务模块：user / overview / config / discovery / trace，模块统一命名 `jv-api-gateway-*`<br>6. 系统配置服务：DB / Redis / Nacos 多来源托管，展示来源与平台/应用/服务归属，公开配置接口<br>7. 服务标识体系：服务ID（英文唯一）+ 服务名（中文显示），对齐应用ID/应用名模式，SDK 三端同步<br><br>**三、接入 SDK 三端**<br>8. Java client-starter：注册 / 心跳 / trace 采集，注册失败心跳周期自愈<br>9. Python SDK 补齐（协议与 Java 一致）<br>10. Node.js SDK（jv-gateway，零第三方依赖）：GatewayClient / TraceReporter / traceExpress + Express 示例<br>11. SDK 安全双门：上报共享密钥 `X-Gateway-Client-Token`（伪造注册/上报 401）+ SDK 入口鉴权验签（回调 verify + 缓存、豁免 / fail-open 可配）<br><br>**四、服务发现**<br>12. 注册表：register / heartbeat / deregister，30s 心跳、90s 判离线、HeartbeatMonitor 每 60s 巡检<br>13. 服务列表：全部应用 / 全部服务双下拉过滤、接口数与调用量列<br>14. 健康状态三态（健康 / 离线 / 异常）与颜色区分<br><br>**五、可观测三件**<br>15. 日志追踪（阶段一）：跨服务调用链采集与存储（trace_log / trace_span 按天分区 + 索引优化）、报文详情、应用/服务级联过滤<br>16. 服务拓扑（阶段二）：调用边预聚合（tb_gw_service_edge）+ 慢链路 TopN、全局拓扑 + 应用/服务过滤<br>17. 数据统计：请求量 / QPS / 入站出站流量三趋势、方法分布、状态码分布、Top 服务 / Top 接口（含服务名、所属应用归属维度）<br><br>**六、网关总览**<br>18. 核心指标卡、请求量 / QPS / 流量三张趋势折线图（各自整行）、运行时指标<br>19. 已接入服务实例按近 5 分钟调用量排名<br><br>**七、认证与安全**<br>20. 分组与权限：分组管理 / 部门组织 / 平台功能与菜单，permCodes 权限点下发<br>21. API Key 全生命周期：签发 / 启停 / 轮换 / 续期 / 吊销 + 操作审计（tb_gw_api_key_log）+ 统计看板（今日签发/续期等）<br>22. 开放认证：register / login / logout / token verify、双类型校验<br><br>**八、配置中心**<br>23. tb_sys_config 配置项管理 + 公开配置接口（无需登录读取开关）<br>24. 控制台自动刷新频率配置化 `platform.ui.refresh.interval`（总览/统计/发现接入，改值 30 秒生效，默认 10 秒）<br><br>**九、前端控制台**<br>25. 8 大模块 21 个菜单；认证与安全五个子菜单收敛为页内 Tab、接入中心菜单排序调整<br>26. TrendChart 折线图组件：Y 轴刻度、悬浮 tooltip、双序列（双折线 + 图例 + 双 Y 轴）<br>27. 四页统一时间统计维度（1 分钟 ~ 30 天共 10 档）、PaneTabs 分段控件、全局响应式适配<br>28. 侧边栏菜单健康标识：状态点 + 悬浮提示（后端服务 / 运行状态含延迟 / 功能上线标记绿灰着色）<br><br>**十、交付运维与文档**<br>29. 日志统一切分、一键构建启动脚本（scripts/start.sh / stop.sh / restart.sh）<br>30. docs/modules-deployment.md：服务拆解与按需启动、按模块售卖组装方案<br>31. Maven 私服一键部署（Nexus 3，init-nexus.sh）+ file:// 仓库 / 云制品库分发替代方案<br>32. 接入文档（client-integration.md）、配置信息一览（数据库/Redis/Nacos/JWT/环境变量） | 1. **服务离线状态检测不到**：离线无颜色标识（dotClass 补「离线」映射）；主动下线 deregister 由「异常」改为「离线」；心跳巡检漏处理异常/亚健康状态的服务（kill 进程后 10 秒内页面显红）<br>2. **SDK 接入三连修**：注册失败后心跳周期自动补注册（启动顺序敏感场景自愈）；网关白名单放行 SDK 上报通道（此前 401 导致实例永久离线）；默认 server-addr 由前端 28080 改指网关 28090<br>3. **采集归属口径错位**：SDK 上报 serviceName 混入中文名（trace 表出现「配置中心」等脏值）、服务发现 QPS/调用次数聚合落空——统一走 `effectiveServiceId()`，TraceLogDTO/SpanRecordDTO 增 serviceId<br>4. **应用下拉重复**：同一 appId 历史改名产生多行字典（app-gateway 网关平台→API网关平台等），trace/discovery/overview 三处字典按 app_id 取最新一条<br>5. **proxy 中文名被心跳刷回服务 ID**：自注册只发 serviceName=ID，补 serviceId + 中文名「网关代理」<br>6. **统计页分布卡片堆叠**：`.grid-2` 缺基础样式（仅有 1400px 单列覆盖），补 `display:grid` 两列 + 抵消 `.gw-card + .gw-card` 纵向间距副作用<br>7. **「服务拓扑」菜单无状态点与悬浮**：MENU_SERVICES 漏登记 `/topology`（不以 /trace 开头），最长前缀匹配落空 → 补映射<br>8. **数据归属清理**：冒烟测试数据归并到 regtech 应用（node/py 冒烟服务 ID 对齐）、应用编码两次统一（app-gateway → app-api-gateway，5 张表数据迁移 + 窗口期残留清理）<br>9. **文档与代码一致性**：接入文档与实际行为修正、Web 页面与文件文档全量对齐服务ID/服务名体系 |
| **v0.2** | 📋 计划中 | 待定 | 待定 | zengguiwan | 1. **网关限流**（P0 首位，网关生产底线）：Spring Cloud Gateway `RequestRateLimiter` + Redis，按调用方 Key / IP / 路由三维度配置，控制台可查看与调整<br>2. **令牌与会话页接真**：JWT 无状态 → 登录时 Redis 会话登记（token → 用户 / IP / UA / 登录时间）；页面读真实在线会话（含设备/IP/登录方式）；强制下线复用 token_version 自增<br>3. **安全策略页接真**（假页面优先 1）：MFA 适用范围 / TOTP 偏移 / 敏感操作 step-up 开关落库（tb_sys_config 或独立策略表），登录流程真正拦截生效<br>4. **登录方式页接真**（假页面优先 2）：密码 / 验证码 / 扫码 / SSO 因子开关落库，登录接口按开关启用禁用（密码关掉时返回明确错误码）<br>5. **鉴权精细豁免管理（新菜单）**：按应用 / 服务 / 接口维度配置校验豁免（加白理由、有效期、审计人），平台端落库并下发（client 配置中心拉取或网关下发）；CLIENT/PLATFORM 控制位切换时两侧配置生效关系明确<br>6. **开放认证调用方约束**：调用方白名单 / IP 限制 / 每分钟限流，复用 API Key 作为调用凭证<br>7. **菜单按权限动态渲染**：permCodes 已下发到前端，补菜单过滤逻辑（当前全量可见，纯前端改动）<br>8. **配置 / 路由变更审计**：复用 tb_gw_api_key_log 审计表模式，记录「谁在何时改的、改前值」，配置页与路由页增加变更记录查看 | （随版本开发记录） |
| **v0.3** | 📋 计划中 | 待定 | 待定 | zengguiwan | 1. **单点登录接真**（假页面优先 4，工作量最大）：OIDC Provider 对接 + 回调路由 + 账号映射 + 配置加密存储，视对接方需求排期<br>2. **调用量报表页**：按 Key / 路由 / 状态码聚合 trace 数据出报表（tb_gw_api 表已有，聚合接口与页面待建）<br>3. **服务发现控制台增强**：实例手动下线 / 权重调整 / 健康剔除的可视化操作（当前只能被动等心跳超时）<br>4. **日志追踪前端深化**：高级筛选组合、链路瀑布图详情、慢调用定位（后端 span/log/拓扑接口已就绪）<br>5. **告警体系**：规则引擎（QPS 阈值 / 错误率 / 服务离线触发）+ 通知渠道（邮件 / Webhook）；总览页已有告警设计稿<br>6. **系统接入页升级**：从静态文档向导改为真实接入向导（选应用 → 生成依赖坐标与 yml 片段 → 校验注册结果）<br>7. **角色管理语义收敛**：与分组权限（PERM 分组）合并，或补独立角色 CRUD（当前为占位页）<br>8. **数据统计导出**：报表导出 Excel/CSV | （随版本开发记录） |
| **v0.4+** | 📋 长期规划 | 待定 | 待定 | zengguiwan | 1. **灰度发布 / 蓝绿部署**：tb_gw_route 路由模型扩展 version / weight 字段，按比例分流<br>2. **多租户**：账号、Key、路由、统计全链路增加 tenant_id 隔离<br>3. **可观测性升级**：Prometheus 指标暴露（QPS/RT/错误率/连接数）、链路采样率配置、日志聚合（ELK/Loki）<br>4. **JWT 密钥托管**：KMS 接入与密钥定期轮换（当前走环境变量 / 配置文件）<br>5. **质量与工程化**：自动化测试基线（后端单测 / 集成测试、前端 E2E）、CI/CD 流水线（替代本地 restart.sh）<br>6. **多语言正式 SDK**：Python / Node 从冒烟级升级为正式发布版（含文档与版本管理） | （随版本开发记录） |

---

## 说明

### 版本划分依据

- **v0.1（2026-11-04 ~ 2026-11-06，32 项功能 / 9 项 Bug）**：以「平台能跑通、能演示」为目标，git 50 次提交、PLAN.md 26 条变更记录归类。当前 21 个菜单中 13 个已上线（有真实后端交互）、8 个未上线（占位页 / 静态页，见悬浮灰标）。
- **v0.2**：对应 PLAN.md 的 **P0 缺口**（影响「能交付」的项），主线是两件事——网关生产底线能力（限流、会话管理）和把 4 个「假页面」接真。
- **v0.3**：对应 **P1**（体验与运维），以 SSO 对接为最大单项。
- **v0.4+**：对应 **P2**（长期演进）。

### 明确不做（避免反复讨论，详见 PLAN.md）

- 不单独拆 auth / authz 服务（ADR-001、ADR-002）
- 不重做统一响应结构（ADR-005）
- 不引入运行时微前端框架（ADR-006）
- 不用 JWT 承载权限点（走 permCodes 接口 / 缓存）

### 维护约定

- 每个版本开发期间，功能与 Bug 列表持续追加，发布时冻结；
- 版本时间点确定后更新本表；变更明细仍以 `docs/PLAN.md` 变更记录为准。
