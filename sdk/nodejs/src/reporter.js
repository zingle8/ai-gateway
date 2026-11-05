'use strict';
/**
 * 调用日志 / span 异步批量上报：队列满直接丢弃，绝不阻塞业务
 * （与 Java SDK / Python SDK 同语义：3 秒 flush、单批 50 条、队列 1024）。
 */

const { postJson } = require('./_http');

const PATH_LOG_BATCH = '/trace/logs/batch';
const PATH_SPAN_BATCH = '/trace/spans/batch';

class TraceReporter {
  /**
   * @param {string} serverAddr 平台统一入口，如 http://127.0.0.1:28090/api
   * @param {string} clientToken 上报共享密钥（X-Gateway-Client-Token）
   * @param {object} [opts] reportInterval/queueSize/batchSize/timeoutMs
   */
  constructor(serverAddr, clientToken = '', opts = {}) {
    const base = serverAddr.replace(/\/+$/, '');
    this._urlLog = base + PATH_LOG_BATCH;
    this._urlSpan = base + PATH_SPAN_BATCH;
    this._clientToken = clientToken;
    this._interval = Math.max(1000, opts.reportInterval || 3000);
    this._batchSize = opts.batchSize || 50;
    this._timeoutMs = opts.timeoutMs || 5000;
    this._queueSize = opts.queueSize || 1024;
    this._logs = [];
    this._spans = [];
    this._timer = null;
    this._stopping = false;
  }

  start() {
    if (this._timer) return;
    this._timer = setInterval(() => this._drain(), this._interval);
    this._timer.unref(); // 不阻止进程退出
  }

  async stop() {
    if (!this._timer) return;
    clearInterval(this._timer);
    this._timer = null;
    await this._drain(); // 刷出剩余
  }

  offerLog(record) {
    this._offer(this._logs, record);
  }

  offerSpan(span) {
    this._offer(this._spans, span);
  }

  _offer(q, item) {
    if (this._stopping) return;
    if (q.length >= this._queueSize) {
      q.shift(); // 与 Java/Python SDK 一致：满即丢
    }
    q.push(item);
  }

  async _drain() {
    const logs = this._logs.splice(0);
    const spans = this._spans.splice(0);
    await Promise.all([
      this._flush(this._urlLog, logs),
      this._flush(this._urlSpan, spans),
    ]);
  }

  async _flush(url, items) {
    while (items.length > 0) {
      const batch = items.splice(0, this._batchSize);
      try {
        const { status } = await postJson(url, batch, this._clientToken, this._timeoutMs);
        if (status >= 300) console.warn(`[jv-gateway] 上报失败 ${url}: HTTP ${status}`);
      } catch (e) {
        console.warn(`[jv-gateway] 上报异常 ${url}: ${e.message}`); // 下个周期重试
      }
    }
  }
}

module.exports = { TraceReporter, PATH_LOG_BATCH, PATH_SPAN_BATCH };
