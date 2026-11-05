<template>
  <div class="page">
    <!-- 一、快速开始 -->
    <div class="gw-card">
      <div class="gw-card-hd">
        自动接入
        <span class="gw-muted gw-sm">引入 SDK 后启动即注册，并持续上报调用日志，无需在控制台点任何按钮</span>
      </div>
      <div class="gw-card-bd">
        <el-steps :active="3" finish-status="success" align-center class="onboard-steps">
          <el-step title="引入 SDK" description="Java 加依赖 / Python 放 SDK 目录" />
          <el-step title="填写配置" description="app-id、service-name、port" />
          <el-step title="启动应用" description="自动注册 + 心跳 + 日志上报" />
        </el-steps>

        <el-tabs v-model="activeLang" class="doc-tabs">
          <el-tab-pane label="Java（Spring Boot）" name="java">
            <el-radio-group v-model="javaMode" size="small" style="margin-bottom: 14px">
              <el-radio-button label="maven">方式一：Maven 依赖</el-radio-button>
              <el-radio-button label="jar">方式二：直接引用 jar 包</el-radio-button>
            </el-radio-group>

            <template v-if="javaMode === 'maven'">
              <div class="doc-step">1. 引入 Maven 依赖</div>
              <pre class="code-block">{{ javaDependency }}</pre>

              <div class="doc-step">2. application.yml 配置</div>
              <pre class="code-block">{{ javaConfig }}</pre>

              <div class="doc-step">3. 启动应用</div>
              <p class="doc-text">
                Starter 通过 <code>ApplicationRunner</code> 在启动完成后注册，并起一个后台线程每 30 秒心跳；
                请求经 Servlet Filter 采集，内存队列 + 定时批量上报。<strong>无需写任何代码</strong>。
              </p>
            </template>

            <template v-else>
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="业务方拿不到源码、或不想接私服时使用：把 SDK 打成 jar 分发，业务项目装进本地仓库后照常声明依赖。"
                style="margin-bottom: 14px"
              />

              <div class="doc-step">1. 生成分发包（在网关平台项目根目录执行）</div>
              <pre class="code-block">{{ jarBuildSnippet }}</pre>

              <div class="doc-step">2. 拷到业务机器，执行安装脚本</div>
              <pre class="code-block">{{ jarInstallSnippet }}</pre>

              <div class="doc-step">3. pom 中声明依赖（与方式一完全一致）</div>
              <pre class="code-block">{{ jarDependencySnippet }}</pre>

              <div class="doc-step">4. application.yml 配置</div>
              <p class="doc-text">配置项与方式一相同，见「配置项说明」。</p>

              <el-alert
                type="warning"
                :closable="false"
                show-icon
                title="三个文件缺一不可：parent pom 缺失会导致依赖版本解析失败，模块 pom 不带上会丢失传递依赖，只装 jar 会缺 jv-api-gateway-client-common。"
                style="margin-top: 14px"
              />
            </template>
          </el-tab-pane>

          <el-tab-pane label="Python" name="python">
            <div class="doc-step">1. 引入 SDK（零第三方依赖，Python 3.8+）</div>
            <pre class="code-block">{{ pythonInstall }}</pre>

            <div class="doc-step">2. Web 框架接入（Flask / FastAPI / Django）</div>
            <pre class="code-block">{{ pythonWeb }}</pre>

            <div class="doc-step">3. 无 Web 框架（脚本 / 定时任务）</div>
            <pre class="code-block">{{ pythonJob }}</pre>

            <el-alert
              type="success"
              :closable="false"
              show-icon
              title="采集方式与 Java 一致：WSGI 用中间件、ASGI 用中间件，字段、错误类型、上报协议完全相同。"
            />
          </el-tab-pane>

          <el-tab-pane label="Node.js" name="nodejs">
            <div class="doc-step">1. 引入 SDK（零第三方依赖，Node.js 16+，位于仓库 sdk/nodejs/）</div>
            <pre class="code-block">{{ nodeInstall }}</pre>

            <div class="doc-step">2. Web 框架接入（Express / 原生 http）</div>
            <pre class="code-block">{{ nodeWeb }}</pre>

            <div class="doc-step">3. 无 Web 框架（脚本 / 定时任务）</div>
            <pre class="code-block">{{ nodeJob }}</pre>

            <el-alert
              type="success"
              :closable="false"
              show-icon
              title="采集方式与 Java/Python 一致：Express 用中间件、原生 http 用包装函数，字段、错误类型、上报协议完全相同。"
            />
          </el-tab-pane>

          <el-tab-pane label="其他语言（自实现）" name="other">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="协议与语言无关，完成注册 + 上报两件事即可接入，Go / Node / C# 等语言照此实现。"
              style="margin-bottom: 14px"
            />

            <div class="doc-step">① 注册实例（启动时调用，之后每 30 秒心跳一次）</div>
            <pre class="code-block">{{ registerSnippet }}</pre>

            <div class="doc-step">② 上报调用日志（批量，建议 3 秒或满 50 条发一次）</div>
            <pre class="code-block">{{ traceSnippet }}</pre>

            <div class="doc-step">③ 进程退出时注销</div>
            <pre class="code-block">{{ deregisterSnippet }}</pre>
          </el-tab-pane>

          <el-tab-pane label="网关代理（零改代码）" name="gateway">
            <el-alert
              type="success"
              :closable="false"
              show-icon
              title="业务代码一行不改：把调用地址指向网关，由网关转发到上游并记录每次请求。适合外部系统、非 Java/Python 服务、以及拿不到源码的老系统。"
              style="margin-bottom: 14px"
            />

            <div class="doc-step">① 在平台配置转发规则</div>
            <p class="doc-text">
              进入「接入中心 → 路由配置」，点「新增路由」，填三样东西：
            </p>
            <el-table :data="gatewayRouteExample" size="small" border style="margin-bottom: 14px">
              <el-table-column prop="key" label="字段" width="130" />
              <el-table-column prop="value" label="示例值" width="230" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <div class="doc-step">② 业务方把调用地址换成网关地址</div>
            <pre class="code-block">{{ gatewayCallSnippet }}</pre>

            <div class="doc-step">③ 回到「日志追踪」查看每次调用</div>
            <p class="doc-text">
              每条记录包含：请求方法与路径、状态码、耗时、<strong>转发到的上游地址与端口</strong>、
              请求头/响应头、请求体/响应体（超过 8KB 截断）、TraceId。异常请求也能看到上游返回的原始报文。
            </p>

            <el-alert
              type="warning"
              :closable="false"
              show-icon
              title="三个注意点：① 上游有 context-path 必须在「追加前缀」填写；② 鉴权头会原样透传，不用额外配置；③ 网关是流量入口，生产环境建议至少双实例。"
              style="margin-top: 14px"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 二、配置项说明 -->
    <div class="gw-card" style="margin-top: var(--gw-gap)">
      <div class="gw-card-hd">
        配置项说明
        <span class="gw-muted gw-sm">都有默认值，通常只需要填 app-id / service-name / port</span>
      </div>
      <div class="gw-card-bd">
        <el-table :data="configRows" size="small" border stripe>
          <el-table-column prop="java" label="Java 属性" width="220">
            <template #default="{ row }"><code class="doc-code">{{ row.java }}</code></template>
          </el-table-column>
          <el-table-column prop="py" label="Python 参数" width="180">
            <template #default="{ row }"><code class="doc-code">{{ row.py }}</code></template>
          </el-table-column>
          <el-table-column prop="def" label="默认值" width="230" />
          <el-table-column prop="desc" label="说明" min-width="240" />
        </el-table>
      </div>
    </div>

    <!-- 三、工作原理 -->
    <div class="gw-card" style="margin-top: var(--gw-gap)">
      <div class="gw-card-hd">工作原理</div>
      <div class="gw-card-bd">
        <el-row :gutter="16">
          <el-col :span="8">
            <div class="doc-block">
              <div class="doc-block-title">① 注册</div>
              <p class="doc-text">
                启动时 <code>POST /discovery/services/register</code>，带上 appId / serviceName / ip / port / language / version；
                同服务名重复注册是<strong>幂等更新</strong>，扩缩容、重启都不会产生重复记录。
              </p>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="doc-block">
              <div class="doc-block-title">② 采集</div>
              <p class="doc-text">
                在请求前后各取一次时间戳，组装成日志模型；超过 8KB 的报文体自动截断，
                支持采样率与排除路径（如 <code>/health</code>、<code>/actuator</code>）。
              </p>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="doc-block">
              <div class="doc-block-title">③ 上报</div>
              <p class="doc-text">
                日志进内存队列后立即返回，后台线程批量 <code>POST /trace/logs/batch</code>，
                同时上报链路 span（<code>/trace/spans/batch</code>，供调用链瀑布图与拓扑页使用）。
                队列满直接丢弃，<strong>绝不阻塞业务线程</strong>。
              </p>
            </div>
          </el-col>
        </el-row>

        <el-divider content-position="left">采集字段</el-divider>
        <el-table :data="fieldRows" size="small" border stripe max-height="260">
          <el-table-column prop="name" label="字段" width="150">
            <template #default="{ row }"><code class="doc-code">{{ row.name }}</code></template>
          </el-table-column>
          <el-table-column prop="desc" label="说明" min-width="320" />
        </el-table>

        <el-divider content-position="left">错误类型（errType）判定</el-divider>
        <el-table :data="errRows" size="small" border stripe>
          <el-table-column prop="status" label="响应状态码" width="180" />
          <el-table-column prop="type" label="错误类型" width="160" />
          <el-table-column prop="desc" label="含义" min-width="260" />
        </el-table>
      </div>
    </div>

    <!-- 四、常见问题 -->
    <div class="gw-card" style="margin-top: var(--gw-gap)">
      <div class="gw-card-hd">常见问题</div>
      <div class="gw-card-bd">
        <el-collapse v-model="faqOpen">
          <el-collapse-item v-for="(f, i) in faqs" :key="i" :title="f.q" :name="String(i)">
            <p class="doc-text">{{ f.a }}</p>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <!-- 五、已接入实例 -->
    <div class="gw-card" style="margin-top: var(--gw-gap)">
      <div class="gw-card-hd">
        已接入实例
        <el-button size="small" text :loading="loading" @click="load">刷新</el-button>
      </div>
      <div class="gw-card-bd">
        <el-table :data="services" size="small" border stripe v-loading="loading">
          <el-table-column prop="appId" label="应用ID" width="120" />
          <el-table-column prop="appName" label="应用名" width="120" />
          <el-table-column prop="serviceName" label="服务名" min-width="160" />
          <el-table-column prop="port" label="端口" width="90" />
          <el-table-column prop="language" label="语言" width="110" />
          <el-table-column prop="version" label="版本" width="100" />
          <el-table-column label="健康状态" width="100" align="center">
            <template #default="{ row }">
              <span class="status-dot" :class="dotClass(row.status)"></span>{{ row.status }}
            </template>
          </el-table-column>
          <el-table-column label="最近心跳" width="170">
            <template #default="{ row }">{{ fmtTime(row.lastHeartbeatTime) }}</template>
          </el-table-column>
          <el-table-column prop="instances" label="实例数" width="90" />
          <template #empty>
            <el-empty description="暂无自动接入的实例" />
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { dotClass, fmtTime } from '@zimo/shared'
import { fetchServices } from '../api'

