/**
 * 日志追踪模块
 */
import IndexView from './src/views/Index.vue'

export const menu = [
  { path: '/trace', title: '日志追踪', icon: 'Tickets' }
]

export const routes = [
  {
    path: 'trace',
    name: 'Trace',
    component: IndexView,
    meta: { title: '日志追踪', subtitle: '网关调用链路日志查询与报文详情' }
  }
]

export default { name: 'trace', menu, routes }
