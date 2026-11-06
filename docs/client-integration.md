# 网关平台 · 业务系统接入指南

平台提供两个可复用模块，业务系统（任意 Spring Boot 3 项目）引入即可完成**自动接入**与**调用日志采集**，
无需重复定义公共模型、请求封装与配置。

| 模块 | 说明 | 是否依赖 Spring |
| --- | --- | --- |
| `jv-api-gateway-client-common` | 公共模块：上报模型（TraceLogDTO）、服务实例模型（ServiceInstanceDTO）、常量、错误枚举 | 否，纯 POJO |
| `jv-api-gateway-client-starter` | 网关子模块：Spring Boot 自动装配，负责自动注册、请求/响应采集与异步上报 | 是 |

## 一、引入依赖

```xml
<dependency>
  <groupId>com.zimo.gateway</groupId>
  <artifactId>jv-api-gateway-client-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

只想要模型、不用自动装配的场景，可单独引入：

```xml
<dependency>
  <groupId>com.zimo.gateway</groupId>
  <artifactId>jv-api-gateway-client-common</artifactId>
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
lib/jv-api-gateway-client-common.jar   + .pom
lib/jv-api-gateway-client-starter.jar  + .pom
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

mvn install:install-file -Dfile=jv-api-gateway-client-common.jar \
  -DpomFile=jv-api-gateway-client-common.pom

mvn install:install-file -Dfile=jv-api-gateway-client-starter.jar \
  -DpomFile=jv-api-gateway-client-starter.pom
```

三个坑，都会导致启动失败：

| 现象 | 原因 |
| --- | --- |
| `Could not find artifact com.zimo.gateway:api-gateway:pom:1.0.0` | parent pom 没装，starter 的 pom 继承了它才能确定依赖版本 |
| `NoClassDefFoundError: ...client/common/dto/TraceLogDTO` | 只给了 `-Dfile` 没给 `-DpomFile`，生成的 pom 没有 dependencies，传递依赖丢失 |
| 启动报缺少 servlet 相关类 | starter 内 `spring-boot-starter-web` 是 `optional`，业务项目需自带（Web 项目本来就有） |

### 1.2 多团队 / 多项目分发：Maven 私服（推荐）

> 没有现成私服？一键部署：`bash scripts/init-nexus.sh`（Docker，端口 28095），
> 完整步骤见 [Maven 私服部署指南](maven-nexus-deployment.md)。

`install.sh` 装的是「当前这台机器」的本地仓库（`~/.m2`）——每台机器、每个 CI 节点都要装一次。
业务方多、机器多、或有 CI/CD 时，应把 SDK 发布到公司 Maven 私服（Nexus / Artifactory），
之后所有项目像引开源库一样引坐标，无需再装任何东西。

**平台侧发布一次**（把 dist/sdk-jar 里的 5 个文件 deploy 上去，顺序不能反）：

```bash
cd dist/sdk-jar/lib

# 1) parent pom
mvn deploy:deploy-file -Dfile=api-gateway-1.0.0.pom \
  -DgroupId=com.zimo.gateway -DartifactId=api-gateway -Dversion=1.0.0 -Dpackaging=pom \
  -DrepositoryId=zimo-releases -Durl=http://<私服地址>/repository/maven-releases/

# 2) client-common（模型与常量层，starter 的传递依赖）
mvn deploy:deploy-file -Dfile=jv-api-gateway-client-common.jar \
  -DpomFile=jv-api-gateway-client-common.pom \
  -DrepositoryId=zimo-releases -Durl=http://<私服地址>/repository/maven-releases/

# 3) client-starter（Starter 本体）
mvn deploy:deploy-file -Dfile=jv-api-gateway-client-starter.jar \
  -DpomFile=jv-api-gateway-client-starter.pom \
  -DrepositoryId=zimo-releases -Durl=http://<私服地址>/repository/maven-releases/
```

> `repositoryId` 需在发布机的 `~/.m2/settings.xml` 里配置对应凭据（`<server>` 节点）。

**业务方**只需在 `pom.xml`（或 `settings.xml`）声明私服仓库，然后照常引坐标（与 1.1 方式一完全一致）：

```xml
<repositories>
  <repository>
    <id>zimo-releases</id>
    <url>http://<私服地址>/repository/maven-releases/</url>
  </repository>
