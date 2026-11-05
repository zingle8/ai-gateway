/**
 * 认证与安全模块
 *
 * 对应后端 jv-api-gateway-user（演进中的认证中心）：
 *   用户管理（已对接）/ 登录方式 / 单点登录 SSO / 令牌与会话 / 安全策略（UI 先行，接口待接入）
 */
import UserIndexView from './src/views/user/Index.vue'
import LoginMethodsView from './src/views/LoginMethods.vue'
import SsoView from './src/views/Sso.vue'
import SessionsView from './src/views/Sessions.vue'
import SecurityPolicyView from './src/views/SecurityPolicy.vue'

export const menu = [
  {
    title: '认证与安全',
    icon: 'Lock',
    children: [
      { path: '/auth/user', title: '用户管理', icon: 'User' },
      { path: '/auth/login-methods', title: '登录方式', icon: 'Key' },
      { path: '/auth/sso', title: '单点登录', icon: 'Link' },
      { path: '/auth/sessions', title: '令牌与会话', icon: 'Timer' },
      { path: '/auth/policy', title: '安全策略', icon: 'SetUp' }
    ]
  }
]

export const routes = [
  {
    path: 'auth/user',
    name: 'AuthUser',
    component: UserIndexView,
    meta: { title: '用户管理', subtitle: '控制台账号与 C 端账号统一管理（按类型切换）' }
  },
  {
    path: 'auth/login-methods',
    name: 'AuthLoginMethods',
    component: LoginMethodsView,
    meta: { title: '登录方式', subtitle: '密码 / 验证码 / 扫码 / SSO 各登录因子的开关与通道配置' }
  },
  {
    path: 'auth/sso',
    name: 'AuthSso',
    component: SsoView,
    meta: { title: '单点登录', subtitle: '外部 IdP 接入配置、账号绑定关系与 SSO 登录审计' }
  },
  {
    path: 'auth/sessions',
    name: 'AuthSessions',
    component: SessionsView,
    meta: { title: '令牌与会话', subtitle: '在线会话、refresh 家族与令牌吊销记录' }
  },
  {
    path: 'auth/policy',
    name: 'AuthPolicy',
    component: SecurityPolicyView,
    meta: { title: '安全策略', subtitle: '登录保护、验证码限频、密码策略与双因子要求' }
  }
]

export default { name: 'auth', menu, routes }
