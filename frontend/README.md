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
│   └── ui/                   # @zimo/ui：Shell 布局外壳、侧边菜单、顶部栏、占位页
├── modules/                  # 业务模块（每个模块 = 一个前端微服务边界）
│   ├── overview/             # @zimo/module-overview  网关总览
│   ├── stats/                # @zimo/module-stats     数据统计（多维过滤）
│   ├── discovery/            # @zimo/module-discovery 服务治理（服务发现…）
│   ├── trace/                # @zimo/module-trace     可观测（日志追踪…）
│   ├── system/               # @zimo/module-system    系统管理（用户管理…）
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

## 前端模块 ↔ 后端微服务

| 前端模块包 | 路由前缀 | 后端微服务 | 端口 |
| --- | --- | --- | --- |
| `@zimo/module-overview` | `/api/overview` | `jv-gateway-overview` | 28082 |
| `@zimo/module-stats` | `/api/stats` | `jv-gateway-overview` | 28082 |
| `@zimo/module-discovery` | `/api/discovery` | `jv-gateway-discovery` | 28083 |
| `@zimo/module-trace` | `/api/trace` | `jv-gateway-trace` | 28084 |
| `@zimo/module-system` + `@zimo/shared/api/auth` | `/api/users`、`/api/auth` | `jv-gateway-user` | 28081 |
| `@zimo/module-onboard` | `/api/discovery` | `jv-gateway-discovery` | 28083 |
| 全部模块（统一入口） | `/api/**` | `jv-gateway-proxy`（按路由转发） | 28090 |

> 后端另有对外复用模块 `jv-gateway-client-common`（公共模块）与 `jv-gateway-client-starter`（接入子模块），
> 业务系统引入后自动注册并上报调用日志，数据最终落到这里的服务发现与日志追踪里。
> 详见 [../docs/client-integration.md](../docs/client-integration.md)。

开发态由 `apps/portal/vite.config.js` 的 `server.proxy` 转发：**所有 `/api` 请求统一指向 `jv-gateway-proxy`（28090）**，
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
- 网关总览：9 张指标卡（两排 5 + 4，含 P95 / P99 延迟分位）、QPS 趋势图、运行时信息、实例列表
  - 趋势图支持粒度切换：`1分钟 / 5分钟 / 10分钟 / 30分钟 / 1小时 / 2小时 / 24小时`
    （固定 120 点，窗口随粒度放大，X 轴标签自动抽稀）
- 服务发现 / 服务治理：11 列列表 + 服务详情抽屉（含端口、实例数、注册时间）；顶部
  「全部应用 / 全部服务」双下拉筛选（选项来自 `/discovery/filters`，服务下拉随所选应用级联）
- 日志追踪：列表含「请求参数」列 + 详情抽屉展示 Query 参数、表单参数、请求体、响应体与 Header
  （抽屉按 id 拉详情接口，列表不返回大字段；支持方法 / 接口名 / 服务 / 状态码 / 错误类型 / 协议 / 参数检索）
- 数据统计：按 应用 / 服务 / 接口 / 方法 + 时间窗口（1/6/12/24 小时）过滤，
  输出 10 张指标卡（含 P95 / P99 耗时）、请求量趋势（与总览共用 7 档粒度与 120 点窗口）、
  状态码与方法分布、Top 服务 / Top 接口
- 用户管理：账号分页表格 + 新增/编辑/启用禁用/删除
- 路由配置：`tb_gw_route` 的增删改查 + 启用停用开关
- 其余 demo 菜单统一落到 `@zimo/ui` 的占位页

## UI 规范（`@zimo/shared/src/styles`）

设计变量集中在 `packages/shared/src/styles/index.css` 的 `:root`，组件内只用 `var(--gw-*)`，不写死色值。

侧边菜单三种状态的取色（`packages/ui/src/Sidebar.vue`）：

| 状态 | 背景 | 文字 |
| --- | --- | --- |
| 悬停 | `--sidebar-hover` `#F2FBF6` | `--gw-text` |
| 点击瞬间 | `--sidebar-active` `#E7F7ED` | `--gw-text` |
| 激活（当前路由） | `--sidebar-active` `#E7F7ED` | `--sidebar-active-text` `#15803D` + 600 |

> 新增菜单态样式时务必确认变量已在 `:root` 定义：CSS 变量未定义会导致整条 `background` 声明被丢弃，
> 表现为「写了样式但完全不生效」。
