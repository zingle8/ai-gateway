# AI 网关平台 · 前端工程（@zimo）

Vue 3 + Vite + Element Plus，采用 **npm workspaces 单体仓库（monorepo）** 组织，
按业务域拆成独立的模块包，每个前端模块与后端微服务一一对应（前端微服务化）。

## 技术栈

环境要求：**Node.js ≥ 20**（仓库根目录 `.nvmrc` 声明为 22，`nvm use` 即可），npm ≥ 9（支持 workspaces）。

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| vue | ^3.5.13 | 组合式 API（`<script setup>`） |
| vue-router | ^4.4.5 | 路由，由主应用统一装配 |
| pinia | ^2.2.6 | 全局用户态 |
| element-plus | ^2.8.8 | UI 组件库 |
| axios | ^1.7.7 | HTTP 客户端 |
| vite | ^5.4.11 | 构建工具 |

## 目录结构

```
frontend/
├── package.json              # 根 workspace（@zimo/gateway-frontend，统一依赖与脚本）
├── packages/                 # 与业务无关的公共包
│   ├── shared/               # @zimo/shared：request / store / utils / 基础样式
│   └── ui/                   # @zimo/ui：Shell 布局外壳、侧边菜单、顶部栏、占位页、折线图 TrendChart
├── modules/                  # 业务模块（每个模块 = 一个前端微服务边界）
│   ├── overview/             # @zimo/module-overview  网关总览
│   ├── stats/                # @zimo/module-stats     数据统计（多维过滤）
│   ├── discovery/            # @zimo/module-discovery 服务治理（服务发现…）
│   ├── trace/                # @zimo/module-trace     可观测（日志追踪…）
│   ├── auth/                 # @zimo/module-auth      认证与安全（用户管理 / 登录方式 / SSO / 令牌会话 / 安全策略）
│   ├── system/               # @zimo/module-system    系统管理（角色管理 / 系统设置）
│   └── onboard/              # @zimo/module-onboard   接入中心（系统接入 / 自动接入）
└── apps/
    └── portal/               # @zimo/portal：主应用（壳工程，唯一入口）
```

### 模块内部约定

```text
modules/<name>/
├── package.json    # name: @zimo/module-<name>，main: index.js
├── index.js        # 对外只导出 { name, menu, routes }
├── src/
│   ├── api.js      # 只访问本模块对应的后端微服务
│   └── views/      # 页面组件
```

- 模块之间**互不引用**，只能通过 `@zimo/shared` / `@zimo/ui` 复用能力；
- 模块自带 `menu`（菜单）与 `routes`（路由），由主应用在 `src/router/index.js` 中 `flatMap` 装配，
  新增模块只需「建包 + 在 portal 依赖与 modules 数组里注册一行」。
- `menu` 的 `icon` 写 **Element Plus 图标组件名**（如 `'Odometer'`），由 `apps/portal/src/main.js`
  按需全局注册；新增菜单图标要在那里补一行 import + 注册，否则 `<component :is>` 解析不到、图标不显示。

## 侧边菜单结构

```
网关总览 / 数据统计 / 服务发现 / 日志追踪 / 服务拓扑     ← 可观测，平铺
接入中心：系统接入 / 自动接入 / 路由配置 / 上线发布        ← 分组
认证与安全：用户管理 / 登录方式 / 单点登录 / 令牌与会话 / 安全策略   ← 分组
系统管理：角色管理 / 系统设置                            ← 分组（用户管理已迁至认证与安全）
```

- 侧边栏保持二级子菜单；**单个页面内部内容块较多时**，用 `@zimo/ui` 的 `PaneTabs` 做页内切换：
  - 登录方式：登录因子 / 验证码通道
  - 单点登录：身份提供方 / 登录审计
  - 令牌与会话：在线会话 / 令牌吊销记录（概览指标卡放在 Tab 外，两个 Tab 共用）
  - 安全策略：登录保护 / 验证码 / 密码策略 / 双因子 / 令牌有效期
  - 用户管理：只有一个内容块，不做页内 Tab
- 页内 Tab 只换内容、不绑路由（与侧边栏子菜单的路由级导航区分）；`PaneTabs` 支持 `badge`
  显示条数，如「身份提供方 3 / 登录审计 5」；
- 用户管理路线迁移：`/system/user` → `/auth/user`，旧地址保留 redirect 兼容收藏夹；
- 「登录方式 / 单点登录 / 令牌与会话 / 安全策略」四页为**前端设计稿**：交互可用、数据为本地示例，
  接口约定见 `modules/auth/src/api.js` 末尾注释，后端就绪后替换数据源即可，页面结构不动；
- 菜单 `icon` 需在 `apps/portal/src/main.js` 注册（本次新增 `Lock / Key / Link / Timer / SetUp / Tools`）。

## 前端模块 ↔ 后端微服务

