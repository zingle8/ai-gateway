import request from '@zimo/shared/request'

/** 用户管理：分页列表（jv-gateway-user 微服务） */
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
