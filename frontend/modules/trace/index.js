/**
 * 可观测模块：日志追踪 / 服务拓扑
 */
import IndexView from './src/views/Index.vue'
import TopologyView from './src/views/Topology.vue'

export const menu = [
  { path: '/trace', title: '日志追踪', icon: 'Tickets' },
  { path: '/topology', title: '服务拓扑', icon: 'Share' }
]

export const routes = [
  {
    path: 'trace',
    name: 'Trace',
    component: IndexView,
    meta: { title: '日志追踪', subtitle: '网关调用链路日志查询与报文详情' }
  },
  {
    path: 'topology',
    name: 'Topology',
    component: TopologyView,
    meta: { title: '服务拓扑', subtitle: '服务间调用关系与慢链路分析' }
  }
]

export default { name: 'trace', menu, routes }