| 前端模块包 | 路由前缀 | 后端微服务 | 端口 |
| --- | --- | --- | --- |
| `@zimo/module-overview` | `/api/overview` | `jv-api-gateway-overview` | 28082 |
| `@zimo/module-stats` | `/api/stats` | `jv-api-gateway-overview` | 28082 |
| `@zimo/module-discovery` | `/api/discovery` | `jv-api-gateway-discovery` | 28083 |
| `@zimo/module-trace` | `/api/trace` | `jv-api-gateway-trace` | 28084 |
| `@zimo/module-auth` + `@zimo/shared/api/auth` | `/api/users`、`/api/auth` | `jv-api-gateway-user`（认证中心） | 28081 |
| `@zimo/module-system` | —（角色 / 系统设置页待接入） | `jv-api-gateway-user` | 28081 |
| `@zimo/module-onboard` | `/api/discovery` | `jv-api-gateway-discovery` | 28083 |
| 全部模块（统一入口） | `/api/**` | `jv-api-gateway-proxy`（按路由转发） | 28090 |

> 后端另有对外复用模块 `jv-api-gateway-client-common`（公共模块）与 `jv-api-gateway-client-starter`（接入子模块），
> 业务系统引入后自动注册并上报调用日志，数据最终落到这里的服务发现与日志追踪里。
> 详见 [../docs/client-integration.md](../docs/client-integration.md)。

开发态由 `apps/portal/vite.config.js` 的 `server.proxy` 转发：**所有 `/api` 请求统一指向 `jv-api-gateway-proxy`（28090）**，
再由网关按 `tb_gw_route` 分发到各微服务。模块内只写相对路径（如 `/trace/logs`），跨域与地址变化对模块透明。

这样控制台自身的每次请求也会被网关采集（日志追踪里可见），后端换端口只改路由表，前端无需改动。

## 命名规范

公司名称统一为 `com.zimo`，前端对应：

- npm scope：`@zimo/*`
  - 公共包：`@zimo/shared`、`@zimo/ui`
  - 业务模块：`@zimo/module-overview`、`@zimo/module-stats`、`@zimo/module-discovery`、`@zimo/module-trace`、`@zimo/module-system`、`@zimo/module-onboard`
  - 应用：`@zimo/portal`
- Vite 别名：与包名保持一致（`@zimo/shared`、`@zimo/ui`、`@zimo/module-*`）
- localStorage 键：`gw_token`、`gw_user`（`gw` = gateway）

## 常用命令

```bash
npm install          # 在根目录执行一次，依赖提升到根 node_modules
npm run dev          # 启动主应用（等价 npm run dev --workspace @zimo/portal）
npm run build        # 生产构建
npm run preview      # 预览构建产物
```

## 已实现页面

- 登录页：`apps/portal/src/views/login/index.vue`（默认账号 `admin` / `Admin@123`）
- 时间区间（四页共用）：`@zimo/ui` 的 `TimeRangePicker` + `@zimo/shared` 的 `TIME_RANGES`，
  统一 10 档 `1分钟 / 5分钟 / 10分钟 / 30分钟 / 1小时 / 2小时 / 24小时 / 1天 / 7天 / 30天`，
  网关总览、数据统计、服务发现、服务拓扑四处样式与选项完全一致；趋势图桶宽由区间自动推导（固定 60 点）
- 网关总览：9 张指标卡（两排 5 + 4，含 P95 / P99 延迟分位）、**请求量趋势 + QPS 趋势**（同源双序列，请求量在前）、运行时信息、实例列表
  - 顶部区间选择器统一控制指标卡 / QPS 趋势 / 实例榜口径；`24小时` 为滚动窗口，`1天` 为自然日（从 00:00 起）
  - 趋势图 X 轴标签自适应：1 分钟内带秒、跨天带日期、≥7 天只到日
- 服务发现 / 服务治理：顶部**请求量趋势 + QPS 趋势**两张图（QPS 在请求量之后，随「应用 / 服务」筛选与区间联动）；11 列列表（应用ID、应用名、服务名、**接口数**、**调用次数**、端口、语言类型、版本号、健康状态、入站/出网流量、操作）
  + 接口数取 `tb_gw_api` 已登记记录，调用次数按顶部区间统计（未登记接口的服务显示 0，属真实数据）
  + 服务详情抽屉（含端口、实例数、QPS、平均 RT、注册时间）；顶部
  「全部应用 / 全部服务」双下拉筛选（选项来自 `/discovery/filters`，服务下拉随所选应用级联）
  + 顶部区间选择器决定平均耗时与流量的聚合窗口（QPS 恒为近 60 秒瞬时速率，详情里已标注）
- 日志追踪：列表含「请求参数」列 + 详情抽屉展示 Query 参数、表单参数、请求体、响应体与 Header
  （抽屉按 id 拉详情接口，列表不返回大字段；支持方法 / 接口名 / **应用 / 服务** / 状态码 / 错误类型 / 协议 / 参数检索，
  应用与服务下拉取自 `/trace/logs/filters`，服务随所选应用级联）
  - 详情抽屉顶部新增「调用链路」瀑布图：按 `traceId` 拉 `/trace/traces/{traceId}`，
    以 `parentSpanId` 组树、层级缩进，条形 `left/width` 按全链路时长归一化，错误 span 标红；
    span 表无数据（阶段一上线前的历史日志）时不显示该区块
  - 支持 `?traceId=xxx` 直达：从服务拓扑的慢链路列表点击行跳转过来时，自动检索并打开详情
