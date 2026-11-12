"""主机运行指标上报（v0.2 第 10 项）。

每 60 秒采集一次，POST 到 ``{server_addr}/discovery/metrics/batch``：

- CPU：使用率、核数、1 分钟负载
- 内存：总量 / 使用率、进程 RSS
- 磁盘：总量 / 使用率
- 网络：收发速率（两次采样的累计计数差）、TCP 已建立连接数
- GPU：有 ``nvidia-smi`` 时附带利用率与显存（无 GPU 自动跳过）

优先使用 ``psutil``（可选依赖）；未安装时退化为读 ``/proc`` 的纯标准库实现，
非 Linux 平台缺失的指标留空，平台侧展示为「-」。

用法::

    from jv_gateway.metrics import MetricsReporter

    reporter = MetricsReporter(server_addr="http://127.0.0.1:28090/api",
                               app_id="app-demo", service_id="demo-service",
                               client_token="")
    reporter.start()      # 后台线程，每隔 interval 秒上报
    ...
    reporter.stop()

或直接运行本模块做一次采集并打印（不发送）::

    python -m jv_gateway.metrics --dry-run
"""

from __future__ import annotations

import json
import os
import shutil
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any

try:  # pragma: no cover - 可选依赖
    import psutil  # type: ignore
except Exception:  # pragma: no cover
    psutil = None  # type: ignore


def _read_proc_meminfo() -> tuple[int, int]:
    """返回 (总量, 可用) 字节数；非 Linux 返回 (0, 0)"""
    path = Path("/proc/meminfo")
    if not path.is_file():
        return 0, 0
    total = available = 0
    try:
        for line in path.read_text().splitlines():
            if line.startswith("MemTotal:"):
                total = int(line.split()[1]) * 1024
            elif line.startswith("MemAvailable:"):
                available = int(line.split()[1]) * 1024
    except Exception:
        return 0, 0
    return total, available


def _read_proc_rss() -> int | None:
    path = Path("/proc/self/status")
    if not path.is_file():
        return None
    try:
        for line in path.read_text().splitlines():
            if line.startswith("VmRSS:"):
                return int(line.split()[1]) * 1024
    except Exception:
        return None
    return None


def _read_proc_net_dev() -> tuple[int, int] | None:
    path = Path("/proc/net/dev")
    if not path.is_file():
        return None
    rx = tx = 0
    try:
        for line in path.read_text().splitlines():
            if ":" not in line:
                continue
            name, data = line.split(":", 1)
            if name.strip() in ("lo", "Inter-|", "face"):
                continue
            fields = data.split()
            if len(fields) >= 9:
                rx += int(fields[0])
                tx += int(fields[8])
    except Exception:
        return None
    return rx, tx


def _read_established_tcp() -> int | None:
    path = Path("/proc/net/tcp")
    if not path.is_file():
        return None
    count = 0
    try:
        for line in path.read_text().splitlines()[1:]:
            parts = line.split()
            if len(parts) > 3 and parts[3] == "01":
                count += 1
    except Exception:
        return None
    return count


