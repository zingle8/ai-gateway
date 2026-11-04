# -*- coding: utf-8 -*-
"""
零依赖示例：用标准库 wsgiref 起一个 WSGI 服务，接入网关平台。

直接运行（不需要装 Flask / FastAPI）::

    python3 examples/wsgi_demo.py

然后访问 http://127.0.0.1:28888/api/orders ，观察控制台「服务发现」与「日志追踪」。
"""

import json
import os
import sys
from pathlib import Path
from wsgiref.simple_server import make_server

# 让示例能直接 import 到上一级的 jv_gateway 包
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from jv_gateway import GatewayClient          # noqa: E402
from jv_gateway.wsgi import WSGITraceMiddleware  # noqa: E402

PORT = int(os.environ.get("PORT", "28888"))

client = GatewayClient(
    # 平台统一入口：本地就是前端 dev server 的 /api（已按前缀代理到各微服务）
    server_addr=os.environ.get("JV_GATEWAY_ADDR", "http://127.0.0.1:28080/api"),
    app_id="app-python-demo",
    app_name="Python 订单中心",
    service_name="python-order-service",
    port=PORT,
    version="v1.0.0",
    flush_interval=1.0,     # 演示用，加快上报
    heartbeat_interval=10.0,
)


def app(environ, start_response):
    """一个最小的业务应用。"""
    path = environ.get("PATH_INFO", "/")

    if path == "/api/orders":
        body = json.dumps(
            {"code": 200, "message": "ok", "data": [{"id": 1, "no": "SO20261103001"}]},
            ensure_ascii=False,
        ).encode("utf-8")
        start_response("200 OK", [
            ("Content-Type", "application/json; charset=utf-8"),
            ("Content-Length", str(len(body))),
        ])
        return [body]

    if path == "/api/orders/boom":
        body = b'{"code":500,"message":"\xe6\xa8\xa1\xe6\x8b\x9f\xe4\xb8\x8a\xe6\xb8\xb8\xe5\xbc\x82\xe5\xb8\xb8"}'
        start_response("500 Internal Server Error", [
            ("Content-Type", "application/json; charset=utf-8"),
            ("Content-Length", str(len(body))),
        ])
        return [body]

    body = b'{"code":404,"message":"not found"}'
    start_response("404 Not Found", [
        ("Content-Type", "application/json; charset=utf-8"),
        ("Content-Length", str(len(body))),
    ])
    return [body]


# 关键一行：把业务应用包进采集中间件
application = WSGITraceMiddleware(app, client)


if __name__ == "__main__":
    client.start()
    print("Python 示例服务已启动: http://127.0.0.1:%d" % PORT)
    print("  正常请求: http://127.0.0.1:%d/api/orders" % PORT)
    print("  500 请求: http://127.0.0.1:%d/api/orders/boom" % PORT)
    print("  404 请求: http://127.0.0.1:%d/not-exist" % PORT)
    try:
        make_server("127.0.0.1", PORT, application).serve_forever()
    except KeyboardInterrupt:
        print("\n正在停止...")
    finally:
        client.stop()
