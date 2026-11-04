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
