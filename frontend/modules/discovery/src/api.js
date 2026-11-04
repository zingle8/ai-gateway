import request from '@zimo/shared/request'

/** 服务发现：服务列表（jv-gateway-discovery 微服务） */
export function fetchServices(params) {
  return request.get('/discovery/services', { params })
}

/** 服务发现：服务详情 */
export function fetchServiceDetail(id) {
  return request.get(`/discovery/services/${id}`)
}
