'use strict';
/**
 * GatewayClient：注册 + 心跳 + 采集上报一站式客户端。
 * 协议与 Java SDK（jv-api-gateway-client-starter）、Python SDK（jv_gateway）完全一致：
 *   注册/心跳/下线 → /discovery/services/*（下线为 DELETE）
 *   调用日志/span   → /trace/logs|spans/batch
 *   共享密钥头      → X-Gateway-Client-Token（网关对上报路径校验，防内网伪造）
 */

const crypto = require('node:crypto');
const os = require('node:os');

const { postJson, deleteJson } = require('./_http');
const { TraceReporter } = require('./reporter');

const PATH_REGISTER = '/discovery/services/register';
const PATH_HEARTBEAT = '/discovery/services/heartbeat';
const PATH_DEREGISTER = '/discovery/services/deregister';
const PATH_VERIFY = '/open/auth/token/verify';

function newId(bytes) {
  return crypto.randomBytes(bytes).toString('hex');
}

/** 生成 (traceId, spanId)，供中间件在无上游链路头时新建链路根。 */
function makeSpanIds() {
  return { traceId: newId(20), spanId: newId(8) };
}

function isoNow() {
  const d = new Date();
  const p = (n, w = 2) => String(n).padStart(w, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function localIp() {
  const ifs = os.networkInterfaces();
  for (const list of Object.values(ifs)) {
    for (const it of list) {
      if (it.family === 'IPv4' && !it.internal) return it.address;
    }
  }
  return '127.0.0.1';
}

/**
 * 平台开放验签接口：返回 data（valid=true/false，tokenType=USER/API_KEY）。
 * 凭证语义与网关、Java SDK 一致：用户 JWT 或 API Key（gwak- 前缀）均走此接口。
 */
async function verifyToken(serverAddr, token, timeoutMs = 3000) {
  const { body } = await postJson(
    serverAddr.replace(/\/+$/, '') + PATH_VERIFY,
    { token },
    '',
    timeoutMs
  );
  return body.data || { valid: false };
}

class GatewayClient {
  /**
   * @param {object} opts
   *   appId / appName                应用身份
   *   serviceId                      服务ID（英文唯一标识，如 order-service）；
   *                                  未配置时回退用 serviceName，兼容旧写法
   *   serviceName                    服务名（中文显示名，如 订单服务），可空，空则与服务ID一致
   *   serverAddr                     平台统一入口，如 http://127.0.0.1:28090/api
   *   port                           服务端口；0 表示无 HTTP 服务（脚本/定时任务）
   *   clientToken                    上报共享密钥，与网关 gateway.proxy.auth.client-token 同值
   *   heartbeatIntervalMs            心跳毫秒（默认 30000，下限 10000）
   */
  constructor(opts) {
    const resolvedId = opts && (opts.serviceId || opts.serviceName);
    if (!opts || !opts.appId || !resolvedId) {
      throw new Error('GatewayClient 需要 appId 与 serviceId（英文唯一标识，兼容旧参数 serviceName）');
    }
    this.appId = opts.appId;
    this.appName = opts.appName || opts.appId;
    // 服务ID（英文唯一标识）：未配置时回退 serviceName，兼容旧写法
    this.serviceId = resolvedId;
    // 服务名（中文显示名）：未配置时与服务ID一致
    this.serviceName = opts.serviceName || resolvedId;
    this.port = Number(opts.port || 0);
    this.ip = opts.ip || localIp();
    this.language = opts.language || 'Node.js';
    this.version = opts.version || '1.0.0';
    this.clientToken = opts.clientToken || '';
    this.serverAddr = (opts.serverAddr || 'http://127.0.0.1:28090/api').replace(/\/+$/, '');
    this._heartbeatMs = Math.max(10000, opts.heartbeatIntervalMs || 30000);
    this._registered = false;
    this._timer = null;
    this.reporter = new TraceReporter(this.serverAddr, this.clientToken, opts);
  }

  /** 注册 + 启动心跳与上报线程。 */
  start() {
    this.register();
    this.reporter.start();
    if (!this._timer) {
      this._timer = setInterval(() => this._beat(), this._heartbeatMs);
      this._timer.unref();
    }
    return this;
  }

  /** 刷出剩余日志并主动下线。 */
  async stop() {
    if (this._timer) {
      clearInterval(this._timer);
      this._timer = null;
    }
    await this.reporter.stop();
    await this.deregister();
  }

  async register() {
    const ok = await this._post(PATH_REGISTER, this._instance());
    this._registered = ok;
    if (ok) {
      console.log(`[jv-gateway] 已注册到网关平台：${this.appId} (${this.serviceId} / ${this.serviceName}) ${this.ip}:${this.port}`);
    } else {
      console.warn('[jv-gateway] 注册失败（不影响业务启动，下个心跳周期自动重试）');
    }
    return ok;
  }

  async heartbeat() {
    const ok = await this._post(PATH_HEARTBEAT, this._instance());
    this._registered = ok;
    return ok;
  }

  async deregister() {
    try {
      const { status, body } = await deleteJson(
        this.serverAddr + PATH_DEREGISTER, this._instance(), this.clientToken, 5000, this.appId);
      return status === 200 && body.code === 200;
    } catch (e) {
      console.warn(`[jv-gateway] 平台下线调用失败：${e.message}`);
      return false;
    }
  }

  /**
   * 无 Web 框架时手动上报一条调用日志（含 server span）。字段与 TraceLogDTO 对齐。
   * errType：NONE / CLIENT_ERROR / UPSTREAM_ERROR / SERVER_ERROR …
   */
  report({ method = 'GET', path = '/', statusCode = 200, costMs = 0, reqBody = '',
           resBody = '', errType = 'NONE', reqBytes = 0, resBytes = 0,
           traceId, spanId } = {}) {
    const ids = makeSpanIds();
    traceId = traceId || ids.traceId;
    spanId = spanId || ids.spanId;
    const now = isoNow();
    this.reporter.offerLog({
      appId: this.appId, appName: this.appName,
      serviceId: this.serviceId, serviceName: this.serviceName,
      api: path, protocol: 'HTTP/1.1', reqIp: this.ip, reqPort: this.port,
      statusCode, errType, cost: Math.round(costMs),
      reqTime: now, resTime: now, reqBody: reqBody || '', resBody: resBody || '',
      reqBytes: Number(reqBytes || 0), resBytes: Number(resBytes || 0),
      traceId, reqFormat: 'application/json', resFormat: 'application/json',
    });
    this.reporter.offerSpan({
      traceId, spanId, parentSpanId: '', kind: 'SERVER',
      appId: this.appId, appName: this.appName,
      serviceId: this.serviceId, serviceName: this.serviceName,
      api: path, peerService: '', statusCode, errType,
      cost: Math.round(costMs), startTime: now,
    });
  }

  _instance() {
    return {
      appId: this.appId, appName: this.appName,
      serviceId: this.serviceId, serviceName: this.serviceName,
      ip: this.ip, port: this.port, language: this.language,
      version: this.version, registerTime: isoNow(),
    };
  }

  async _post(path, payload) {
    try {
      const { status, body } = await postJson(this.serverAddr + path, payload, this.clientToken, 5000, this.appId);
      return status === 200 && body.code === 200;
    } catch (e) {
      console.warn(`[jv-gateway] 平台接口 ${path} 调用失败：${e.message}`);
      return false;
    }
  }

  async _beat() {
    try {
      // 未注册成功时先补注册（幂等，服务端按 serviceId upsert），与 Java SDK 同语义
      if (!this._registered) await this.register();
      if (this._registered) await this.heartbeat();
    } catch (_) { /* 后台线程绝不拖垮业务 */ }
  }
}

module.exports = { GatewayClient, verifyToken, makeSpanIds };
