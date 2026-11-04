# 网关平台 · 业务系统接入指南

平台提供两个可复用模块，业务系统（任意 Spring Boot 3 项目）引入即可完成**自动接入**与**调用日志采集**，
无需重复定义公共模型、请求封装与配置。

| 模块 | 说明 | 是否依赖 Spring |
| --- | --- | --- |
| `jv-gateway-client-common` | 公共模块：上报模型（TraceLogDTO）、服务实例模型（ServiceInstanceDTO）、常量、错误枚举 | 否，纯 POJO |
| `jv-gateway-client-starter` | 网关子模块：Spring Boot 自动装配，负责自动注册、请求/响应采集与异步上报 | 是 |

## 一、引入依赖

```xml
<dependency>
  <groupId>com.zimo.gateway</groupId>
  <artifactId>jv-gateway-client-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

只想要模型、不用自动装配的场景，可单独引入：

```xml
<dependency>
  <groupId>com.zimo.gateway</groupId>
  <artifactId>jv-gateway-client-common</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 1.1 没有私服：以 jar 包分发

业务方拿不到源码、或公司没有 Maven 私服时，把 SDK 打成 jar 分发，装进业务方本地仓库即可，
依赖写法与上面完全一致。

**生成分发包**（网关平台项目根目录）：

```bash
bash scripts/build-sdk-jar.sh
```

产出 `dist/sdk-jar/`：

```
lib/api-gateway-1.0.0.pom          ← parent pom，解析依赖版本必需
lib/jv-gateway-client-common.jar   + .pom
lib/jv-gateway-client-starter.jar  + .pom
install.sh                         ← 业务方一条命令装好
pom-snippet.xml
```

**业务机器上安装**：

```bash
cd dist/sdk-jar && ./install.sh
```

等价手工命令（顺序不能反）：

```bash
mvn install:install-file -Dfile=api-gateway-1.0.0.pom \
  -DgroupId=com.zimo.gateway -DartifactId=api-gateway -Dversion=1.0.0 -Dpackaging=pom

mvn install:install-file -Dfile=jv-gateway-client-common.jar \
  -DpomFile=jv-gateway-client-common.pom

mvn install:install-file -Dfile=jv-gateway-client-starter.jar \
  -DpomFile=jv-gateway-client-starter.pom
```

三个坑，都会导致启动失败：

| 现象 | 原因 |
| --- | --- |
| `Could not find artifact com.zimo.gateway:api-gateway:pom:1.0.0` | parent pom 没装，starter 的 pom 继承了它才能确定依赖版本 |
| `NoClassDefFoundError: ...client/common/dto/TraceLogDTO` | 只给了 `-Dfile` 没给 `-DpomFile`，生成的 pom 没有 dependencies，传递依赖丢失 |
| 启动报缺少 servlet 相关类 | starter 内 `spring-boot-starter-web` 是 `optional`，业务项目需自带（Web 项目本来就有） |

## 二、配置

```yaml
zimo:
  gateway:
    client:
      enabled: true                                  # 总开关
      server-addr: http://127.0.0.1:28080/api        # 平台统一入口地址（见下方说明）
      app-id: app-order                              # 应用编码
      app-name: 订单中心                              # 应用名称
      service-name: order-service                    # 服务名
      port: 9001                                     # 不填则取 server.port
      ip: 10.20.3.11                                 # 不填则自动探测（多网卡机器建议显式指定）
      language: Java 17
      version: v2.4.0
      trace:
        enabled: true                                # 是否采集
        max-body-size: 8192                          # 单条报文体最大采集长度，超出截断
        sample-rate: 1.0                             # 采样率 0~1
        batch-size: 50                               # 批量上报条数
        flush-interval: 3000                         # 定时上报间隔（ms）
        exclude-paths:                               # 不采集的路径（Ant 风格）
          - /actuator/**
          - /error
          - /favicon.ico
      registry:
        enabled: true                                # 是否自动注册（自动接入）
        heartbeat-interval: 30000                    # 心跳间隔（ms）
```

> ### 关于 server-addr（重要）
>
> SDK 在 `server-addr` 之后拼接**不同前缀**打到**不同微服务**：
>
> | 前缀 | 目标微服务 | 端口 |
> | --- | --- | --- |
> | `/api/trace/**` | jv-gateway-trace | 28084 |
> | `/api/discovery/**` | jv-gateway-discovery | 28083 |
> | `/api/users`、`/api/auth` | jv-gateway-user | 28081 |
> | `/api/overview` | jv-gateway-overview | 28082 |
>
> 所以 `server-addr` 必须填**统一入口**地址，而不是某个微服务的端口，
> 否则注册与上报会打到不提供该接口的服务上而返回 404。
>
> - **本地开发**：直接填前端 dev server 地址 `http://127.0.0.1:28080/api`，
>   其 `vite.config.js` 已按上述前缀配置了代理转发。
> - **生产环境**：用 Nginx / Spring Cloud Gateway 做统一入口，参考配置：
>
> ```nginx
> upstream gw_user      { server 127.0.0.1:28081; }
> upstream gw_overview  { server 127.0.0.1:28082; }
> upstream gw_discovery { server 127.0.0.1:28083; }
> upstream gw_trace     { server 127.0.0.1:28084; }
>
> server {
>   listen 28080;
>   location /api/auth/      { proxy_pass http://gw_user; }
>   location /api/users/     { proxy_pass http://gw_user; }
>   location /api/overview/  { proxy_pass http://gw_overview; }
>   location /api/discovery/ { proxy_pass http://gw_discovery; }
>   location /api/trace/     { proxy_pass http://gw_trace; }
> }
> ```