- 服务拓扑（`/topology`）：`/trace/topology` 的调用边画成分层 SVG 拓扑图，**默认全局**（所有应用 × 所有服务的真实调用）——
  + 顶部「全部应用 / 全部服务」双下拉过滤，服务随应用级联；过滤后节点由剩余边自动推导
  - 顶部区间选择器与其它三页统一 10 档；≤30 分钟区间后端直接对 span 表实时聚合，保证分钟级精度
  - 入口服务（无入边）在左，每沿一条边加深一层；同源多条边的起点按序分散，标签沿曲线错开
  - 线宽 = 调用量（对数压缩 2~6px）、颜色 = 错误率（绿 / 橙 <5% / 红 ≥5%）、箭头指向被调方
  - 节点名剥离公共前缀（`jv-api-gateway-`）只显示有区分度的部分，完整名在悬浮提示里
  - 透明宽描边做边命中区（真实线宽仅 2~6px，直接 hover 难点中），节点/边悬浮均有提示
  - 下方「慢链路 Top 10」表格：按链路总耗时排名，显示入口服务/接口、span 数、错误数；
    点击行跳到 `/trace?traceId=...` 看完整瀑布图
- 数据统计：按 应用 / 服务 / 接口 / 方法 + 统一时间区间过滤（指标卡与趋势共用一个 range，不再两套口径），
  输出 10 张指标卡（含 P95 / P99 耗时）、请求量趋势（与总览共用分桶逻辑）、
  状态码与方法分布、Top 服务 / Top 接口
- 用户管理：账号分页表格 + 新增/编辑/启用禁用/删除
- 路由配置：`tb_gw_route` 的增删改查 + 启用停用开关
- 其余 demo 菜单统一落到 `@zimo/ui` 的占位页

## UI 规范（`@zimo/shared/src/styles`）

设计变量集中在 `packages/shared/src/styles/index.css` 的 `:root`，组件内只用 `var(--gw-*)`，不写死色值。

折线图统一用 `@zimo/ui` 的 `TrendChart`（`packages/ui/src/TrendChart.vue`），**不要再手写 `<svg><polyline>`**：

```vue
<TrendChart :times="trend.times" :values="trend.values" unit=" 次" series-name="请求数" :height="160" />
```

内置能力：Y 轴 5 档刻度（步长自动归一到 1/2/5×10ⁿ）、X 轴时间标签抽稀（最多 7 个）、面积渐变、
悬浮十字准线 + 数据点 + tooltip（跟随鼠标，靠右自动翻转）、全 0 时显示「该区间暂无调用数据」、
宽度随容器自适应（`ResizeObserver`，SVG 用真实像素绘制，线宽不会像 `preserveAspectRatio="none"` 那样被拉伸变形）。

侧边菜单三种状态的取色（`packages/ui/src/Sidebar.vue`）：

| 状态 | 背景 | 文字 |
| --- | --- | --- |
| 悬停 | `--sidebar-hover` `#F2FBF6` | `--gw-text` |
| 点击瞬间 | `--sidebar-active` `#E7F7ED` | `--gw-text` |
| 激活（当前路由） | `--sidebar-active` `#E7F7ED` | `--sidebar-active-text` `#15803D` + 600 |

> 新增菜单态样式时务必确认变量已在 `:root` 定义：CSS 变量未定义会导致整条 `background` 声明被丢弃，
> 表现为「写了样式但完全不生效」。

## 响应式适配

断点与行为（样式集中在 `packages/shared/src/styles/index.css` 与 `components.css`）：

| 断点 | 变化 |
| --- | --- |
| ≤ 1400px | 指标卡内边距 / 字号收紧，`stats` 页两栏图表降为单栏 |
| ≤ 1100px | 侧边栏收起为 **60px 图标栏**（悬停有 `title` 提示，分组间有分隔线）；顶栏隐藏页面副标题 |
| ≤ 768px | 过滤栏变为 **两列网格**（控件占满半行，说明文字独占一行）；卡片标题行允许换行（趋势粒度按钮换行）；抽屉宽度 `min(560/620px, 94vw)`；分页条居中换行；日志追踪操作列收窄为「详情」；顶栏隐藏 Nacos 标签与昵称 |
| ≤ 480px | 指标卡单列 |

约定：

- **指标卡**用 `repeat(auto-fit, minmax(215px, 1fr))`，不要改回固定列数——215px 是保证 1440 宽仍是 5 列的上限值；
- **过滤栏**统一用全局 `.filter-bar`，**不要在模块 scoped 里重复定义**（scoped 选择器权重更高，会把全局的窄屏媒体查询全部盖掉）；
- 页面根容器统一用 `.page`（自带 `padding` 与纵向滚动），否则内容超高时底部会被 `shell-content` 的 `overflow: hidden` 裁掉；
- 表格列用固定 `width` + Element Plus 自带横向滚动，不要为了适配窄屏去删列。
