# -*- coding: utf-8 -*-
"""
最小示例：不依赖任何 Web 框架，定时任务 / 脚本 / 消费者也能接入。

运行::

    python3 examples/quickstart.py
"""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from jv_gateway import GatewayClient  # noqa: E402


def main():
    client = GatewayClient(
        server_addr="http://127.0.0.1:28080/api",
        app_id="app-python-job",
        app_name="Python 对账任务",
        service_name="python-recon-job",
        port=0,                 # 不是 Web 服务，端口填 0
        version="v1.0.0",
        flush_interval=1.0,
        heartbeat_interval=15.0,
    )

    client.start()

    # 方式一：手动上报一条（字段最全，推荐）
    started = time.time()
    try:
        # ... 这里是你真实的业务调用 ...
        time.sleep(0.05)
        status_code = 200
        err = None
    except Exception as e:          # 业务异常也要上报，便于排查
        status_code = 500
        err = str(e)
    cost = int((time.time() - started) * 1000)

    client.report(
        client.build_trace_log(
            method="POST",
            path="/job/reconcile/daily",
            status_code=status_code,
            cost_ms=cost,
            req_body='{"date":"2026-11-03"}',
            res_body='{"matched":1200,"diff":3}',
            req_content_type="application/json",
            res_content_type="application/json",
            err_type=err and "上游响应异常" or None,
        )
    )

    # 方式二：直接传字段，SDK 自动补全 appId / serviceName / errType 等
    client.report(method="GET", path="/job/reconcile/status", status_code=200, cost_ms=3)

    # 立即刷写（脚本结束前建议显式调用；client.stop() 也会自动刷）
    sent = client.flush()
    print("本次上报 %d 条" % sent)

    client.stop()
    print("已下线")


if __name__ == "__main__":
    main()
