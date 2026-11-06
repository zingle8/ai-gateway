# 网关代理接入

零改代码接入方式：业务方把调用地址指向网关，由网关转发到上游，并记录每次调用的完整详情。

适用于任何语言、任何框架、外部系统，以及拿不到源码、无法加 SDK 的老系统。

## 一、和其他接入方式的区别

| | 网关代理 | SDK 埋点 |
| --- | --- | --- |
| 业务改代码 | 不需要 | 需要加依赖 |
| 语言限制 | 无 | Java / Python |
| 外部系统 | 支持 | 不支持 |
| 能看到转发详情 | 支持（上游地址、超时、重定向） | 只能看到自身调用 |
| 性能开销 | 多一跳代理 | 几乎为零 |

两种方式可共存，数据统一落在 `tb_gw_trace_log`，控制台不区分来源。

## 二、架构

```
业务方调用 ──► jv-api-gateway-proxy :28090 ──► 上游服务 10.20.3.11:8080
                    │
                    │ TraceGlobalFilter 记录
                    ▼
              tb_gw_trace_log ──► 控制台「日志追踪」
```

路由规则存在 `tb_gw_route`，网关每 30 秒热加载，改完不用重启。

## 三、启动

```bash
bash scripts/restart.sh backend      # 会一并启动 5 个服务（含 proxy 28090）
bash scripts/status.sh               # 查看端口占用
tail -f logs/jv-api-gateway-proxy.log    # 网关日志
```

单独调试：

```bash
cd backend && mvn -pl jv-api-gateway-proxy -am package
java -jar backend/jv-api-gateway-proxy/target/jv-api-gateway-proxy.jar
```

## 四、配置规则

控制台「接入中心 → 路由配置 → 新增路由」：

| 字段 | 说明 | 示例 |
| --- | --- | --- |
| 匹配路径 | 支持通配符，以 `/` 开头 | `/order/**` |
| 转发目标 | 只填协议 + 主机 + 端口 | `http://10.20.3.11:8080` |
| 追加前缀 | 上游有 context-path 时必填 | `/api` |
| 去掉首段 | 是否去掉匹配前缀 | `/order/create → /create` |

路径处理顺序：**先去首段，再追加前缀**。

```
/gw-demo/discovery/services
  → StripPrefix=1 → /discovery/services
  → PrefixPath=/api → /api/discovery/services
  → http://127.0.0.1:28083/api/discovery/services
```

> 转发目标里不要带路径。Spring Cloud Gateway 会用请求路径覆盖 URI 的 path 部分，
> 写成 `http://host/api` 会被丢弃，必须用「追加前缀」。

## 五、业务方接入

改一个 baseUrl 即可：

```diff
- order.api.base-url: http://10.20.3.11:8080/api
+ order.api.base-url: http://gateway-host:28090/order
```

鉴权头、Cookie、Content-Type 全部原样透传，不需要额外配置。

## 六、采集内容

每条记录包含：`api`（方法 + 路径）、`status_code`、`cost`、`res_ip/res_port`（上游地址）、
`req_header/res_header`、`req_body/res_body`、`req_bytes/res_bytes`、`trace_id`、`service_name`（存服务ID，英文唯一标识）。

约束：

- 报文体默认截断 8KB（`gateway.proxy.max-body-size` 可调）
- 只采集文本类报文；文件上传等二进制内容记长度不记内容
- 采集在内存队列异步批量写库，队列满丢弃，**不影响转发**
- 写库失败只记日志，不向业务方抛错

## 七、注意事项

1. **网关是流量入口**，生产环境至少双实例 + Nginx/Keepalived 前置，否则网关挂掉会全站不可用。
2. **超时**：默认连接 3s、读取 30s，大文件上传场景需调大 `spring.cloud.gateway.httpclient.response-timeout`。
3. **响应体捕获**只旁路复制不重写，不会出现报文重复。
4. 上游返回 200 但业务异常的情况，网关如实记录 200，错误率统计不到，需要在业务侧保证状态码正确。
