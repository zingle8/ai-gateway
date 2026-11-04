/**
 * 网关总览模块
 * 对外只暴露 menu（菜单）与 routes（路由），由主应用（@zimo/portal）装配。
 */
import IndexView from './src/views/Index.vue'

export const menu = [
  { path: '/overview', title: '网关总览', icon: 'Odometer' }
]

export const routes = [
  {
    path: 'overview',
    name: 'Overview',
    component: IndexView,
    meta: { title: '网关总览', subtitle: '网关全局运行状态与核心指标总览' }
  }
]

export default { name: 'overview', menu, routes }
