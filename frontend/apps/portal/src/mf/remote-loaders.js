/**
 * 联邦模式 remote 加载器（真实实现）。
 *
 * 宿主 vite.config 在联邦模式下把 'mf-remote-loaders' 别名指向本文件，
 * federation 插件会把这里的 mf_* 动态导入改写为 remote 加载；
 * 单体模式下别名指向 remote-loaders-stub.js，本文件不参与构建。
 * 加载器顺序 = 菜单顺序 = mf-manifest.json 的 modules 顺序。
 */
export const remoteLoaders = [
  { key: 'overview', load: () => import('mf_overview/module') },
  { key: 'stats', load: () => import('mf_stats/module') },
  { key: 'discovery', load: () => import('mf_discovery/module') },
  { key: 'trace', load: () => import('mf_trace/module') },
  { key: 'auth', load: () => import('mf_auth/module') },
  { key: 'onboard', load: () => import('mf_onboard/module') },
  { key: 'perm', load: () => import('mf_perm/module') },
  { key: 'system', load: () => import('mf_system/module') }
]
