import request from '@zimo/shared/request'

/**
 * 接入中心只与 jv-api-gateway-discovery 微服务交互，
 * 读取业务系统通过 jv-api-gateway-client-starter 自动注册上来的实例。
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

/* ---------------------------------------------------------- 网关限流（tb_gw_rate_limit） */

/** 限流规则清单 */
export function fetchRateLimits() {
  return request.get('/rate-limits')
}

/** 新增限流规则 */
export function createRateLimit(data) {
  return request.post('/rate-limits', data)
}

/** 修改限流规则 */
export function updateRateLimit(id, data) {
  return request.put(`/rate-limits/${id}`, data)
}

/** 启停限流规则 */
export function updateRateLimitStatus(id, status) {
  return request.put(`/rate-limits/${id}/status`, { status })
}

/** 删除限流规则 */
export function deleteRateLimit(id) {
  return request.delete(`/rate-limits/${id}`)
}
/* ==========================================================================
 * 变更审计（v0.2 第 8 项）：配置 / 策略 / 路由的增删改留痕
 * ========================================================================== */

/** 变更记录（bizType: CONFIG / POLICY / ROUTE；bizKey 为空表示不限） */
export function fetchChangeLogs(params) {
  return request.get('/change-logs', { params })
}

/* ==========================================================================
 * 开放认证调用方约束（v0.2 第 6 项）：开关 + Key 约束清单
 * ========================================================================== */

/** 开放接口调用方约束开关（platform.open.auth.require-key） */
export function fetchOpenAuthPolicy() {
  return request.get('/policies/open-auth')
}

export function saveOpenAuthPolicy(requireKey) {
  return request.put('/policies/open-auth', { requireKey })
}

/** API Key 清单（含调用方约束字段，供约束表格展示） */
export function fetchApiKeyList() {
  return request.get('/keys')
}

/* ==========================================================================
 * 上报通道黑白名单（IP / 服务名，platform.report.acl）：网关过滤器 10 秒内生效
 * ========================================================================== */

/** 读取上报通道访问控制配置 */
export function fetchReportAcl() {
  return request.get('/policies/report-acl')
}

/** 保存上报通道访问控制配置 */
export function saveReportAcl(data) {
  return request.put('/policies/report-acl', data)
}
