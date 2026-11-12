import { h } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { Shell } from '@zimo/ui'
import { useUserStore, setUnauthorizedHandler } from '@zimo/shared'

// 业务模块：每个模块自带 menu + routes，与后端微服务一一对应
import overview from '@zimo/module-overview'
import stats from '@zimo/module-stats'
import discovery from '@zimo/module-discovery'
import trace from '@zimo/module-trace'
import system from '@zimo/module-system'
import onboard from '@zimo/module-onboard'
import auth from '@zimo/module-auth'
import perm from '@zimo/module-perm'

export const modules = [overview, stats, discovery, trace, auth, onboard, perm, system]

/**
 * 侧边栏菜单：只保留已落地的能力
 * 网关总览 / 数据统计 / 服务发现 / 日志追踪 / 认证与安全 / 接入中心 / 分组与权限 / 系统管理
 */
export const menuRoutes = modules.flatMap((m) => m.menu)

/**
 * 菜单 → 权限点映射：来自各模块 menu 声明的 perm 字段，与 tb_perm_item 的 DISPLAY 权限码一致。
 * 未声明 perm 的菜单（如拓扑、自动接入、调度计划、个人中心）不参与权限过滤，默认可见。
 */
export const MENU_PERM_MAP = (() => {
  const map = {}
  for (const group of menuRoutes) {
    if (group.perm) map[group.path] = group.perm
    for (const child of group.children || []) {
      if (child.perm) map[child.path] = child.perm
    }
  }
  return map
})()

/**
 * 按权限点过滤菜单：
 *  - 菜单项声明了 perm 而用户权限点不含它 → 剔除；
 *  - 分组下可见子项为空 → 整组剔除；
 *  - permCodes 未加载（首次导航守卫正在拉取）时返回全量，加载完成后响应式重渲染。
 */
export function filterMenusByPerms(menus, codes) {
  const has = (code) => !codes || codes.includes(code)
  return menus
    .map((group) => {
      if (!group.children) {
        return group.perm && !has(group.perm) ? null : group
      }
      const children = group.children.filter((c) => !c.perm || has(c.perm))
      return children.length ? { ...group, children } : null
    })
    .filter(Boolean)
}

/**
 * 菜单 → 后端服务映射：侧边栏状态点与悬浮信息用。
 * 每个一级菜单对应一个后端微服务；接入中心 / 认证 / 分组权限的控制台接口
 * 都落在 jv-api-gateway-user，系统设置落在 jv-api-gateway-config。
 */
/**
 * 菜单功能上线标记：悬浮提示里展示 [功能已上线] / [功能未上线]。
 *  - true  ：页面有真实后端业务（有接口交互）
 *  - false ：纯前端占位页 / 静态说明页，暂无后端业务
 *  - 未登记 ：不显示标记（文档类页面、文档库在此排除）
 * 基于各页面实际接口调用盘点（2026-11-06）：
 *   未上线 = 占位组件（go-live、system/role）或零接口调用
 *   （system-onboard、open-auth、login-methods、sso、sessions、policy）
 */
export const MENU_FEATURE_STATUS = {
  '/overview': true,
  '/stats': true,
  '/discovery': true,
  '/trace': true,
  '/topology': true,
  '/system-onboard': false,
  '/auto-onboard': true,
  '/route-config': true,
  '/go-live': false,
  '/rate-limit': true,
  '/open-auth': true,
  '/auth/user': true,
  '/auth/login-methods': true,
  '/auth/sso': false,
  '/auth/sessions': true,
  '/auth/policy': true,
  '/auth/exempt': true,
  '/auth/api-keys': true,
  '/perm/group': true,
  '/perm/org': true,
  '/perm/menu': true,
  '/system/role': false,
  '/system/settings': true,
  '/system/logs': true
}

export const MENU_SERVICES = {
  '/overview': 'jv-api-gateway-overview',
  '/stats': 'jv-api-gateway-overview',
  '/discovery': 'jv-api-gateway-discovery',
  '/trace': 'jv-api-gateway-trace',
  // 服务拓扑是 trace 模块下的独立一级路由（path 为 /topology，不以 /trace 开头），
  // 必须单独登记，否则最长前缀匹配不到、侧边栏拿不到状态点与悬浮信息
  '/topology': 'jv-api-gateway-trace',
  '/system-onboard': 'jv-api-gateway-user',
  '/auto-onboard': 'jv-api-gateway-user',
  '/route-config': 'jv-api-gateway-user',
  '/go-live': 'jv-api-gateway-user',
  '/rate-limit': 'jv-api-gateway-config',
  '/open-auth': 'jv-api-gateway-user',
  '/auth/user': 'jv-api-gateway-user',
  '/auth/login-methods': 'jv-api-gateway-user',
  '/auth/sso': 'jv-api-gateway-user',
  '/auth/sessions': 'jv-api-gateway-user',
  '/auth/policy': 'jv-api-gateway-user',
  '/auth/exempt': 'jv-api-gateway-config',
  '/auth/api-keys': 'jv-api-gateway-user',
  '/perm/group': 'jv-api-gateway-user',
  '/perm/org': 'jv-api-gateway-user',
  '/perm/menu': 'jv-api-gateway-user',
  '/system/role': 'jv-api-gateway-user',
  '/system/settings': 'jv-api-gateway-config',
  '/system/logs': 'jv-api-gateway-config'
}

/**
 * 布局组件：Shell 是普通 SFC 组件（通过 props 接收 menus / brand / services），
 * 不能当工厂函数调用，这里用 h() 包装一层，把菜单、标题与服务映射传进去。
 */
const Layout = {
  name: 'AppLayout',
  render: () => {
    const userStore = useUserStore()
    // 权限点已加载则按权限过滤；未加载先给全量，loadPerms 完成后响应式重渲染
    const menus = userStore.permsLoaded
      ? filterMenusByPerms(menuRoutes, userStore.permCodes)
      : menuRoutes
    return h(Shell, {
    menus,
    brand: 'API网关平台',
    services: MENU_SERVICES,
    featureStatus: MENU_FEATURE_STATUS
    })
  }
}

export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/overview',
    children: modules.flatMap((m) => m.routes)
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 任意接口返回 401 / 403 时，统一跳回登录页并携带原地址，
 * 登录后自动回跳。
 */
setUnauthorizedHandler(() => {
  const current = router.currentRoute.value
  const redirect = current && current.path !== '/login' ? current.fullPath : ''
  router.replace(redirect ? { name: 'Login', query: { redirect } } : { name: 'Login' })
})

router.beforeEach(async (to) => {
  const store = useUserStore()
  if (!to.meta.public) {
    if (!store.token) {
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
    // 令牌已过期：先清理再跳登录，避免带着死令牌发无效请求
    if (store.expired) {
      store.forceLogout()
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
    // 登录会话内拉一次权限点（失败不置位，下次导航重试）
    await store.loadPerms()
    // 菜单级权限拦截：目标页声明了 perm 且用户无该权限点 → 落到首个有权限的菜单
    const need = MENU_PERM_MAP[to.path]
    if (need && store.permsLoaded && !store.permCodes.includes(need)) {
      const fallback = menuRoutes
        .flatMap((g) => [g, ...(g.children || [])])
        .find((m) => !m.perm || store.permCodes.includes(m.perm))
      return fallback ? fallback.path : { name: 'Login' }
    }
  }
  return true
})

export default router
