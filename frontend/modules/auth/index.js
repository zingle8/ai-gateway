/**
 * 认证与安全模块
 *
 * 对应后端 jv-api-gateway-user（演进中的认证中心）：
 *   用户管理（已对接）/ 登录方式 / 单点登录 SSO / 令牌与会话 / 安全策略（UI 先行，接口待接入）
 *
 * 菜单只保留一个入口，五个功能区（用户管理 / 登录方式 / 单点登录 / 令牌与会话 / 安全策略）
 * 收敛为同一个页面内的 Tab。Tab 仍是独立路由（/auth/*），刷新与分享链接可直达。
 */
import AuthIndexView from './src/views/Index.vue'

export const menu = [
  { path: '/auth', title: '认证与安全', icon: 'Lock' }
]

export const routes = [
  // 只记住「认证与安全」时落默认 Tab
  { path: 'auth', redirect: '/auth/user' },
  {
    path: 'auth/user',
    name: 'AuthUser',
    component: AuthIndexView,
    meta: { title: '用户管理', subtitle: '控制台账号与 C 端账号统一管理（按类型切换）' }
  },
  {
    path: 'auth/login-methods',
    name: 'AuthLoginMethods',
    component: AuthIndexView,
    meta: { title: '登录方式', subtitle: '密码 / 验证码 / 扫码 / SSO 各登录因子的开关与通道配置' }
  },
  {
    path: 'auth/sso',
    name: 'AuthSso',
    component: AuthIndexView,
    meta: { title: '单点登录', subtitle: '外部 IdP 接入配置、账号绑定关系与 SSO 登录审计' }
  },
  {
    path: 'auth/sessions',
    name: 'AuthSessions',
    component: AuthIndexView,
    meta: { title: '令牌与会话', subtitle: '在线会话、refresh 家族与令牌吊销记录' }
  },
  {
    path: 'auth/policy',
    name: 'AuthPolicy',
    component: AuthIndexView,
    meta: { title: '安全策略', subtitle: '登录保护、验证码限频、密码策略与双因子要求' }
  }
]

export default { name: 'auth', menu, routes }
