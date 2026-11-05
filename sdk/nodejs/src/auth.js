'use strict';
/**
 * SDK 入口鉴权：回调网关开放验签接口，防止绕过网关直连业务系统。
 *
 * 用法（Express）::
 *
 *   const { GatewayClient } = require('jv-gateway')
 *   const { GatewayAuth } = require('jv-gateway')
 *
 *   const client = new GatewayClient({ appId: 'app-order', serviceName: 'order-service', port: 8000 })
 *   const auth = new GatewayAuth(client)                 // 复用 server_addr 与 client_token
 *   app.use(auth.express())                              // 入口鉴权
 *
 * 与 Java 端 GatewayAuthFilter 同语义；SDK 数据上报是出站调用，不受入口鉴权影响——
 * 即使调用方验签失败，日志与 span 照常上报，网关平台始终能看到真实流量。
 */

const { postJson } = require('./_http');

const DEFAULT_EXEMPT = ['/health', '/actuator', '/docs', '/openapi.json', '/favicon.ico'];

class GatewayAuth {
  /**
   * @param {object} client GatewayClient 实例（复用其 serverAddr 与 clientToken）
   * @param {object} [opts] exemptPaths（豁免路径前缀）、cacheSeconds（默认 60）、
   *                        timeoutMs（默认 3000）、failOpen（默认 false 安全优先）
   */
  constructor(client, opts = {}) {
    this._server = client.serverAddr;
    this._clientToken = client.clientToken;
    this._exempt = opts.exemptPaths || DEFAULT_EXEMPT;
    this._cacheSeconds = opts.cacheSeconds || 60;
    this._timeoutMs = opts.timeoutMs || 3000;
    this._failOpen = opts.failOpen === true;
    this._cache = new Map(); // token -> 过期时间戳（秒）
  }

  /**
   * 核心判定：返回 null = 放行；返回 { status, body } = 拒绝。
   * @param {string} path 请求路径
   * @param {string} authorization Authorization 请求头（Bearer JWT）
   * @param {string} apiKey X-Api-Key 请求头（gwak- 前缀凭证）
   */
  async check(path, authorization = '', apiKey = '') {
    if (this._exempt.some((p) => path.startsWith(p))) return null;

    let token = '';
    if (typeof authorization === 'string' && authorization.startsWith('Bearer ')) {
      token = authorization.slice(7).trim();
    } else if (apiKey) {
      token = String(apiKey).trim();
    }
    if (!token) {
      return { status: 401, body: { code: 401, message: '未提供访问凭证（Authorization: Bearer 或 X-Api-Key）' } };
    }

    const exp = this._cache.get(token);
    if (exp && exp > Date.now() / 1000) return null; // 缓存有效期内通过

    let valid = false;
    try {
      const { status, body } = await postJson(
        this._server + '/open/auth/token/verify', { token }, this._clientToken, this._timeoutMs);
      valid = status === 200 && body.data && body.data.valid === true;
    } catch (_) {
      if (this._failOpen) return null;
      return { status: 403, body: { code: 403, message: '验签服务不可达' } };
    }

    if (valid) {
      if (this._cache.size >= 2048) this._cache.clear();
      this._cache.set(token, Date.now() / 1000 + this._cacheSeconds);
      return null;
    }
    return { status: 401, body: { code: 401, message: '凭证无效或已过期' } };
  }

  /** Express 中间件：app.use(auth.express()) */
  express() {
    return (req, res, next) => {
      this.check(req.path || req.url, req.headers.authorization, req.headers['x-api-key'])
        .then((denied) => {
          if (!denied) return next();
          res.status(denied.status).json(denied.body);
        })
        .catch(() => res.status(403).json({ code: 403, message: '验签异常' }));
    };
  }

  /** 原生 http.createServer 包装：const server = auth.wrap(http.createServer(handler)) */
  wrap(server) {
    const check = this.check.bind(this);
    const origin = server.listenerCount('request') > 0
      ? server.listeners('request')[0]
      : (req, res) => { res.end(); };
    server.removeAllListeners('request');
    server.on('request', (req, res) => {
      check(req.url, req.headers.authorization, req.headers['x-api-key'])
        .then((denied) => {
          if (!denied) return origin(req, res);
          res.writeHead(denied.status, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(denied.body));
        })
        .catch(() => {
          res.writeHead(403, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ code: 403, message: '验签异常' }));
        });
    });
    return server;
  }
}

module.exports = { GatewayAuth, DEFAULT_EXEMPT };
