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

/* ==========================================================================
 * 主机运行指标（v0.2 第 10 项，SDK 每 60 秒上报）
 * ========================================================================== */

/** 窗口内每台主机的资源概览（CPU 均值/峰值、内存、堆、磁盘、网络、最近上报） */
export function fetchHostMetrics(minutes = 60) {
  return request.get('/discovery/metrics/hosts', { params: { minutes } })
}

/** 单主机时间序列（按分钟归并） */
export function fetchHostMetricTrend(hostname, minutes = 60) {
  return request.get('/discovery/metrics/trend', { params: { hostname, minutes } })
}

/** 趋势页主机下拉（近 24 小时有上报的主机） */
export function fetchHostnames() {
  return request.get('/discovery/metrics/hostnames')
}