const services = ref([])
const loading = ref(false)
const activeLang = ref('java')

/* --------------------------------------------------- 网关代理接入（零改代码） */

const gatewayRouteExample = [
  { key: '匹配路径', value: '/order/**', desc: '以 /order 开头的请求全部转发' },
  { key: '转发目标', value: 'http://10.20.3.11:8080', desc: '只填协议 + 主机 + 端口，不要带路径' },
  { key: '追加前缀', value: '/api', desc: '上游有 context-path 时填，没有就留空' },
  { key: '去掉首段', value: '开启', desc: '/order/create 转发为 /create' }
]

const gatewayCallSnippet = `# 改之前：直连上游
curl http://10.20.3.11:8080/api/create

# 改之后：走网关（默认端口 28090）
curl http://gateway-host:28090/order/create

# 业务方只需要改 baseUrl，代码不动
# application.yml:
#   order.api.base-url: http://gateway-host:28090/order`
const javaMode = ref('maven')
const faqOpen = ref(['0'])

/* ---------------------------------------------------------------- 代码片段 */

const javaDependency = `<dependency>
  <groupId>com.zimo.gateway</groupId>
  <artifactId>jv-api-gateway-client-starter</artifactId>
  <version>1.0.0</version>
</dependency>`

const javaConfig = `zimo:
  gateway:
    client:
      server-addr: http://127.0.0.1:28090/api   # 平台统一入口，不是微服务端口
      app-id: app-order
      app-name: 订单中心
      service-name: order-service
      port: 8000
      # 上报共享密钥（X-Gateway-Client-Token）：注册/心跳/采集上报自动携带，
      # 网关侧校验（gateway.proxy.auth.client-token），防止内网伪造注册与上报
      client-token: \${GATEWAY_CLIENT_TOKEN:dev-gateway-token}
      trace:
        enabled: true
        sample-rate: 1.0
      registry:
        enabled: true
        heartbeat-interval: 30000
      # 入口鉴权（可选，默认关）：开启后调用本服务的请求需持有效凭证，
      # SDK 回调网关 /open/auth/token/verify 验签（Bearer JWT 与 X-Api-Key 同语义），
      # 防止绕过网关直连业务系统端口；SDK 自身数据上报是出站调用，不受影响
      auth:
        enabled: true
        exempt-paths:                            # 豁免路径（健康检查等）
          - /health/**
          - /actuator/**
          - /error
        cache-seconds: 60                        # 验签结果缓存
        fail-open: false                         # 网关不可达时拒绝（安全优先）`

