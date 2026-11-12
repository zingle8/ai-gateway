/**
 * 系统管理模块（角色管理 / 系统设置）
 *
 * 用户管理已迁至「认证与安全」模块（@zimo/module-auth），
 * 本模块只保留与账号无关的平台级配置。
 */
import { Placeholder } from '@zimo/ui'
import SettingsView from './src/views/Settings.vue'
import SchedulerView from './src/views/Scheduler.vue'
import LogControlView from './src/views/LogControl.vue'

export const menu = [
  {
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/system/role', title: '角色管理', icon: 'Avatar', perm: 'system.role.view' },
      { path: '/system/settings', title: '系统设置', icon: 'Tools', perm: 'system.setting.view' },
      { path: '/system/logs', title: '日志管控', icon: 'Document', perm: 'system.setting.view' },
      { path: '/system/scheduler', title: '调度计划', icon: 'Timer' }
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
    component: SettingsView,
    meta: { title: '系统设置', subtitle: '平台级配置：菜单状态标识、鉴权缓存模式、开放认证开关、调度时间（保存即生效）' }
  },
  {
    path: 'system/logs',
    name: 'SystemLogs',
    component: LogControlView,
    meta: { title: '日志管控', subtitle: '动态日志级别（10 秒生效）、日志文件量与滚动策略、脱敏与限速说明' }
  },
  {
    path: 'system/scheduler',
    name: 'SystemScheduler',
    component: SchedulerView,
    meta: { title: '调度计划', subtitle: '调度任务列表与详情：任务状态、执行计划、执行记录（点击任务行查看）' }
  },
  // 旧地址兼容：用户管理迁到 /auth/user，收藏夹 / 历史链接不 404
  { path: 'system/user', redirect: '/auth/user' }
]

export default { name: 'system', menu, routes }
