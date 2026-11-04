/**
 * 系统管理模块（用户管理 / 角色管理）
 */
import UserIndexView from './src/views/user/Index.vue'
import { Placeholder } from '@zimo/ui'

export const menu = [
  {
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/system/user', title: '用户管理', icon: 'User' },
      { path: '/system/role', title: '角色管理', icon: 'Avatar' }
    ]
  }
]

export const routes = [
  {
    path: 'system/user',
    name: 'SystemUser',
    component: UserIndexView,
    meta: { title: '用户管理', subtitle: '平台账号管理与权限分配' }
  },
  {
    path: 'system/role',
    name: 'SystemRole',
    component: Placeholder,
    meta: { title: '角色管理', subtitle: '角色定义与菜单权限分配' }
  }
]

export default { name: 'system', menu, routes }
