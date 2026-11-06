# jv_gateway —— AI 网关平台 Python 接入 SDK

零第三方依赖（标准库 `urllib` / `threading` / `queue`），Python 3.8+。
与 Java SDK（`jv-api-gateway-client-starter`）上报协议完全一致：
注册/心跳/下线 → `/discovery/services/*`；调用日志/span → `/trace/logs|spans/batch`；
上报请求自动携带共享密钥头 `X-Gateway-Client-Token`（网关校验端：`gateway.proxy.auth.client-token`）。

## 目录

```
sdk/python/
├── jv_gateway/                  # SDK 包
│   ├── client.py                # GatewayClient：注册/心跳/下线/手动上报；verify_token
│   ├── reporter.py              # TraceReporter：队列 + 后台线程批量上报（满即丢，3 秒 flush）
│   ├── wsgi.py                  # WSGITraceMiddleware（Flask/Django）+ WSGIAuthMiddleware
│   ├── asgi.py                  # ASGITraceMiddleware（FastAPI/Starlette）+ ASGIAuthMiddleware
│   ├── auth.py                  # GatewayAuth：入口鉴权（回调 /open/auth/token/verify + 缓存）
│   └── _http.py                 # urllib 封装：自动携带 X-Gateway-Client-Token
├── examples/fastapi_demo.py     # FastAPI 三件套示例（注册+采集+入口鉴权）
├── scripts/smoke_report.py      # 无框架冒烟脚本（注册→心跳→上报→下线→验签演示）
└── README.md
```

## 引用方式

```bash
# 方式一：PYTHONPATH（零安装）
export PYTHONPATH=/path/to/ai-gateway/sdk/python:$PYTHONPATH

# 方式二：pip（可编辑安装）
pip install -e /path/to/ai-gateway/sdk/python
```

## 快速开始

```python
from jv_gateway import GatewayClient

client = GatewayClient(
    server_addr="http://127.0.0.1:28090/api",
    app_id="app-order", app_name="订单中心",
    service_id="order-service", service_name="订单服务", port=8000,
    client_token="dev-gateway-token",   # 与网关 gateway.proxy.auth.client-token 同值
)
client.start()      # 注册 + 心跳 + 上报线程
```

- Flask：`app.wsgi_app = WSGITraceMiddleware(app.wsgi_app, client)`
- FastAPI：`app.add_middleware(ASGITraceMiddleware, client=client)`
- 脚本 / 定时任务：`client.report(method=..., path=..., status_code=..., cost_ms=...)`
- 退出前 `client.stop()`：刷出剩余日志并主动下线

## 入口鉴权（防止绕过网关直连业务系统）

```python
from jv_gateway.auth import GatewayAuth
from jv_gateway.asgi import ASGIAuthMiddleware

app.add_middleware(ASGIAuthMiddleware, auth=GatewayAuth(client))
```

- 凭证语义与网关一致：`Authorization: Bearer <JWT>` 或 `X-Api-Key`（gwak- 前缀）；
- 验签回调 `{server_addr}/open/auth/token/verify`，结果缓存 60 秒（`cache_seconds` 可调）；
- `exempt_paths` 豁免健康检查等；`fail_open=True` 网关不可达时放行（默认 False 安全优先）；
- **SDK 数据上报是出站调用，不受入口鉴权影响**——即使调用方验签失败，
  调用日志与 span 照常上报，网关平台始终能看到这家接入方的真实流量。

## 验证

```bash
python3 scripts/smoke_report.py
```

预期：脚本各步成功；控制台「服务发现」短暂出现 `test-py-smoke`（下线后消失）；
「日志追踪」能看到 `/job/smoke` 这条调用日志。