const pythonInstall = `# 无需安装，把 sdk/python 加入 PYTHONPATH 即可（零第三方依赖）
export PYTHONPATH=/path/to/ai-gateway/sdk/python:$PYTHONPATH`

/* --------------------------------------------------- jar 包分发（方式二） */

const jarBuildSnippet = `# 在网关平台项目根目录执行
bash scripts/build-sdk-jar.sh

# 产出 dist/sdk-jar/
#   install.sh                        业务方一条命令装好
#   pom-snippet.xml
#   lib/api-gateway-1.0.0.pom         parent pom（解析依赖版本必需）
#   lib/jv-api-gateway-client-common.jar  + .pom
#   lib/jv-api-gateway-client-starter.jar + .pom`

const jarInstallSnippet = `# 把 dist/sdk-jar 整个拷到业务机器，然后：
cd dist/sdk-jar && ./install.sh

# 等价的手工命令（顺序不能反：parent -> common -> starter）
mvn install:install-file -Dfile=api-gateway-1.0.0.pom \\
  -DgroupId=com.zimo.gateway -DartifactId=api-gateway -Dversion=1.0.0 -Dpackaging=pom

mvn install:install-file -Dfile=jv-api-gateway-client-common.jar \\
  -DpomFile=jv-api-gateway-client-common.pom

mvn install:install-file -Dfile=jv-api-gateway-client-starter.jar \\
  -DpomFile=jv-api-gateway-client-starter.pom`

