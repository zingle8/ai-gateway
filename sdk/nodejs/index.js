'use strict';
/**
 * jv-gateway —— AI 网关平台 Node.js 接入 SDK（零第三方依赖，Node 16+）。
 *
 * 快速上手（Express）::
 *
 *   const { GatewayClient, traceExpress } = require('jv-gateway')
 *
 *   const client = new GatewayClient({
 *     serverAddr: 'http://127.0.0.1:28090/api',
 *     appId: 'app-order', appName: '订单中心',
 *     serviceName: 'order-service', port: 8000,
 *     clientToken: 'dev-gateway-token',      // 与网关 gateway.proxy.auth.client-token 同值
 *   })
 *   client.start()                            // 注册 + 心跳 + 上报
 *   app.use(traceExpress(client))             // 自动采集
 *   process.on('SIGTERM', () => client.stop())// 刷出剩余日志并下线
 */

const { GatewayClient, verifyToken, makeSpanIds } = require('./src/client');
const { TraceReporter } = require('./src/reporter');
const { GatewayAuth } = require('./src/auth');
const { traceExpress, traceWrap } = require('./src/middleware');

module.exports = {
  GatewayClient,
  TraceReporter,
  GatewayAuth,
  verifyToken,
  makeSpanIds,
  traceExpress,
  traceWrap,
};
