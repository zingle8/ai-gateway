import request from '@zimo/shared/request'

/**
 * 接入中心只与 jv-gateway-discovery 微服务交互，
 * 读取业务系统通过 jv-gateway-client-starter 自动注册上来的实例。
 */

/** 已注册服务分页列表 */
export function fetchServices(params) {
  return request.get('/discovery/services', { params })
}

/** 服务治理总览 */
export function fetchDiscoveryOverview() {
  return request.get('/discovery/overview')
}

/* ---------------------------------------------------------- 转发路由（tb_gw_route） */

/** 路由列表 */
export function fetchRoutes(params) {
  return request.get('/discovery/routes', { params })
}

/** 新增路由 */
export function createRoute(data) {
  return request.post('/discovery/routes', data)
}

/** 更新路由 */
export function updateRoute(id, data) {
  return request.put(`/discovery/routes/${id}`, data)
}

/** 逻辑删除 */
export function deleteRoute(id) {
  return request.delete(`/discovery/routes/${id}`)
}

/** 启用 / 停用 */
export function toggleRoute(id) {
  return request.post(`/discovery/routes/${id}/toggle`)
}
