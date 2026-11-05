/**
 * 系统管理模块（角色管理 / 系统设置）
 *
 * 用户管理已迁至「认证与安全」模块（@zimo/module-auth），
 * 本模块只保留与账号无关的平台级配置。
 */
import { Placeholder } from '@zimo/ui'

export const menu = [
  {
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/system/role', title: '角色管理', icon: 'Avatar' },
      { path: '/system/settings', title: '系统设置', icon: 'Tools' }
    ]
  }
]

export const routes = [
  {
    path: 'system/role',
    name: 'SystemRole',
    component: Placeholder,
    meta: { title: '角色管理', subtitle: '角色定义与菜单权限分配' }
  },
  {
    path: 'system/settings',
    name: 'SystemSettings',
    component: Placeholder,
    meta: { title: '系统设置', subtitle: '平台级参数：站点信息、通知通道、数据保留策略' }
  },
  // 旧地址兼容：用户管理迁到 /auth/user，收藏夹 / 历史链接不 404
  { path: 'system/user', redirect: '/auth/user' }
]

export default { name: 'system', menu, routes }