## 三、自动接入做了什么

```
应用启动  ──►  POST /api/discovery/services/register   注册实例（appId / ip / port / 版本 / 语言）
运行中    ──►  POST /api/discovery/services/heartbeat  每 30s 刷新 last_heartbeat_time
进程退出  ──►  DELETE /api/discovery/services/deregister 主动下线（状态置为「异常」）
每次请求  ──►  POST /api/trace/logs/batch              异步批量上报调用日志
```

对应控制台菜单：

- **接入中心 → 自动接入**：自动完成上述注册与心跳
- **可观测 → 日志追踪**：查看上报的调用日志

## 四、采集字段

| 分组 | 字段 |
| --- | --- |
| 归属 | `appId`、`appName`、`serviceName` |
| 请求 | `api`（method + uri）、`protocol`、`reqFormat`、`reqHeader`、`reqBody`、`reqBytes` |
| 响应 | `statusCode`、`resFormat`、`resHeader`、`resBody`、`resBytes` |
| 地址 | `reqIp`、`reqPort`、`resIp`、`resPort` |
| 质量 | `cost`（耗时 ms）、`errType`（错误类型）、`reqTime`、`resTime`、`traceId` |

错误类型自动推断：401/403 → 鉴权失败，404 → 路由失败，400/422 → 参数校验失败，
429 → 限流拒绝，504/超时异常 → 上游响应超时，其他 5xx/异常 → 上游响应异常。

## 五、平台侧接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/discovery/services/register` | 注册 / 更新服务实例 |
| POST | `/api/discovery/services/heartbeat` | 心跳 |
| DELETE | `/api/discovery/services/deregister` | 实例下线 |
| POST | `/api/trace/logs/batch` | 批量上报调用日志 |

## 六、编程式调用平台能力

平台本身也是微服务，其他项目注入 `GatewayPlatformClient` 即可调用：

```java
@Service
@RequiredArgsConstructor
public class DemoService {

    private final GatewayPlatformClient platformClient;

    public void demo() {
        Map<String, Object> summary = platformClient.overviewSummary();
        Map<String, Object> services = platformClient.pageServices("order", 1, 10);

        Map<String, Object> params = new HashMap<>();
        params.put("errType", "上游响应超时");
        params.put("current", 1);
        params.put("size", 20);
        Map<String, Object> logs = platformClient.pageTraceLogs(params);
    }
}
```

不引入 Feign / 注册中心等额外依赖，任何 Spring Boot 3 项目都能直接使用。

## 七、关闭与降级

```yaml
zimo:
  gateway:
    client:
      enabled: false        # 一键关闭（注册与采集全部停止）
      trace:
        enabled: false      # 仅关闭采集，保留自动注册
      registry:
        enabled: false      # 仅关闭自动注册，保留采集
```

高流量服务建议把 `sample-rate` 降到 0.1~0.3，避免日志表过大。

## 八、非 Java 语言接入

平台协议与语言无关，只要完成两件事即可「自动接入」：

1. `POST /api/discovery/services/register` 注册实例（定期 `POST .../heartbeat`，退出时 `DELETE .../deregister`）
2. `POST /api/trace/logs/batch` 批量上报调用日志

请求体字段见第五、六节对应的 `ServiceInstanceDTO` / `TraceLogDTO`。

**Python 已有现成 SDK**（零依赖，含 WSGI / ASGI 中间件），见
[`sdk/python/README.md`](../sdk/python/README.md)：

```python
from jv_gateway import GatewayClient
from jv_gateway.wsgi import WSGITraceMiddleware   # Flask/Django 用这个
# from jv_gateway.asgi import ASGITraceMiddleware  # FastAPI/Starlette 用这个

client = GatewayClient(app_id="app-order", app_name="订单中心",
                       service_name="order-service", port=8000)
client.start()
app.wsgi_app = WSGITraceMiddleware(app.wsgi_app, client)
```

Go / Node 等语言可参照同一协议自行实现，关键是 `server-addr` 要指向**平台统一入口**
（如 `http://127.0.0.1:28080/api`），而不是某个微服务端口。
