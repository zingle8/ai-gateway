import { defineStore } from 'pinia'

const TOKEN_KEY = 'gw_token'
const USER_KEY = 'gw_user'

/**
 * 会话持久化位置：
 * - 'local'   记住我，关浏览器后仍保持登录
 * - 'session' 不记住，关闭标签页即失效
 */
let persist = 'local'

const storage = () => (persist === 'local' ? localStorage : sessionStorage)

function readPersisted(key) {
  return localStorage.getItem(key) || sessionStorage.getItem(key) || ''
}

/** 解析 JWT payload，解析失败返回 null */
export function parseJwtPayload(token) {
  if (!token) return null
  const part = String(token).split('.')[1]
  if (!part) return null
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    return JSON.parse(decodeURIComponent(escape(atob(padded))))
  } catch (e) {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: readPersisted(TOKEN_KEY),
    userInfo: JSON.parse(readPersisted(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    nickname: (state) => (state.userInfo && state.userInfo.nickname) || '未登录',
    role: (state) => (state.userInfo && state.userInfo.role) || '',
    /** 令牌是否已过期（无 exp 声明时认为有效） */
    expired: (state) => {
      const payload = parseJwtPayload(state.token)
      if (!payload || !payload.exp) return false
      return payload.exp * 1000 <= Date.now()
    }
  },
  actions: {
    /**
     * 写入会话
     * @param {string} token     JWT
     * @param {object} userInfo  用户信息
     * @param {boolean} remember 是否记住我
     */
    setSession(token, userInfo, remember = true) {
      persist = remember ? 'local' : 'session'
      this.token = token
      this.userInfo = userInfo
      const target = storage()
      const other = persist === 'local' ? sessionStorage : localStorage
      ;[TOKEN_KEY, USER_KEY].forEach((k) => other.removeItem(k))
      target.setItem(TOKEN_KEY, token)
      target.setItem(USER_KEY, JSON.stringify(userInfo))
    },
    clearSession() {
      this.token = ''
      this.userInfo = null
      ;[localStorage, sessionStorage].forEach((s) => {
        s.removeItem(TOKEN_KEY)
        s.removeItem(USER_KEY)
      })
    },
    /** 登录失效：清理会话（是否跳转由主应用注册的回调决定） */
    forceLogout() {
      this.clearSession()
    }
  }
})
