import request from '../request'

/**
 * 通用 HTTP 调试接口：把「请求 Header / 请求参数 / 响应 Header / 返回参数」
 * 以任意方法、任意路径转发给后端，用于网关联调。
 *
 * 说明：后端暂未提供该接口，调用失败属于预期行为。
 */
export function sendHttpRequest({ method = 'GET', url = '/', headers = {}, body = '' }) {
  return request.request({
    method,
    url,
    headers,
    data: ['GET', 'HEAD'].includes(method.toUpperCase()) ? undefined : body
  })
}
