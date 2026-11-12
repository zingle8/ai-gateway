import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from './store/user'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000
})

/**
 * 登录失效回调：由主应用（@zimo/portal）在装配路由时注册，
 * 避免公共包反向依赖具体路由实例。
 */
let unauthorizedHandler = null

export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn
}

/** 业务码 401 与 HTTP 401 均视为登录失效 */
function handleUnauthorized(message) {
  const store = useUserStore()
  if (!store.token) return
  store.forceLogout()
  ElMessage.error(message || '登录已失效，请重新登录')
  if (unauthorizedHandler) unauthorizedHandler()
}

request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  // 变更审计留痕（v0.2 第 8 项）：trace / discovery 等无登录拦截器的模块
  // 从该头取操作人；有登录态的服务仍以 UserContext 为准，不会被伪造头覆盖
  const username = store.userInfo && store.userInfo.username
  if (username) {
    config.headers['X-Username'] = username
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    // 后端统一返回 { code, message, data }
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return body.data
      }
      if (body.code === 401 || body.code === 403) {
        handleUnauthorized(body.message)
        return Promise.reject(new Error(body.message || '登录已失效'))
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      handleUnauthorized('登录已失效，请重新登录')
    } else {
      ElMessage.error(
        (error.response && error.response.data && error.response.data.message) ||
          error.message ||
          '网络异常'
      )
    }
    return Promise.reject(error)
  }
)

export default request
