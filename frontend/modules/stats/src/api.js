import request from '@zimo/shared/request'

/** 数据统计：过滤条件字典（应用 / 服务 / 接口 / 方法） */
export function fetchStatsFilters() {
  return request.get('/stats/filters')
}

/**
 * 数据统计：汇总指标 + 分布 + Top 榜
 * @param {{appId?:string, serviceName?:string, api?:string, method?:string, range?:string}} params
 *        range 为统一时间区间：1m / 5m / 10m / 30m / 1h / 2h / 24h / 1d / 7d / 30d
 */
export function fetchStatsSummary(params) {
  return request.get('/stats/summary', { params })
}

/**
 * 数据统计：请求量趋势（区间内固定 60 个点，桶宽由区间自动推导）
 * @param {{appId?:string, serviceName?:string, api?:string, method?:string, range?:string}} params
 */
export function fetchStatsTrend(params) {
  return request.get('/stats/trend', { params })
}
