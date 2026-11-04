import request from '../request'

/**
 * 认证接口（jv-api-gateway-user 微服务，前缀 /api/auth）
 */
export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function fetchUserInfo() {
  return request.get('/auth/info')
}