</repositories>
```

- **Gradle 项目**同样可用：`repositories { maven { url 'http://<私服地址>/repository/maven-releases/' } }` 后正常 `implementation 'com.zimo.gateway:jv-api-gateway-client-starter:1.0.0'`；
- 版本升级：deploy 新版本号后业务方改版本即可，旧版本不受影响；
- 没有私服且不想每台机器装 → 把 `dist/sdk-jar` 提交到交付包/制品库随项目走，CI 里先跑 `install.sh`。

### 1.3 不走私服的分发替代方案

**方案 A：项目内 file:// 仓库（推荐，零服务器、零安装）**——把 SDK 按 Maven 仓库布局放进业务项目 git 仓库，clone 即用，CI 天然可用：

```bash
# 交付时生成（dist/sdk-jar 目录下执行，三条命令顺序不能反）
mvn deploy:deploy-file -Dfile=lib/api-gateway-1.0.0.pom \
  -DgroupId=com.zimo.gateway -DartifactId=api-gateway -Dversion=1.0.0 -Dpackaging=pom \
  -Durl=file:$PWD/libs-repo
mvn deploy:deploy-file -Dfile=lib/jv-api-gateway-client-common.jar \
  -DpomFile=lib/jv-api-gateway-client-common.pom -Durl=file:$PWD/libs-repo
mvn deploy:deploy-file -Dfile=lib/jv-api-gateway-client-starter.jar \
  -DpomFile=lib/jv-api-gateway-client-starter.pom -Durl=file:$PWD/libs-repo
```

```xml
<!-- 业务方 pom.xml：libs-repo 随 git 提交；单模块用 ${project.basedir}，
     多模块用 ${maven.multiModuleProjectDirectory}（Maven 3.3+，否则子模块解析不到） -->
<repositories>
  <repository>
    <id>sdk-in-project</id>
    <url>file://${maven.multiModuleProjectDirectory}/libs-repo</url>
  </repository>
