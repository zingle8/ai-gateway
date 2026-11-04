import request from '@zimo/shared/request'

/** 网关总览：核心指标汇总（jv-gateway-overview 微服务），range 为统一时间区间 */
export function fetchOverviewSummary(range = '24h') {
  return request.get('/overview/summary', { params: { range } })
}

/** 网关总览：QPS 趋势（range 决定窗口，区间内固定 60 个点） */
export function fetchOverviewTrend(range = '24h') {
  return request.get('/overview/trend', { params: { range } })
}

/** 网关总览：运行时信息 */
export function fetchOverviewRuntime() {
  return request.get('/overview/runtime')
}

/** 网关总览：网关实例列表（调用量按 range 统计） */
export function fetchOverviewInstances(range = '24h') {
  return request.get('/overview/instances', { params: { range } })
}