const jarDependencySnippet = `<!-- 装好之后，与方式一写法完全一致 -->
<dependency>
  <groupId>com.zimo.gateway</groupId>
  <artifactId>jv-api-gateway-client-starter</artifactId>
  <version>1.0.0</version>
</dependency>

<!-- 注意：starter 里的 spring-boot-starter-web 是 optional，
     Web 项目本身已有则无需处理；纯后台任务需自行引入 -->`

const pythonWeb = `from jv_gateway import GatewayClient
from jv_gateway.wsgi import WSGITraceMiddleware      # FastAPI 改用 jv_gateway.asgi

client = GatewayClient(
    server_addr="http://127.0.0.1:28090/api",
    app_id="app-order", app_name="订单中心",
    service_name="order-service", port=8000,
    client_token="dev-gateway-token",        # 与网关 gateway.proxy.auth.client-token 同值
)
client.start()                                        # 注册 + 心跳 + 上报线程

# Flask / Django：包一层中间件即可自动采集
app.wsgi_app = WSGITraceMiddleware(app.wsgi_app, client)

# FastAPI：app.add_middleware(ASGITraceMiddleware, client=client)

# 入口鉴权（可选，防止绕过网关直连；上报不受影响）
from jv_gateway.auth import GatewayAuth
from jv_gateway.asgi import ASGIAuthMiddleware
app.add_middleware(ASGIAuthMiddleware, auth=GatewayAuth(client))`

