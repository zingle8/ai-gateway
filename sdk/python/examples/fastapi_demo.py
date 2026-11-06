"""FastAPI 接入示例：注册 + 采集 + 入口鉴权三件套。

运行::

    export PYTHONPATH=/path/to/ai-gateway/sdk/python:$PYTHONPATH
    uvicorn examples.fastapi_demo:app --port 8000

FastAPI 若未安装：pip install "fastapi[standard]"
"""

from fastapi import FastAPI

from jv_gateway import GatewayClient
from jv_gateway.asgi import ASGIAuthMiddleware, ASGITraceMiddleware
from jv_gateway.auth import GatewayAuth

client = GatewayClient(
    server_addr="http://127.0.0.1:28090/api",
    app_id="app-order", app_name="订单中心",
    service_id="order-service", service_name="订单服务", port=8000,
    client_token="dev-gateway-token",        # 与网关 gateway.proxy.auth.client-token 同值
)
client.start()

app = FastAPI(title="order-service")
app.add_middleware(ASGIAuthMiddleware, auth=GatewayAuth(client))   # 入口鉴权（可选）
app.add_middleware(ASGITraceMiddleware, client=client)             # 自动采集


@app.get("/orders/{order_id}")
def get_order(order_id: int):
    return {"orderId": order_id, "status": "PAID"}


@app.on_event("shutdown")
def shutdown():
    client.stop()   # 刷出剩余日志并主动下线
