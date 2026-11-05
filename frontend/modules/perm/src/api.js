import request from '@zimo/shared/request'

/* ==========================================================================
 * 分组与权限（后端 jv-api-gateway-user /perm 接口，经网关 28090）
 * ========================================================================== */

/** 分组全量（扁平；前端把 ORG 组成树、PERM 做列表） */
export const listGroups = () => request.get('/perm/groups')

export const createGroup = (data) => request.post('/perm/groups', data)

export const updateGroup = (id, data) => request.put(`/perm/groups/${id}`, data)

export const deleteGroup = (id) => request.delete(`/perm/groups/${id}`)

/** 分组成员（用户视图） */
export const fetchGroupMembers = (id) => request.get(`/perm/groups/${id}/members`)

/** 成员全量替换 */
export const updateGroupMembers = (id, userIds) => request.put(`/perm/groups/${id}/members`, { userIds })

/** 分组已绑定的权限点 code */
export const fetchGroupPerms = (id) => request.get(`/perm/groups/${id}/perms`)

/** 权限点全量替换 */
export const updateGroupPerms = (id, codes) => request.put(`/perm/groups/${id}/perms`, { codes })

/** 权限字典：平台/菜单树 + 权限点全量 */
export const fetchDictionary = () => request.get('/perm/dictionary')

/** 成员选择用的用户列表（复用后端 /users 分页接口） */
export const fetchUserOptions = (params) => request.get('/users', { params })

export const createPermItem = (data) => request.post('/perm/items', data)

export const updatePermItem = (id, data) => request.put(`/perm/items/${id}`, data)

export const deletePermItem = (id) => request.delete(`/perm/items/${id}`)

/* ==========================================================================
 * API Key（后端 /keys 接口；明文仅签发 / 轮换响应返回一次）
 * ========================================================================== */

export const listApiKeys = () => request.get('/keys')

export const createApiKey = (data) => request.post('/keys', data)

export const updateApiKeyStatus = (id, status) => request.put(`/keys/${id}/status`, { status })

export const rotateApiKey = (id) => request.post(`/keys/${id}/rotate`)

export const deleteApiKey = (id) => request.delete(`/keys/${id}`)

/** 当前用户有效权限点（所属分组并集），用于菜单 / 按钮可见性 */
export const fetchMyPerms = () => request.get('/auth/my-perms')
