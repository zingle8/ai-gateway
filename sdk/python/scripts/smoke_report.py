"""Python SDK 冒烟脚本（无 Web 框架依赖，零第三方依赖）。

验证完整链路：注册 → 心跳 → 手动上报一条调用日志 → 下线 → 验签演示。

运行::

    export PYTHONPATH=/path/to/ai-gateway/sdk/python:$PYTHONPATH
    python3 sdk/python/scripts/smoke_report.py

预期：脚本各步成功；控制台「服务发现」短暂出现 test-py-smoke（下线后消失）；
「日志追踪」能看到 /job/smoke 这条调用日志。
"""

import sys
import time

from jv_gateway import GatewayClient, verify_token


def main():
    client = GatewayClient(
        server_addr="http://127.0.0.1:28090/api",
        app_id="app-regtech", app_name="NCS RegTech 监管科技",  # 按被测项目填所属应用
        service_id="test-py-smoke", service_name="Python冒烟服务", port=0,          # 0 = 无 HTTP 端口
        client_token="dev-gateway-token",                 # 与网关 gateway.proxy.auth.client-token 同值
    )
    print("① 注册：", "成功" if client.register() else "失败")
    print("② 心跳：", "成功" if client.heartbeat() else "失败")

    client.start()  # 启动上报线程
    client.report(method="POST", path="/job/smoke", status_code=200, cost_ms=42,
                  req_body='{"hello":"python"}', res_body='{"ok":true}',
                  req_bytes=18, res_bytes=12)
    print("③ 已上报一条调用日志（3 秒内批量发出）")
    time.sleep(4)

    print("④ 下线：", "成功" if client.deregister() else "失败")
    client.stop()

    demo = verify_token(client.server_addr, "invalid-token")
    print("⑤ 验签演示（无效 token 应 valid=False）：", demo)
    return 0


if __name__ == "__main__":
    sys.exit(main())
