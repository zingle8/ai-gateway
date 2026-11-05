import request from '../request'

/**
 * 认证接口（jv-api-gateway-user 微服务，前缀 /api/auth）
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 注册：成功即登录，返回 { token, id, username, nickname, role } */
export function register(data) {
  return request.post('/auth/register', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function fetchUserInfo() {
  return request.get('/auth/info')
}
