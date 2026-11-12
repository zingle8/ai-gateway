/**
 * 认证与安全模块
 *
 * 对应后端 jv-api-gateway-user（演进中的认证中心）：
 *   用户管理（已对接）/ 登录方式 / 单点登录 SSO / 令牌与会话 / 安全策略（UI 先行，接口待接入）
 *
 * 五个子菜单各自独立成页；页面内部内容块较多时，用 @zimo/ui 的 PaneTabs 做页内切换
 * （如「登录方式」页的 登录因子 / 验证码通道）。
 */
import UserIndexView from './src/views/user/Index.vue'
import LoginMethodsView from './src/views/LoginMethods.vue'
import SsoView from './src/views/Sso.vue'
import SessionsView from './src/views/Sessions.vue'
import SecurityPolicyView from './src/views/SecurityPolicy.vue'
import AuthExemptView from './src/views/AuthExempt.vue'
import ApiKeysView from './src/views/ApiKeys.vue'

export const menu = [
  {
    title: '认证与安全',
    icon: 'Lock',
    children: [
      { path: '/auth/user', title: '用户管理', icon: 'User', perm: 'auth.user.view' },
      { path: '/auth/login-methods', title: '登录方式', icon: 'Key', perm: 'auth.login.view' },
      { path: '/auth/sso', title: '单点登录', icon: 'Link', perm: 'auth.sso.view' },
      { path: '/auth/sessions', title: '令牌与会话', icon: 'Timer', perm: 'auth.session.view' },
      { path: '/auth/policy', title: '安全策略', icon: 'SetUp', perm: 'auth.policy.view' },
      { path: '/auth/exempt', title: '鉴权豁免', icon: 'Unlock', perm: 'auth.exempt.view' },
      { path: '/auth/api-keys', title: 'API Key', icon: 'Tools', perm: 'open.key.view' }
    ]
  }
]

export const routes = [
  // 只记住「认证与安全」时落用户管理
  { path: 'auth', redirect: '/auth/user' },
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
    path: 'auth/exempt',
    name: 'AuthExempt',
    component: AuthExemptView,
    meta: { title: '鉴权豁免', subtitle: '免登录 / 免限流精细放行，支持到期自动失效' }
  },
  {
    path: 'auth/policy',
    name: 'AuthPolicy',
    component: SecurityPolicyView,
    meta: { title: '安全策略', subtitle: '登录保护、验证码限频、密码策略与双因子要求' }
  },
  {
    path: 'auth/api-keys',
    name: 'AuthApiKeys',
    component: ApiKeysView,
    meta: { title: 'API Key', subtitle: '开放接口凭证：签发 / 轮换 / 吊销，网关侧 SHA-256 校验' }
  }
]

export default { name: 'auth', menu, routes }