const pythonJob = `client = GatewayClient(app_id="app-job", service_name="recon-job", port=0,
                       client_token="dev-gateway-token")
client.start()

# 业务执行完手动上报
client.report(method="POST", path="/job/reconcile", status_code=200, cost_ms=52)

client.stop()      # 刷出剩余日志并下线`

const nodeInstall = `// 零第三方依赖（Node.js 16+），把仓库 sdk/nodejs/ 拷入业务项目后：
const { GatewayClient, traceExpress, GatewayAuth } = require('./jv-gateway/index.js')`

const nodeWeb = `const client = new GatewayClient({
  serverAddr: 'http://127.0.0.1:28090/api',
  appId: 'app-order', appName: '订单中心',
  serviceName: 'order-service', port: 8000,
  clientToken: 'dev-gateway-token',        // 与网关 gateway.proxy.auth.client-token 同值
})
client.start()                             // 注册 + 心跳 + 上报线程

// Express：自动采集（放路由之前）
app.use(traceExpress(client))

// 原生 http：const server = traceWrap(client, http.createServer(handler))

// 入口鉴权（可选，防止绕过网关直连；上报不受影响）
app.use(new GatewayAuth(client).express())

process.on('SIGTERM', () => client.stop()) // 刷出剩余日志并下线`

const nodeJob = `const client = new GatewayClient({
  appId: 'app-job', serviceName: 'recon-job', port: 0,
  clientToken: 'dev-gateway-token',
})
client.start()

// 业务执行完手动上报
client.report({ method: 'POST', path: '/job/reconcile', statusCode: 200, costMs: 52 })

await client.stop()   // 刷出剩余日志并下线`

const registerSnippet = `POST {server-addr}/discovery/services/register
Content-Type: application/json

{
  "appId": "app-order",
  "appName": "订单中心",
  "serviceName": "order-service",
  "ip": "10.20.3.11",
  "port": 8080,
  "language": "Go 1.22",
  "version": "v1.0.0",
  "registerTime": "2026-11-03T10:00:00"
}

# 心跳：POST /discovery/services/heartbeat（同样请求体，建议 30 秒一次）`

const traceSnippet = `POST {server-addr}/trace/logs/batch
Content-Type: application/json

[{
  "appId": "app-order",
  "appName": "订单中心",
  "serviceName": "order-service",
  "api": "POST /api/v1/order/create",
  "traceId": "4f2a9c1e8b7d4a3f",
  "protocol": "HTTP/1.1",
  "reqFormat": "JSON",
  "resFormat": "JSON",
  "reqIp": "10.20.1.5",
  "reqPort": 51234,
  "resIp": "10.20.3.11",
  "resPort": 8080,
  "statusCode": 200,
  "errType": "无",
  "cost": 38,
  "reqTime": "2026-11-03T10:00:00",
  "resTime": "2026-11-03T10:00:00",
  "reqBody": "{\\"orderNo\\":\\"SO001\\"}",
  "resBody": "{\\"code\\":200}",
  "reqBytes": 21,
  "resBytes": 13
}]`

const deregisterSnippet = `DELETE {server-addr}/discovery/services/deregister
Content-Type: application/json

{ "appId": "app-order", "serviceName": "order-service" }`

/* ---------------------------------------------------------------- 表格数据 */

