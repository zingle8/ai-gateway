import request from '@zimo/shared/request'

/** 数据统计：过滤条件字典（应用 / 服务 / 接口 / 方法） */
export function fetchStatsFilters() {
  return request.get('/stats/filters')
}

/**
 * 数据统计：汇总指标 + 分布 + Top 榜 + 趋势
 * @param {{appId?:string, serviceName?:string, api?:string, method?:string, hours?:number}} params
 */
export function fetchStatsSummary(params) {
  return request.get('/stats/summary', { params })
}

/**
 * 数据统计：请求量趋势
 * @param {{appId?:string, serviceName?:string, api?:string, method?:string, step?:number}} params
 *        step 为桶宽分钟数：1 / 5 / 10 / 30 / 60 / 120 / 1440，与总览 QPS 趋势一致
 */
export function fetchStatsTrend(params) {
  return request.get('/stats/trend', { params })
}