def _collect_gpu() -> dict[str, Any]:
    """nvidia-smi 存在才采集，任何失败都静默跳过"""
    try:
        out = subprocess.run(
            ["nvidia-smi", "--query-gpu=utilization.gpu,memory.used,memory.total",
             "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=2, check=False,
        )
    except Exception:
        return {}
    lines = [line.strip() for line in out.stdout.splitlines() if line.strip()]
    if out.returncode != 0 or not lines:
        return {}
    util_sum = mem_used = mem_total = 0.0
    for line in lines:
        parts = [p.strip() for p in line.split(",")]
        if len(parts) >= 3:
            util_sum += float(parts[0])
            mem_used += float(parts[1])
            mem_total += float(parts[2])
    result: dict[str, Any] = {"gpuCount": len(lines), "gpuUtil": round(util_sum / len(lines), 2)}
    if mem_total > 0:
        result["gpuMemPct"] = round(mem_used * 100 / mem_total, 2)
    return result


class MetricsReporter:
    """独立线程定时采集上报，失败只记日志不影响业务。"""

    def __init__(self, server_addr: str, app_id: str, service_id: str,
                 client_token: str = "", interval: int = 60, gpu: bool = True) -> None:
        self.server_addr = server_addr.rstrip("/")
        self.app_id = app_id
        self.service_id = service_id
        self.client_token = client_token
        self.interval = max(10, interval)
        self.gpu = gpu
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._last_net: tuple[int, int] | None = None
        self._last_at = 0.0

    # ------------------------------------------------------------ 采集
    def collect(self) -> dict[str, Any]:
        data: dict[str, Any] = {
            "hostname": socket.gethostname(),
            "appId": self.app_id,
            "serviceName": self.service_id,
            "osName": os.uname().sysname if hasattr(os, "uname") else os.name,
            "osVersion": os.uname().release if hasattr(os, "uname") else "",
            "cpuCores": os.cpu_count() or 1,
            "reportTime": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        }

        # CPU
        try:
            if psutil is not None:
                data["cpuUsage"] = round(psutil.cpu_percent(interval=None), 2)
                if hasattr(psutil, "getloadavg"):
                    data["loadAvg"] = round(psutil.getloadavg()[0], 2)
            else:
                load = os.getloadavg()[0] if hasattr(os, "getloadavg") else -1
                if load >= 0:
                    data["loadAvg"] = round(load, 2)
                    data["cpuUsage"] = round(min(100.0, load * 100 / (os.cpu_count() or 1)), 2)
        except Exception:
            pass

        # 内存
        try:
            if psutil is not None:
                mem = psutil.virtual_memory()
                data["memTotal"] = int(mem.total)
                data["memUsedPct"] = round(mem.percent, 2)
                data["procRss"] = int(psutil.Process().memory_info().rss)
                data["fdCount"] = psutil.Process().num_fds() if hasattr(psutil.Process(), "num_fds") else None
            else:
                total, available = _read_proc_meminfo()
                if total:
                    data["memTotal"] = total
                    data["memUsedPct"] = round((total - available) * 100 / total, 2)
                rss = _read_proc_rss()
                if rss:
                    data["procRss"] = rss
        except Exception:
            pass

        # 线程数（Python 进程）
        try:
            data["threadCount"] = threading.active_count()
        except Exception:
            pass

        # 磁盘
        try:
            usage = shutil.disk_usage("/")
            data["diskTotal"] = int(usage.total)
            data["diskUsedPct"] = round(usage.used * 100 / usage.total, 2)
        except Exception:
            pass

        # 网络：累计值差 / 间隔
        counters = None
        try:
            if psutil is not None:
                net = psutil.net_io_counters()
                counters = (net.bytes_recv, net.bytes_sent)
            else:
                counters = _read_proc_net_dev()
        except Exception:
            counters = None
        now = time.time()
        if counters is not None:
            if self._last_net is not None and self._last_at > 0:
                seconds = max(1.0, now - self._last_at)
                data["netRxBps"] = int((counters[0] - self._last_net[0]) / seconds)
                data["netTxBps"] = int((counters[1] - self._last_net[1]) / seconds)
            self._last_net = counters
            self._last_at = now
        conn = _read_established_tcp()
        if conn is not None:
            data["netConn"] = conn

        # GPU（可选）
        if self.gpu:
            data.update(_collect_gpu())
        data.setdefault("gpuCount", 0)
        return data

    # ------------------------------------------------------------ 上报
    def report_once(self, timeout: float = 5.0) -> bool:
        payload = json.dumps([self.collect()], ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            f"{self.server_addr}/discovery/metrics/batch",
            data=payload,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-Gateway-Client-Token": self.client_token,
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return 200 <= response.status < 300
        except (urllib.error.URLError, TimeoutError, OSError):
            return False

    def start(self) -> None:
        if self._thread is not None:
            return

        def loop() -> None:
            # 首次先采一次基线，避免第一轮速率失真
            self.collect()
            while not self._stop.wait(self.interval):
                self.report_once()

        self._thread = threading.Thread(target=loop, name="jv-gateway-metrics", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2)
            self._thread = None


if __name__ == "__main__":  # pragma: no cover - 手工调试
    import sys

    dry = "--dry-run" in sys.argv
    reporter = MetricsReporter(
        server_addr=os.getenv("GATEWAY_SERVER_ADDR", "http://127.0.0.1:28090/api"),
        app_id=os.getenv("GATEWAY_APP_ID", "app-demo"),
        service_id=os.getenv("GATEWAY_SERVICE_ID", "demo-service"),
        client_token=os.getenv("GATEWAY_CLIENT_TOKEN", ""),
    )
    first = reporter.collect()
    time.sleep(1)
    second = reporter.collect()
    if dry:
        print(json.dumps(second, ensure_ascii=False, indent=2))
    else:
        print("reported:", reporter.report_once())
