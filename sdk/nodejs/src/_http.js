'use strict';
/**
 * 最小 HTTP 封装：node:http 实现，零第三方依赖。
 * 所有请求自动携带共享密钥头 X-Gateway-Client-Token（与 Java / Python SDK 同语义）。
 */

const http = require('node:http');

const HEADER_CLIENT_TOKEN = 'X-Gateway-Client-Token';

/**
 * POST JSON，返回 Promise<{ status, body }>。
 * 网络/超时错误 reject（调用方自行兜底，上报链路只告警不抛出）。
 */
function postJson(url, payload, clientToken = '', timeoutMs = 5000) {
  return requestJson(url, 'POST', payload, clientToken, timeoutMs);
}

/** DELETE + JSON body（主动下线用，与 Java SDK @DeleteMapping 一致）。 */
function deleteJson(url, payload, clientToken = '', timeoutMs = 5000) {
  return requestJson(url, 'DELETE', payload, clientToken, timeoutMs);
}

function requestJson(url, method, payload, clientToken, timeoutMs) {
  return new Promise((resolve, reject) => {
    const data = Buffer.from(JSON.stringify(payload), 'utf8');
    const u = new URL(url);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port || 80,
        path: u.pathname + u.search,
        method,
        timeout: timeoutMs,
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': data.length,
          ...(clientToken ? { [HEADER_CLIENT_TOKEN]: clientToken } : {}),
        },
      },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          let body = {};
          try {
            body = text ? JSON.parse(text) : {};
          } catch (_) {
            body = {};
          }
          resolve({ status: res.statusCode, body });
        });
      }
    );
    req.on('timeout', () => req.destroy(new Error(`请求超时：${url}`)));
    req.on('error', reject);
    req.end(data);
  });
}

module.exports = { postJson, deleteJson, HEADER_CLIENT_TOKEN };
