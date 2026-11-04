# JV Gateway Python SDK（自动接入）

业务系统引入后**启动即自动注册**到网关平台，并持续采集请求/响应上报调用日志，
效果等同于 Java 版的 `jv-gateway-client-starter`。

**零第三方依赖**，只用标准库，Python 3.8+ 可用。

## 目录

```
sdk/python/
├── jv_gateway/
│   ├── __init__.py      # 导出 GatewayClient
│   ├── client.py        # 核心：注册 / 心跳 / 下线 / 采集 / 批量上报
│   ├── wsgi.py          # WSGI 中间件（Flask / Django / Bottle）
│   └── asgi.py          # ASGI 中间件（FastAPI / Starlette）
└── examples/
    ├── wsgi_demo.py     # 零依赖可运行示例（标准库 wsgiref）
    └── quickstart.py    # 无 Web 框架：脚本 / 定时任务
```

无需安装，把 `sdk/python` 加到 `sys.path`，或用 `PYTHONPATH` 指向它即可；
也可以 `pip install -e sdk/python`（需自行补一个 `pyproject.toml`）。

## 一、Flask（WSGI）

```python
from flask import Flask
from jv_gateway import GatewayClient
from jv_gateway.wsgi import WSGITraceMiddleware

app = Flask(__name__)

client = GatewayClient(
    app_id="app-order",              # 应用编码
    app_name="订单中心",              # 应用名称
    service_name="order-service",    # 服务名
    port=8000,                       # 服务端口
)
client.start()                       # 注册 + 启动心跳/上报线程

# 关键一行：采集请求与响应（必须包在 wsgi_app 上）
app.wsgi_app = WSGITraceMiddleware(app.wsgi_app, client)

@app.get("/api/orders")
def orders():
    return {"code": 200, "data": []}
```

## 二、FastAPI（ASGI）

```python
from fastapi import FastAPI
from jv_gateway import GatewayClient
from jv_gateway.asgi import ASGITraceMiddleware

client = GatewayClient(
    app_id="app-order",
    app_name="订单中心",
    service_name="order-service",
    port=8000,
)
client.start()

app = FastAPI()
app.add_middleware(ASGITraceMiddleware, client=client)
```

## 三、无 Web 框架（脚本 / 定时任务 / 消费者）

```python
from jv_gateway import GatewayClient

client = GatewayClient(app_id="app-job", service_name="recon-job", port=0)
client.start()

# 业务执行完后手动上报
client.report(method="POST", path="/job/reconcile", status_code=200, cost_ms=52)

client.stop()      # 会把队列里剩余日志刷出去，再下线
```

需要更完整的字段（请求体、响应体、traceId 等）时用 `build_trace_log()`：

```python
client.report(client.build_trace_log(
    method="POST", path="/job/reconcile", status_code=200, cost_ms=52,
    req_body='{"date":"2026-11-03"}', res_body='{"matched":1200}',
    req_content_type="application/json", res_content_type="application/json",
))
```

## 配置项

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `server_addr` | `http://127.0.0.1:28080/api` | **平台统一入口**，见下方注意 |
| `app_id` | `app-demo` | 应用编码，控制台按此筛选 |
| `app_name` | `示例应用` | 应用名称 |
| `service_name` | `demo-service` | 服务名，注册去重键（同服务名重复注册即更新） |
| `port` | `$PORT` 或 `8000` | 服务端口；纯脚本可填 `0` |
| `ip` | 自动探测 | 注册用 IP |
| `language` | `Python 3.x` | 语言类型，控制台展示 |
| `version` | `v1.0.0` | 版本号 |
| `registry_enabled` | `True` | 是否注册 + 心跳 |
| `trace_enabled` | `True` | 是否采集上报 |
| `heartbeat_interval` | `30` 秒 | 心跳间隔（下限 5 秒） |
| `flush_interval` | `3` 秒 | 批量上报间隔（下限 0.5 秒） |
| `batch_size` | `50` | 单批最多条数 |
| `queue_size` | `1024` | 内存队列容量，**满了直接丢弃** |
| `timeout` | `5` 秒 | 单次 HTTP 超时 |
| `max_body_size` | `8192` | 报文体采集上限，超出截断 |
| `sample_rate` | `1.0` | 采样率 0~1 |
| `exclude_paths` | `/actuator`、`/error`、`/favicon.ico`、`/health` 等 | 不采集的路径前缀 |

## 工作原理

1. **注册**：`start()` 时 `POST {server_addr}/discovery/services/register`，
   带上 appId / serviceName / ip / port / language / version；之后每 30 秒心跳一次；
   `stop()` 时 `DELETE .../deregister` 主动下线。
2. **采集**：中间件在请求前后各取一次时间戳，组装成平台约定的日志模型
   （`api`、`statusCode`、`cost`、`errType`、报文体、header 等）。
   `errType` 是中文标签，按状态码推断：`401/403→鉴权失败`、`404→路由失败`、
   `400/422→参数校验失败`、`429→限流拒绝`、`504→上游响应超时`、`5xx→上游响应异常`、其余 `无`。
3. **上报**：日志进内存队列立即返回，后台线程每 3 秒（或满 50 条）批量
   `POST {server_addr}/trace/logs/batch`。

**任何环节失败都不影响业务**：注册/心跳失败只记日志，上报失败直接丢弃当前批次，
队列满用 `put_nowait` 丢弃而不是阻塞。`stop()` 与 `atexit` 都会兜底刷写一次。

## 注意：server_addr 必须填统一入口

SDK 在 `server_addr` 之后拼接不同前缀，分别打到**不同微服务**：

| 前缀 | 目标微服务 |
| --- | --- |
| `/discovery/**` | jv-gateway-discovery |
| `/trace/**` | jv-gateway-trace |

所以不能填某个微服务的端口，否则另一半请求必然 404。

- **本地开发**：`http://127.0.0.1:28080/api`（vite dev server 已按前缀代理）
- **生产**：Nginx / Spring Cloud Gateway 统一入口，配置见 `docs/client-integration.md`

## 快速验证

```bash
# 1. 起演示服务（无需安装任何依赖）
cd sdk/python && python3 examples/wsgi_demo.py

# 2. 制造几条调用（含 200 / 500 / 404）
curl http://127.0.0.1:28888/api/orders
curl http://127.0.0.1:28888/api/orders/boom
curl http://127.0.0.1:28888/not-exist

# 3. 打开控制台
#    服务发现：应出现 python-order-service（语言 Python 3.x）
#    日志追踪：应出现上述三条调用，错误类型分别为 无 / 上游响应异常 / 路由失败
open http://127.0.0.1:28080/discovery
```

## 与 Java SDK 的差异

| 能力 | Java (`jv-gateway-client-starter`) | Python (`jv_gateway`) |
| --- | --- | --- |
| 注册 / 心跳 / 下线 | ✅ | ✅ |
| 自动采集 | Servlet Filter | WSGI / ASGI 中间件 |
| 异步批量上报 | ✅ | ✅（后台线程） |
| 采样率 / 排除路径 / 报文体截断 | ✅ | ✅ |
| 上报失败重试 | ❌ 直接丢弃 | ❌ 直接丢弃（保持一致） |
| Spring 自动装配 | ✅ | —（显式 `start()`） |

> 队列满、上报失败都是**丢弃而非重试**，这是刻意与 Java 端保持一致的取舍：
> 采集属于旁路能力，宁可丢日志也不能拖慢或压垮业务。
