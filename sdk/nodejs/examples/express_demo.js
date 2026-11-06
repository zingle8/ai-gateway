'use strict';
/**
 * Express 接入示例：注册 + 采集 + 入口鉴权三件套。
 *
 * 运行::
 *
 *   npm install express          # 业务自身依赖，SDK 本身零依赖
 *   node sdk/nodejs/examples/express_demo.js
 */

const express = require('express');
const { GatewayClient, traceExpress, GatewayAuth } = require('..');

const client = new GatewayClient({
  serverAddr: 'http://127.0.0.1:28090/api',
  appId: 'app-order', appName: '订单中心',
  serviceId: 'order-service', serviceName: '订单服务', port: 8000,
  clientToken: 'dev-gateway-token',            // 与网关 gateway.proxy.auth.client-token 同值
});
client.start();

const app = express();
app.use(express.json());
app.use(traceExpress(client));                                // 自动采集
app.use(new GatewayAuth(client).express());                   // 入口鉴权（可选，防止绕网关直连）

app.get('/orders/:id', (req, res) => {
  res.json({ orderId: Number(req.params.id), status: 'PAID' });
});

const server = app.listen(8000, () => console.log('order-service → http://127.0.0.1:8000'));

process.on('SIGTERM', () => {
  server.close();
  client.stop().then(() => process.exit(0)); // 刷出剩余日志并主动下线
});
