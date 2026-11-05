/**
 * 分组与权限模块
 *
 * 分组有两种类型：
 *   ORG  部门分组 —— 层级来自组织架构，决定数据范围
 *   PERM 权限分组 —— 扁平，绑定权限点集合
 * 用户是否属于某分组 = 是否拥有该分组的平台 / 菜单 / 功能 / 按钮权限（多组取并集）。
 *
 * 三个页面：
 *   分组管理        分组列表 + 成员分配 + 权限点勾选
 *   部门组织        组织树维护与部门成员
 *   平台功能与菜单  平台 → 菜单 → 功能 → 按钮 / 展示 / 接口 的权限点维护
 *
 * 当前为前端设计稿，数据在 src/data.js，后端 /api/perm/* 就绪后替换为 api.js。
 */
import GroupIndexView from './src/views/group/Index.vue'
import OrgIndexView from './src/views/org/Index.vue'
import MenuIndexView from './src/views/menu/Index.vue'

export const menu = [
  {
    title: '分组与权限',
    icon: 'Management',
    children: [
      { path: '/perm/group', title: '分组管理', icon: 'Collection' },
      { path: '/perm/org', title: '部门组织', icon: 'OfficeBuilding' },
      { path: '/perm/menu', title: '平台功能与菜单', icon: 'Menu' }
    ]
  }
]

export const routes = [
  {
    path: 'perm/group',
    name: 'PermGroup',
    component: GroupIndexView,
    meta: { title: '分组管理', subtitle: '部门分组与权限分组：成员归属 + 权限点分配（多组取并集）' }
  },
  {
    path: 'perm/org',
    name: 'PermOrg',
    component: OrgIndexView,
    meta: { title: '部门组织', subtitle: '组织架构维护，部门成员与数据范围' }
  },
  {
    path: 'perm/menu',
    name: 'PermMenu',
    component: MenuIndexView,
    meta: { title: '平台功能与菜单', subtitle: '平台 → 菜单 → 功能 → 按钮 / 展示 / 接口的权限点字典' }
  }
]

export default { name: 'perm', menu, routes }
