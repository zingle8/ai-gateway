# jv-gateway —— AI 网关平台 Node.js 接入 SDK

零第三方依赖（`node:http` / `node:crypto`），Node.js 16+。
与 Java SDK（`jv-api-gateway-client-starter`）、Python SDK（`sdk/python`）上报协议完全一致：
注册/心跳/下线 → `/discovery/services/*`（下线 DELETE）；调用日志/span → `/trace/logs|spans/batch`；
上报请求自动携带共享密钥头 `X-Gateway-Client-Token`（网关校验端：`gateway.proxy.auth.client-token`）。

## 目录

```
sdk/nodejs/
├── index.js                 # 包入口
├── src/
│   ├── client.js            # GatewayClient：注册/心跳/下线/手动上报；verifyToken
│   ├── reporter.js          # TraceReporter：队列 + 定时批量上报（满即丢，3 秒 flush）
│   ├── auth.js              # GatewayAuth：入口鉴权（回调 /open/auth/token/verify + 缓存）
│   ├── middleware.js        # traceExpress / traceWrap（原生 http）自动采集
│   └── _http.js             # node:http 封装：自动携带 X-Gateway-Client-Token
├── examples/express_demo.js # Express 三件套示例（注册+采集+入口鉴权）
├── scripts/smoke_report.js  # 无框架冒烟脚本（注册→心跳→上报→下线→验签演示）
└── README.md
```

## 引用方式

```js
// 直接相对路径引用（零安装）
const { GatewayClient } = require('../jv-gateway/index.js')

// 或复制本目录为业务项目的 jv-gateway/ 后 require('jv-gateway')
```

## 快速开始

```js
const { GatewayClient } = require('jv-gateway')

const client = new GatewayClient({
  serverAddr: 'http://127.0.0.1:28090/api',
  appId: 'app-order', appName: '订单中心',
  serviceName: 'order-service', port: 8000,
  clientToken: 'dev-gateway-token',   // 与网关 gateway.proxy.auth.client-token 同值
})
client.start()      // 注册 + 心跳 + 上报线程
```

- Express：`app.use(traceExpress(client))`（放路由之前）
- 原生 http：`traceWrap(client, http.createServer(handler))`
- 脚本 / 定时任务：`client.report({ method, path, statusCode, costMs, ... })`
- 退出前 `await client.stop()`：刷出剩余日志并主动下线

## 入口鉴权（防止绕过网关直连业务系统）

```js
const { GatewayAuth } = require('jv-gateway')

app.use(new GatewayAuth(client).express())          // Express
auth.wrap(http.createServer(handler))               // 原生 http
```

- 凭证语义与网关一致：`Authorization: Bearer <JWT>` 或 `X-Api-Key`（gwak- 前缀）；
- 验签回调 `{serverAddr}/open/auth/token/verify`，结果缓存 60 秒（`cacheSeconds` 可调）；
- `exemptPaths` 豁免健康检查等；`failOpen: true` 网关不可达时放行（默认 false 安全优先）；
- **SDK 数据上报是出站调用，不受入口鉴权影响**——即使调用方验签失败，
  调用日志与 span 照常上报，网关平台始终能看到这家接入方的真实流量。

## 验证

```bash
node sdk/nodejs/scripts/smoke_report.js
```

预期：脚本五步全成功；控制台「服务发现」短暂出现 `node-smoke-service`（下线后消失）；
「日志追踪」能看到 `/job/smoke` 这条调用日志。
