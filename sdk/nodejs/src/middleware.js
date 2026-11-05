'use strict';
/**
 * 采集中间件：Express / 原生 http 自动采集调用日志（含 server span）。
 * 与 Python SDK（WSGI/ASGI）、Java SDK（GatewayTraceFilter）同一套排除清单：
 * SDK 数据上报路径不采集，避免上报请求本身被记为调用日志。
 */

const MAX_BODY = 8 * 1024;

const EXCLUDED = ['/api/trace/', '/api/discovery/services/'];

function excluded(path) {
  return EXCLUDED.some((p) => path.startsWith(p));
}

function trunc(buf) {
  return buf && buf.length > MAX_BODY ? buf.slice(0, MAX_BODY).toString('utf8') : (buf || Buffer.alloc(0)).toString('utf8');
}

/**
 * Express 采集中间件：
 *   app.use(traceExpress(client))   // 注意放在路由之前
 * 可选 body 记录：需在 json 解析后挂 res.req.body，或在前面用 express.json()。
 */
function traceExpress(client) {
  return (req, res, next) => {
    if (excluded(req.path || req.url)) return next();
    const start = Date.now();
    res.on('finish', () => {
      try {
        const reqBody = req.body ? (typeof req.body === 'string' ? req.body : JSON.stringify(req.body)) : '';
        client.report({
          method: req.method,
          path: req.originalUrl || req.url,
          statusCode: res.statusCode,
          costMs: Date.now() - start,
          reqBody: reqBody.slice(0, MAX_BODY),
          errType: res.statusCode >= 400 ? 'UPSTREAM_ERROR' : 'NONE',
        });
      } catch (_) { /* 采集绝不影响业务 */ }
    });
    next();
  };
}

/**
 * 原生 http 采集包装：
 *   const server = traceWrap(client, http.createServer(handler))
 */
function traceWrap(client, server) {
  const origin = server.listenerCount('request') > 0 ? server.listeners('request')[0] : null;
  if (!origin) return server;
  server.removeAllListeners('request');
  server.on('request', (req, res) => {
    if (excluded(req.url)) return origin(req, res);
    const start = Date.now();
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    res.on('finish', () => {
      try {
        client.report({
          method: req.method,
          path: req.url,
          statusCode: res.statusCode,
          costMs: Date.now() - start,
          reqBody: trunc(Buffer.concat(chunks)),
          errType: res.statusCode >= 400 ? 'UPSTREAM_ERROR' : 'NONE',
        });
      } catch (_) { /* 采集绝不影响业务 */ }
    });
    origin(req, res);
  });
  return server;
}

module.exports = { traceExpress, traceWrap, EXCLUDED };