const configRows = [
  { java: 'server-addr', py: 'server_addr', def: 'http://127.0.0.1:28090/api', desc: '平台统一入口，SDK 自动拼接 /discovery、/trace 前缀' },
  { java: 'app-id', py: 'app_id', def: 'app-demo', desc: '应用编码，控制台按此筛选' },
  { java: 'app-name', py: 'app_name', def: '示例应用', desc: '应用名称，展示用' },
  { java: 'service-name', py: 'service_name', def: 'demo-service', desc: '服务名，注册的唯一键（同名重复注册即更新）' },
  { java: 'port', py: 'port', def: '8080 / $PORT', desc: '服务端口；纯脚本类可填 0' },
  { java: 'ip', py: 'ip', def: '自动探测', desc: '注册用的本机 IP；多网卡机器（Docker / VPN / 虚拟机）建议显式指定，否则可能选中虚拟网卡' },
  { java: 'language', py: 'language', def: 'Java 17 / Python 3.x', desc: '语言类型，控制台展示' },
  { java: 'version', py: 'version', def: 'v1.0.0', desc: '版本号' },
  { java: 'enabled', py: '—', def: 'true', desc: '总开关，关闭后不再注册与采集' },
  { java: 'registry.enabled', py: 'registry_enabled', def: 'true', desc: '是否自动注册 + 心跳' },
  { java: 'registry.heartbeat-interval', py: 'heartbeat_interval', def: '30000（ms）', desc: '心跳间隔，最小 5 秒' },
  { java: 'trace.enabled', py: 'trace_enabled', def: 'true', desc: '是否采集上报' },
  { java: 'trace.sample-rate', py: 'sample_rate', def: '1.0', desc: '采样率 0~1，高流量建议 0.1~0.3' },
  { java: 'trace.max-body-size', py: 'max_body_size', def: '8192（字节）', desc: '报文体采集上限，超出截断' },
  { java: 'trace.batch-size', py: 'batch_size', def: '50', desc: '单批上报条数' },
  { java: 'trace.flush-interval', py: 'flush_interval', def: '3000（ms）', desc: '定时上报间隔' },
  { java: 'trace.exclude-paths', py: 'exclude_paths', def: '/actuator、/health 等', desc: '不采集的路径前缀' }
]

const fieldRows = [
  { name: 'api', desc: '接口标识，形如 POST /api/v1/order/create' },
  { name: 'traceId', desc: '链路 ID；请求头带 X-Gateway-Trace-Id 时复用，否则自动生成' },
  { name: 'protocol', desc: 'HTTP/1.1、HTTP/2、gRPC、WebSocket' },
  { name: 'reqFormat / resFormat', desc: '报文格式：JSON、XML、form、form-data、TEXT 等' },
  { name: 'statusCode', desc: '响应状态码' },
  { name: 'errType', desc: '错误类型（中文），由状态码推断，控制台按此筛选' },
  { name: 'cost', desc: '耗时（毫秒）' },
  { name: 'reqIp / reqPort / resIp / resPort', desc: '调用方与后端实例的地址信息' },
  { name: 'reqTime / resTime', desc: '请求进、响应出的时间' },
  { name: 'reqBody / resBody', desc: '报文体原文，超过 max-body-size 截断' },
  { name: 'reqBytes / resBytes', desc: '原始报文字节数（截断前真实大小）' }
]

const errRows = [
  { status: '401 / 403', type: '鉴权失败', desc: '令牌缺失、过期或无权限' },
  { status: '400 / 422', type: '参数校验失败', desc: '请求参数不合法' },
  { status: '404', type: '路由失败', desc: '没有匹配的路由或后端服务' },
  { status: '429', type: '限流拒绝', desc: '超出 QPS 配额被限流' },
  { status: '504', type: '上游响应超时', desc: '后端服务响应超时' },
  { status: '5xx（其余）', type: '上游响应异常', desc: '后端服务内部错误' },
  { status: '2xx / 3xx', type: '无', desc: '正常调用' }
]

