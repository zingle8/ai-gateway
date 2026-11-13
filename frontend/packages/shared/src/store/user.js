import { defineStore } from 'pinia'
import request from '../request'

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
    userInfo: JSON.parse(readPersisted(USER_KEY) || 'null'),
    /** 当前用户有效权限点（所属分组并集，登录会话内缓存） */
    permCodes: [],
    /** 权限点是否已加载（失败不置位，下次导航重试） */
    permsLoaded: false
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
    },
    /** 是否拥有指定权限点 */
    hasPerm: (state) => (code) => state.permCodes.includes(code)
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
      this.permCodes = []
      this.permsLoaded = false
      // SSO IdP 会话 Cookie（v0.2 第 15 项）同步清除，避免残留影响授权端点判定
      document.cookie = 'gw_token=; path=/; max-age=0'
      ;[localStorage, sessionStorage].forEach((s) => {
        s.removeItem(TOKEN_KEY)
        s.removeItem(USER_KEY)
      })
    },
    /** 拉取当前用户权限点（登录会话内只拉一次，供菜单过滤 / 路由守卫使用） */
    async loadPerms() {
      if (this.permsLoaded || !this.token) return
      try {
        const codes = await request.get('/auth/my-perms')
        this.permCodes = Array.isArray(codes) ? codes : []
        this.permsLoaded = true
      } catch (e) {
        // 拉取失败保持未加载：下次导航重试，本次放行避免误伤
      }
    },
    /** 登录失效：清理会话（是否跳转由主应用注册的回调决定） */
    forceLogout() {
      this.clearSession()
    }
  }
})
