
/**
 * 服务发现模块
 */
import IndexView from './src/views/Index.vue'

export const menu = [
  { path: '/discovery', title: '服务发现', icon: 'Grid' }
]

export const routes = [
  {
    path: 'discovery',
    name: 'Discovery',
    component: IndexView,
    meta: { title: '服务发现', subtitle: '注册中心中的服务实例与端口信息' }
  }
]

export default { name: 'discovery', menu, routes }
