import request from '@zimo/shared/request'

/** 日志追踪：调用日志列表（jv-gateway-trace 微服务） */
export function fetchTraceLogs(params) {
  return request.get('/trace/logs', { params })
}

/** 日志追踪：日志详情 */
export function fetchTraceLogDetail(id) {
  return request.get(`/trace/logs/${id}`)
}

/** 日志追踪：筛选项字典（协议 / 服务名 / 报文格式，取库中真实出现过的值） */
export function fetchTraceFilters() {
  return request.get('/trace/logs/filters')
}

/** 链路详情：按 traceId 取全量 span（瀑布图），start_time 升序 */
export function fetchTraceSpans(traceId) {
  return request.get(`/trace/traces/${traceId}`)
}
