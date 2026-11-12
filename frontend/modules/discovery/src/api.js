import request from '@zimo/shared/request'

/** 服务发现：服务列表（jv-api-gateway-discovery 微服务） */
export function fetchServices(params) {
  return request.get('/discovery/services', { params })
}

/** 服务发现：服务详情 */
export function fetchServiceDetail(id) {
  return request.get(`/discovery/services/${id}`)
}

/** 服务发现：筛选项字典（应用 / 服务 / 语言，取库中真实出现过的值） */
export function fetchServiceFilters() {
  return request.get('/discovery/filters')
}

/** 服务发现：调用趋势（请求量 + QPS 双序列，支持应用 / 服务过滤） */
export function fetchDiscoveryTrend(params) {
  return request.get('/discovery/trend', { params })
}

/* ==========================================================================
 * 部署关联与跨主机调用（v0.2 第 11 项）
 * ========================================================================== */

/** 部署清单：主机 / pod → 实例 + 资源水位 */
export function fetchDeployments(minutes = 60) {
  return request.get('/discovery/deployments', { params: { minutes } })
}

/** 跨主机调用：源 IP → 目标 IP 调用量 / 耗时 / 错误率 */
export function fetchCrossHost(minutes = 60) {
  return request.get('/discovery/cross-host', { params: { minutes } })
}

/** 单点影响面：主机故障时受影响的实例与服务调用占比 */
export function fetchImpact(hostname, minutes = 60) {
  return request.get('/discovery/impact', { params: { hostname, minutes } })
}
