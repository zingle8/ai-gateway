
/**
 * 服务发现模块
 */
import IndexView from './src/views/Index.vue'
import DeploymentView from './src/views/Deployment.vue'

export const menu = [
  { path: '/discovery', title: '服务发现', icon: 'Grid', perm: 'discovery.view' },
  { path: '/deployment', title: '部署关联', icon: 'Monitor', perm: 'discovery.view' }
]

export const routes = [
  {
    path: 'discovery',
    name: 'Discovery',
    component: IndexView,
    meta: { title: '服务发现', subtitle: '注册中心中的服务实例与端口信息' }
  },
  {
    path: 'deployment',
    name: 'Deployment',
    component: DeploymentView,
    meta: { title: '部署关联', subtitle: '主机 / pod ↔ 服务实例 ↔ 跨主机调用，含单点影响面分析' }
  }
]

export default { name: 'discovery', menu, routes }
