/**
 * 接入中心模块
 */
import AutoOnboardView from './src/views/AutoOnboard.vue'
import SystemOnboardView from './src/views/SystemOnboard.vue'
import RouteConfigView from './src/views/RouteConfig.vue'
import { Placeholder } from '@zimo/ui'

export const menu = [
  {
    title: '接入中心',
    icon: 'Connection',
    children: [
      { path: '/system-onboard', title: '系统接入', icon: 'Promotion' },
      { path: '/auto-onboard', title: '自动接入', icon: 'MagicStick' },
      { path: '/route-config', title: '路由配置', icon: 'Share' },
      { path: '/go-live', title: '上线发布', icon: 'Upload' }
    ]
  }
]

export const routes = [
  {
    path: 'system-onboard',
    name: 'SystemOnboard',
    component: SystemOnboardView,
    meta: { title: '系统接入', subtitle: '新建应用并完成网关接入配置' }
  },
  {
    path: 'auto-onboard',
    name: 'AutoOnboard',
    component: AutoOnboardView,
    meta: { title: '自动接入', subtitle: '业务系统引入 SDK 后自动注册并上报调用日志' }
  },
  {
    path: 'route-config',
    name: 'RouteConfig',
    component: RouteConfigView,
    meta: { title: '路由配置', subtitle: '配置转发规则，业务方零改代码接入网关' }
  },
  {
    path: 'go-live',
    name: 'GoLive',
    component: Placeholder,
    meta: { title: '上线发布', subtitle: '接入配置灰度与正式发布' }
  }
]

export default { name: 'onboard', menu, routes }
