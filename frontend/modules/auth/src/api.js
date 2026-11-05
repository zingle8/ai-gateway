import request from '@zimo/shared/request'

/* ==========================================================================
 * 已对接：账号管理（后端 jv-api-gateway-user 现有 /users 接口，走网关 28090）
 * ========================================================================== */

/** 用户管理：分页列表 */
export function fetchUsers(params) {
  return request.get('/users', { params })
}

/** 用户管理：用户详情 */
export function fetchUserDetail(id) {
  return request.get(`/users/${id}`)
}

/** 用户管理：新增用户 */
export function createUser(data) {
  return request.post('/users', data)
}

/** 用户管理：修改用户 */
export function updateUser(id, data) {
  return request.put(`/users/${id}`, data)
}

/** 用户管理：删除用户 */
export function deleteUser(id) {
  return request.delete(`/users/${id}`)
}

/** 用户管理：启用 / 禁用 */
export function updateUserStatus(id, status) {
  return request.put(`/users/${id}/status`, { status })
}

/* ==========================================================================
 * 待接入：登录方式 / SSO / 令牌与会话 / 安全策略
 *
 * 这四个页面当前为前端设计稿（本地示例数据，交互可操作但不落库）。
 * 后端接口确定后，在此处补充函数并替换页面内的 sample 数据源即可，
 * 页面结构无需改动。规划中的接口：
 *   GET  /auth/login-methods        登录因子开关与通道配置
 *   GET  /auth/sso/providers        IdP 列表与状态
 *   GET  /auth/sessions             在线会话（含设备 / IP / 登录方式）
 *   POST /auth/sessions/{id}/revoke 强制下线
 *   GET  /auth/policy               安全策略
 * ========================================================================== */


/* ==========================================================================
 * 已对接：API Key 管理（后端 /keys 接口；明文仅签发 / 轮换响应返回一次）
 * ========================================================================== */

export function fetchApiKeys() {
  return request.get('/keys')
}

export function issueApiKey(data) {
  return request.post('/keys', data)
}

export function setApiKeyStatus(id, status) {
  return request.put(`/keys/${id}/status`, { status })
}

export function rotateApiKey(id) {
  return request.post(`/keys/${id}/rotate`)
}

export function revokeApiKey(id) {
  return request.delete(`/keys/${id}`)
}

/** 统计看板：总数 / 今日新增·轮换·吊销·删除·启用 / 7 日内到期 */
export function fetchApiKeyStats() {
  return request.get('/keys/stats')
}

/** 最近操作记录（审计明细，默认 20 条） */
export function fetchApiKeyLogs(limit = 20) {
  return request.get('/keys/logs', { params: { limit } })
}
