<template>
  <div class="page">
    <div class="gw-card">
      <div class="gw-card-hd">系统接入</div>
      <div class="gw-card-bd">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="网关平台提供可复用的公共模块与接入子模块，业务系统无需重复定义。"
          style="margin-bottom: 16px"
        />

        <el-descriptions :column="1" border size="small" class="onboard-desc">
          <el-descriptions-item label="公共模块">jv-api-gateway-client-common（DTO / 常量 / 枚举，无 Spring 依赖）</el-descriptions-item>
          <el-descriptions-item label="接入子模块">jv-api-gateway-client-starter（Spring Boot 自动装配）</el-descriptions-item>
          <el-descriptions-item label="自动能力">实例注册、定时心跳、优雅下线、调用日志采集与批量上报</el-descriptions-item>
          <el-descriptions-item label="平台调用">GatewayPlatformClient（总览 / 服务 / 日志接口）</el-descriptions-item>
        </el-descriptions>

        <el-divider content="left">配置项</el-divider>
        <el-table :data="configRows" size="small" border stripe>
          <el-table-column prop="key" label="配置项" width="260" />
          <el-table-column prop="desc" label="说明" min-width="240" />
          <el-table-column prop="def" label="默认值" width="140" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
const configRows = [
  { key: 'zimo.gateway.client.enabled', desc: '接入 SDK 总开关', def: 'true' },
  { key: 'zimo.gateway.client.server-addr', desc: '网关平台地址', def: 'http://127.0.0.1:8080/api' },
  { key: 'zimo.gateway.client.app-id', desc: '应用编码，如 app-order', def: 'app-demo' },
  { key: 'zimo.gateway.client.app-name', desc: '应用名称，如 订单中心', def: '示例应用' },
  { key: 'zimo.gateway.client.service-name', desc: '服务名，如 order-service', def: 'demo-service' },
  { key: 'zimo.gateway.client.port', desc: '服务端口，不填取 server.port', def: '8080' },
  { key: 'zimo.gateway.client.trace.enabled', desc: '是否采集请求与响应', def: 'true' },
  { key: 'zimo.gateway.client.trace.max-body-size', desc: '单条报文体最大采集长度，超出截断', def: '8192' },
  { key: 'zimo.gateway.client.trace.sample-rate', desc: '采样率 0~1', def: '1.0' },
  { key: 'zimo.gateway.client.trace.batch-size', desc: '批量上报条数阈值', def: '50' },
  { key: 'zimo.gateway.client.trace.flush-interval', desc: '定时上报间隔（ms）', def: '3000' },
  { key: 'zimo.gateway.client.registry.enabled', desc: '是否自动注册（自动接入）', def: 'true' },
  { key: 'zimo.gateway.client.registry.heartbeat-interval', desc: '心跳间隔（ms）', def: '30000' }
]
</script>