const faqs = [
  {
    q: '控制台「服务发现」里看不到我的服务？',
    a: '先确认 server-addr 指向的是平台统一入口（如 http://127.0.0.1:28090/api），而不是某个微服务的端口。SDK 会在其后拼接 /discovery/** 和 /trace/**，分别打到不同微服务，填成单服务端口会导致一半请求 404。其次检查 registry.enabled 是否为 true、服务名是否重复覆盖了别的实例。'
  },
  {
    q: '「日志追踪」里没有数据？',
    a: '依次检查：trace.enabled 是否为 true；sample-rate 是否为 0；请求路径是否命中 exclude-paths；Python 是否把中间件真正挂到了应用上（Flask 需要赋给 app.wsgi_app，FastAPI 需要 add_middleware）。日志是批量上报的，默认最多延迟 3 秒，可把 flush-interval 调小来加速验证。'
  },
  {
    q: '上报失败会影响我的业务吗？',
    a: '不会。采集属于旁路能力：日志进内存队列后立即返回，队列满直接丢弃；上报异常只记 debug 日志，不重试、不抛出。注册失败也只告警，不影响应用启动。'
  },
  {
    q: '一个应用多实例部署，会不会注册出多条记录？',
    a: '不会。注册以 service-name 为唯一键做幂等更新，多实例共享一条服务记录，实例数与健康状态由心跳维护。'
  },
  {
    q: '可以只上报日志、不自动注册吗？',
    a: '可以。Java 设 registry.enabled=false，Python 传 registry_enabled=False，反之亦可只注册不采集。'
  },
  {
    q: '报文体太大占空间怎么办？',
    a: '调小 max-body-size（默认 8KB，超出截断），或降低 sample-rate 只采部分请求，也可以把健康检查一类路径加进 exclude-paths。'
  },
  {
    q: '引用 jar 包后启动报找不到 api-gateway:pom:1.0.0？',
    a: 'parent pom 没装。jv-api-gateway-client-starter 的 pom 继承了 com.zimo.gateway:api-gateway:1.0.0，Maven 解析它的依赖版本时必须有这个 parent。分发包里的 api-gateway-1.0.0.pom 要单独用 -Dpackaging=pom 装一次，且必须在 starter 之前装。'
  },
  {
    q: '装完 jar 启动报 NoClassDefFoundError（如 TraceLogDTO 找不到）？',
    a: '多半是只装了 jar、没带 -DpomFile。install-file 不带 pom 时会生成一个只有坐标、没有 dependencies 的空 pom，传递依赖（jv-api-gateway-client-common）就丢了。请改用 -DpomFile=jv-api-gateway-client-starter.pom 安装，或直接执行分发包里的 install.sh。'
  },
  {
    q: '接入后接口响应变慢或响应体重复？',
    a: '这是 SDK 早期版本的缺陷（响应体被重复写回、IP 探测用 getLocalHost 触发 DNS 超时），已在新版本修复：响应体只转发一次，本机 IP 走网卡枚举不走 DNS。确认使用的是最新构建的 jar 包。'
  }
]

/* ---------------------------------------------------------------- 数据加载 */

const load = async () => {
  loading.value = true
  try {
    const data = await fetchServices({ current: 1, size: 100 })
    services.value = (data && data.records) || []
  } catch (e) {
    services.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.onboard-steps { margin: 8px 0 20px; }

.doc-tabs :deep(.el-tabs__content) { padding-top: 4px; }

.doc-step {
  font-size: 13px;
  font-weight: 600;
  color: var(--gw-text-primary);
  margin: 16px 0 8px;
}

.doc-step:first-child { margin-top: 4px; }

.code-block {
  margin: 0;
  padding: 12px 14px;
  background: var(--gw-bg, #F7F8FA);
  border: 1px solid var(--gw-border-light);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--gw-text-secondary);
  overflow-x: auto;
  white-space: pre;
}

.doc-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--gw-text-secondary);
}

.doc-text code,
.doc-code {
  padding: 1px 5px;
  background: var(--gw-bg, #F7F8FA);
  border-radius: 3px;
  font-size: 12px;
  color: var(--gw-primary, #2F6BFF);
}

.doc-block {
  padding: 14px 16px;
  background: var(--gw-bg, #F7F8FA);
  border-radius: 6px;
  height: 100%;
}

.doc-block-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--gw-text-primary);
}
</style>
