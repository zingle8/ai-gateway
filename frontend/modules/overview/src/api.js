import request from '@zimo/shared/request'

/** 网关总览：核心指标汇总（jv-gateway-overview 微服务） */
export function fetchOverviewSummary() {
  return request.get('/overview/summary')
}

/** 网关总览：QPS 趋势（step 为桶粒度分钟数：1/5/10/30/60/120/1440） */
export function fetchOverviewTrend(step = 1) {
  return request.get('/overview/trend', { params: { step } })
}

/** 网关总览：运行时信息 */
export function fetchOverviewRuntime() {
  return request.get('/overview/runtime')
}

/** 网关总览：网关实例列表 */
export function fetchOverviewInstances() {
  return request.get('/overview/instances')
}
