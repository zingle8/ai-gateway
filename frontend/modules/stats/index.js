/**
 * 数据统计模块
 * 对外只暴露 menu（菜单）与 routes（路由），由主应用（@zimo/portal）装配。
 */
import IndexView from './src/views/Index.vue'

export const menu = [
  { path: '/stats', title: '数据统计', icon: 'DataAnalysis', perm: 'stats.view' }
]

export const routes = [
  {
    path: 'stats',
    name: 'Stats',
    component: IndexView,
    meta: { title: '数据统计', subtitle: '按应用 / 服务 / 接口 / 方法维度分析网关指标' }
  }
]

export default { name: 'stats', menu, routes }
