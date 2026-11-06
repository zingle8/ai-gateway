'use strict';
/**
 * Node.js SDK 冒烟脚本（无 Web 框架依赖，零第三方依赖）。
 *
 * 验证完整链路：注册 → 心跳 → 手动上报一条调用日志 → 下线 → 验签演示。
 *
 * 运行::
 *
 *   node sdk/nodejs/scripts/smoke_report.js
 *
 * 预期：五步全成功；控制台「服务发现」短暂出现 test-node-smoke（下线后消失）；
 * 「日志追踪」能看到 /job/smoke 这条调用日志。
 */

const { GatewayClient, verifyToken } = require('..');

async function main() {
  const client = new GatewayClient({
    serverAddr: 'http://127.0.0.1:28090/api',
    appId: 'app-regtech', appName: 'NCS RegTech 监管科技', // 按被测项目填所属应用
    serviceId: 'test-node-smoke', serviceName: 'Node冒烟服务', port: 0,          // 0 = 无 HTTP 端口
    clientToken: 'dev-gateway-token',                    // 与网关 gateway.proxy.auth.client-token 同值
  });

  console.log('① 注册：', (await client.register()) ? '成功' : '失败');
  console.log('② 心跳：', (await client.heartbeat()) ? '成功' : '失败');

  client.start(); // 启动上报线程
  client.report({
    method: 'POST', path: '/job/smoke', statusCode: 200, costMs: 42,
    reqBody: '{"hello":"node"}', resBody: '{"ok":true}',
    reqBytes: 16, resBytes: 12,
  });
  console.log('③ 已上报一条调用日志（3 秒内批量发出）');
  await new Promise((r) => setTimeout(r, 4000));

  console.log('④ 下线：', (await client.deregister()) ? '成功' : '失败');
  await client.stop();

  const demo = await verifyToken(client.serverAddr, 'invalid-token');
  console.log('⑤ 验签演示（无效 token 应 valid=false）：', JSON.stringify(demo));
}

main().then(
  () => process.exit(0),
  (e) => {
    console.error('冒烟失败：', e);
    process.exit(1);
  }
);