</repositories>
```

Gradle 同样支持：`maven { url uri("${rootDir}/libs-repo") }`。升级版本 = 重跑三条 deploy 后提交，业务方 pull 即得。

**方案 B：免运维云制品库**——GitHub Packages / 阿里云云效制品仓库 / 腾讯云 CODING 制品库均兼容 Maven 协议，deploy 命令与 1.2 私服完全相同（URL 与认证 token 换成云平台提供的），适合没有自建 Nexus 但有云平台的团队。

**方案 C：CI 构建期自动安装**——沿用 install.sh，挂到流水线第一步（或绑定 `mvn initialize` phase）。缺点：每台机器每条流水线都要装一次，仅适合少量项目。

**不推荐 `<systemPath>` 直接引 jar**：system scope 不参与传递依赖（会丢 client-common 与 spring-boot-starter），需 `includeSystemScope` 补救且 Maven 已弃用。

选型：一两个业务项目 → A；项目多/跨部门 → B 或回头上私服。

## 二、配置

```yaml
zimo:
  gateway:
    client:
      enabled: true                                  # 总开关
      server-addr: http://127.0.0.1:28090/api        # 平台统一入口（网关 28090，见下方说明）
      client-token: ${GATEWAY_CLIENT_TOKEN:dev-gateway-token}
                                                     # 上报共享密钥（X-Gateway-Client-Token）：
                                                     # 注册/心跳/采集上报自动携带，网关侧校验，
                                                     # 防止内网伪造注册与上报；与 proxy 校验端同值
      app-id: app-order                              # 应用编码
      app-name: 订单中心                              # 应用名称
      service-id: order-service                      # 服务ID（英文唯一标识，注册幂等键）
      service-name: 订单服务                          # 服务名（中文显示名），不填则显示服务ID
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
      auth:                                          # 入口鉴权（可选，默认关）
        enabled: true                                # 开启后调用本服务的请求需持有效凭证
        exempt-paths:                                # 豁免路径（健康检查等，Ant 风格）
          - /health/**
          - /actuator/**
          - /error
        cache-seconds: 60                            # 验签结果缓存秒数
        timeout-ms: 3000                             # 单次验签超时
        fail-open: false                             # 网关不可达时 true 放行 / false 拒绝
```

> **关于 client-token 与入口鉴权（auth）**
>
> - `client-token`：SDK 出站上报请求携带 `X-Gateway-Client-Token` 头，
>   网关（`gateway.proxy.auth.client-token` 非空时）对上报路径二次校验，
>   不匹配拒绝——防止内网伪造注册/上报。两端必须同值，生产用环境变量注入。
> - `auth`：SDK 入口鉴权，验签回调网关开放接口 `POST /open/auth/token/verify`
>   （Bearer 用户 JWT 与 `X-Api-Key` gwak- 凭证同语义自动识别），有效期内同 token
>   走本地缓存。**SDK 自身的数据上报是出站调用，不受入口鉴权影响**——即使调用方
>   验签失败，调用日志与 span 照常上报，网关平台始终能看到这家接入方的真实流量。

> ### 关于 server-addr（重要）
>
> SDK 在 `server-addr` 之后拼接**不同前缀**打到**不同微服务**：
>
> | 前缀 | 目标微服务 | 端口 |
> | --- | --- | --- |
> | `/api/trace/**` | jv-api-gateway-trace | 28084 |
> | `/api/discovery/**` | jv-api-gateway-discovery | 28083 |
> | `/api/users`、`/api/auth` | jv-api-gateway-user | 28081 |
> | `/api/overview` | jv-api-gateway-overview | 28082 |
>
> 所以 `server-addr` 必须填**统一入口**地址，而不是某个微服务的端口，
> 否则注册与上报会打到不提供该接口的服务上而返回 404。
>
> - **本地 / 生产**：统一填网关地址 `http://<网关IP>:28090/api`，
>   由 jv-api-gateway-proxy 按 `tb_gw_route` 转发到对应微服务。
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
每次请求  ──►  POST /api/trace/spans/batch             异步批量上报链路 span（调用链 / 服务拓扑数据源）
```

对应控制台菜单：

- **接入中心 → 自动接入**：自动完成上述注册与心跳
- **可观测 → 日志追踪**：查看上报的调用日志

## 四、采集字段

> 归属字段口径：SDK 采集上报时同时带 `serviceId`（英文服务ID）与 `serviceName`（中文服务名），
> 服务端 `tb_gw_trace_log` / `tb_gw_trace_span` 的 `service_name` 列**统一写入服务ID**，
> 统计、拓扑、趋势因此按 ID 聚合，中文名由控制台按注册表映射展示。


| 分组 | 字段 |
| --- | --- |
| 归属 | `appId`、`appName`、`serviceId`（服务ID，英文唯一标识）、`serviceName`（服务名，中文显示名）——**服务端落库统一存服务ID**：`serviceId` 优先、缺省回退 `serviceName`；中文名只在注册表 `tb_gw_service` 维护，页面展示时按 ID 映射 |
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
| POST | `/api/trace/spans/batch` | 批量上报链路 span（SERVER / CLIENT，供瀑布图与拓扑） |

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
3. `POST /api/trace/spans/batch` 批量上报链路 span（可选；Java SDK 自动完成，不报 span 的客户端没有调用链瀑布与拓扑边）

请求体字段见第五、六节对应的 `ServiceInstanceDTO` / `TraceLogDTO`。

**Python 已有现成 SDK**（零依赖，含 WSGI / ASGI 中间件），见
[`sdk/python/README.md`](../sdk/python/README.md)：

```python
from jv_gateway import GatewayClient
from jv_gateway.wsgi import WSGITraceMiddleware   # Flask/Django 用这个
# from jv_gateway.asgi import ASGITraceMiddleware  # FastAPI/Starlette 用这个

client = GatewayClient(app_id="app-order", app_name="订单中心",
                       service_id="order-service", service_name="订单服务", port=8000)
client.start()
app.wsgi_app = WSGITraceMiddleware(app.wsgi_app, client)
```

Go / Node 等语言可参照同一协议自行实现，关键是 `server-addr` 要指向**平台统一入口**
（如 `http://127.0.0.1:28090/api`），而不是某个微服务端口。

## 九、Python SDK（jv_gateway）

零第三方依赖（标准库 urllib/threading/queue），Python 3.8+，位于仓库 `sdk/python/`：

```
sdk/python/
├── jv_gateway/            # SDK 包
│   ├── client.py          # GatewayClient：注册/心跳/下线/手动上报；verify_token
│   ├── reporter.py        # TraceReporter：队列 + 后台线程批量上报（满即丢，3 秒 flush）
│   ├── wsgi.py            # WSGITraceMiddleware（Flask/Django）+ WSGIAuthMiddleware
│   ├── asgi.py            # ASGITraceMiddleware（FastAPI/Starlette）+ ASGIAuthMiddleware
│   ├── auth.py            # GatewayAuth：入口鉴权（回调开放验签接口 + 缓存）
│   └── _http.py           # urllib 封装：自动携带 X-Gateway-Client-Token
├── examples/fastapi_demo.py
├── scripts/smoke_report.py
└── README.md
```

引用与用法、入口鉴权、验证方式见 `sdk/python/README.md`；上报协议与 Java SDK 完全一致。

## 十、Node.js SDK（jv-gateway）

零第三方依赖（node:http / node:crypto），Node.js 16+，位于仓库 `sdk/nodejs/`：

```
sdk/nodejs/
├── index.js                 # 包入口
├── src/
│   ├── client.js            # GatewayClient：注册/心跳/下线/手动上报；verifyToken
│   ├── reporter.js          # TraceReporter：队列 + 定时批量上报（满即丢，3 秒 flush）
│   ├── auth.js              # GatewayAuth：入口鉴权（回调开放验签接口 + 缓存）
│   ├── middleware.js        # traceExpress / traceWrap（原生 http）自动采集
│   └── _http.js             # node:http 封装：自动携带 X-Gateway-Client-Token
├── examples/express_demo.js
├── scripts/smoke_report.js
└── README.md
```

```js
const { GatewayClient, traceExpress, GatewayAuth } = require('./jv-gateway/index.js')

const client = new GatewayClient({
  serverAddr: 'http://127.0.0.1:28090/api',
  appId: 'app-order', appName: '订单中心',
  serviceId: 'order-service', serviceName: '订单服务', port: 8000,
  clientToken: 'dev-gateway-token',      // 与网关 gateway.proxy.auth.client-token 同值
})
client.start()                            // 注册 + 心跳 + 上报线程
app.use(traceExpress(client))             // Express 自动采集（放路由之前）
app.use(new GatewayAuth(client).express())// 入口鉴权（可选，防止绕网关直连）
process.on('SIGTERM', () => client.stop())// 刷出剩余日志并主动下线
```

脚本 / 定时任务：`client.report({ method, path, statusCode, costMs, ... })`；
原生 http：`traceWrap(client, http.createServer(handler))`。
协议、字段、共享密钥、入口鉴权语义与 Java / Python SDK 完全一致。

## 十一、验证清单

| 验证 | 命令 / 操作 | 预期 |
| --- | --- | --- |
| 注册与心跳 | 启动业务应用 → 服务发现页 | 实例「健康」 |
| 上报通道密钥 | 无/错 `X-Gateway-Client-Token` 调 `/api/discovery/services/register` | 401「上报通道共享密钥无效」 |
| 入口鉴权拦截 | 直连业务系统端口调用（无凭证 / 伪造身份头） | 401「未提供访问凭证」 |
| 入口鉴权放行 | 带 `Authorization: Bearer <JWT>` 或 `X-Api-Key` | 正常响应 |
| 豁免路径 | 调用 exempt-paths 内路径（如健康检查） | 无需凭证 |
| 数据上报不受限 | 验签失败的调用方持续请求 | 日志与 span 照常落库（控制台可见） |
| Python 冒烟 | `python3 sdk/python/scripts/smoke_report.py` | ①~⑤ 全成功，日志/span 落库 |
| Node.js 冒烟 | `node sdk/nodejs/scripts/smoke_report.js` | ①~⑤ 全成功，日志/span 落库 |
