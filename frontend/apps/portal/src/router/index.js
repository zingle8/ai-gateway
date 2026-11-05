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

export const modules = [overview, stats, discovery, trace, onboard, auth, perm, system]

/**
 * 侧边栏菜单：只保留已落地的能力
 * 网关总览 / 数据统计 / 服务发现 / 日志追踪 / 接入中心 / 认证与安全 / 分组与权限 / 系统管理
 */
export const menuRoutes = modules.flatMap((m) => m.menu)

/**
 * 布局组件：Shell 是普通 SFC 组件（通过 props 接收 menus / brand），
 * 不能当工厂函数调用，这里用 h() 包装一层，把菜单与标题传进去。
 */
const Layout = {
  name: 'AppLayout',
  render: () => h(Shell, { menus: menuRoutes, brand: 'API网关平台' })
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

router.beforeEach((to) => {
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
  }
  return true
})

export default router
