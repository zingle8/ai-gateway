/**
 * 分组与权限：设计稿数据
 *
 * 后端尚未提供 /api/perm/* 接口，这里用本地数据把交互跑通。
 * 接口就绪后本文件整体替换为 api.js 取数，页面结构不动。
 *
 * 模型：
 *   分组（Group）  type=ORG 部门组织（树形，带数据范围）/ type=PERM 权限分组（扁平，绑权限点）
 *   权限点（Perm） 平台 → 菜单 → 功能 → 按钮 / 展示 / 接口
 *   用户 ∈ 多个分组，有效权限 = 所属分组权限并集
 */

/** 候选成员（真实用户中心的数据来源，这里只做展示用） */
export const USERS = [
  { id: 11, account: 'wangzheng', name: '王峥', role: 'ADMIN', deptId: 1 },
  { id: 12, account: 'admin', name: '平台管理员', role: 'ADMIN', deptId: 2 },
  { id: 13, account: 'zhangsan', name: '张三', role: 'OPERATOR', deptId: 4 },
  { id: 14, account: 'lisi', name: '李四', role: 'OPERATOR', deptId: 3 },
  { id: 15, account: 'wangwu', name: '王五', role: 'VIEWER', deptId: 5 },
  { id: 16, account: 'zhaoliu', name: '赵六', role: 'VIEWER', deptId: 6 },
  { id: 17, account: 'sunqi', name: '孙七', role: 'OPERATOR', deptId: 7 },
  { id: 18, account: 'zhouba', name: '周八', role: 'VIEWER', deptId: 2 }
]

export const ROLE_LABEL = { ADMIN: '管理员', OPERATOR: '运营', VIEWER: '只读' }

export const SCOPE_LABEL = {
  ALL: '全部数据',
  DEPT_AND_CHILD: '本部门及下级',
  DEPT: '本部门',
  SELF: '仅本人'
}

/** 部门组织树：分组管理中 ORG 类型分组的层级来源，与「部门组织」页共用一棵树 */
export const ORG_TREE = [
  {
    id: 1,
    name: '齐墨科技',
    code: 'ZIMO',
    parentId: 0,
    leader: '王峥',
    order: 1,
    dataScope: 'ALL',
    remark: '根组织',
    memberIds: [11],
    children: [
      {
        id: 2,
        name: '研发中心',
        code: 'RD',
        parentId: 1,
        leader: '平台管理员',
        order: 1,
        dataScope: 'DEPT_AND_CHILD',
        remark: '负责平台研发与运维',
        memberIds: [12, 18],
        children: [
          {
            id: 3,
            name: '平台组',
            code: 'RD_PLATFORM',
            parentId: 2,
            leader: '李四',
            order: 1,
            dataScope: 'DEPT',
            remark: '控制台与开放平台',
            memberIds: [14],
            children: []
          },
          {
            id: 4,
            name: '网关组',
            code: 'RD_GATEWAY',
            parentId: 2,
            leader: '张三',
            order: 2,
            dataScope: 'DEPT',
            remark: '网关内核与路由',
            memberIds: [13],
            children: []
          }
        ]
      },
      {
        id: 5,
        name: '产品设计部',
        code: 'PD',
        parentId: 1,
        leader: '王五',
        order: 2,
        dataScope: 'DEPT',
        remark: '',
        memberIds: [15],
        children: []
      },
      {
        id: 6,
        name: '运营中心',
        code: 'OPS',
        parentId: 1,
        leader: '赵六',
        order: 3,
        dataScope: 'DEPT_AND_CHILD',
        remark: '',
        memberIds: [16],
        children: [
          {
            id: 7,
            name: '客服组',
            code: 'OPS_CS',
            parentId: 6,
            leader: '孙七',
            order: 1,
            dataScope: 'SELF',
            remark: '工单与用户反馈',
            memberIds: [17],
            children: []
          }
        ]
      }
    ]
  }
]

/** 权限分组（扁平）：成员 + 权限点集合 */
export const PERM_GROUPS = [
  {
    id: 101,
    name: '超级管理员',
    code: 'ROLE_SUPER',
    dataScope: 'ALL',
    memberIds: [11, 12],
    permCodes: [],
    remark: '拥有全部平台权限，不可删除',
    builtin: true,
    status: 1
  },
  {
    id: 102,
    name: '网关运维',
    code: 'ROLE_GW_OPS',
    dataScope: 'ALL',
    memberIds: [13, 14],
    permCodes: [
      'overview.view', 'stats.view', 'discovery.view', 'discovery.instance.offline',
      'trace.view', 'trace.detail', 'onboard.system.view', 'onboard.route.view',
      'onboard.route.edit', 'onboard.release.view', 'onboard.release.publish',
      'auth.session.view', 'auth.session.kick', 'auth.policy.view'
    ],
    remark: '日常巡检、路由与发布操作',
    builtin: false,
    status: 1
  },
  {
    id: 103,
    name: '只读观察者',
    code: 'ROLE_VIEWER',
    dataScope: 'SELF',
    memberIds: [15, 18],
    permCodes: [
      'overview.view', 'stats.view', 'discovery.view', 'trace.view',
      'onboard.route.view', 'auth.user.view'
    ],
    remark: '仅可浏览，无任何写操作',
    builtin: false,
    status: 1
  },
  {
    id: 104,
    name: '开放平台调用方',
    code: 'ROLE_OPEN_API',
    dataScope: 'SELF',
    memberIds: [],
    permCodes: ['open.key.view', 'open.usage.view'],
    remark: '给外部接入方分配，仅看自有 Key 与调用量',
    builtin: false,
    status: 1
  }
]

/**
 * 平台 → 菜单树：权限点的挂载骨架。
 * MENU 节点对应侧边栏的一项；平台级节点不对应页面。
 */
export const PLATFORM_TREE = [
  {
    id: 'p-console',
    name: '网关控制台',
    code: 'console',
    children: [
      { id: 'm-overview', name: '网关总览', code: 'overview' },
      { id: 'm-stats', name: '数据统计', code: 'stats' },
      { id: 'm-discovery', name: '服务发现', code: 'discovery' },
      { id: 'm-trace', name: '日志追踪', code: 'trace' },
      {
        id: 'm-onboard',
        name: '接入中心',
        code: 'onboard',
        children: [
          { id: 'm-onboard-system', name: '系统接入', code: 'onboard.system' },
          { id: 'm-onboard-auto', name: '自动接入', code: 'onboard.auto' },
          { id: 'm-onboard-route', name: '路由配置', code: 'onboard.route' },
          { id: 'm-onboard-release', name: '上线发布', code: 'onboard.release' }
        ]
      },
      {
        id: 'm-auth',
        name: '认证与安全',
        code: 'auth',
        children: [
          { id: 'm-auth-user', name: '用户管理', code: 'auth.user' },
          { id: 'm-auth-login', name: '登录方式', code: 'auth.login' },
          { id: 'm-auth-sso', name: '单点登录', code: 'auth.sso' },
          { id: 'm-auth-session', name: '令牌与会话', code: 'auth.session' },
          { id: 'm-auth-policy', name: '安全策略', code: 'auth.policy' }
        ]
      },
      {
        id: 'm-perm',
        name: '分组与权限',
        code: 'perm',
        children: [
          { id: 'm-perm-group', name: '分组管理', code: 'perm.group' },
          { id: 'm-perm-org', name: '部门组织', code: 'perm.org' },
          { id: 'm-perm-menu', name: '平台功能与菜单', code: 'perm.menu' }
        ]
      },
      {
        id: 'm-system',
        name: '系统管理',
        code: 'system',
        children: [
          { id: 'm-system-role', name: '角色管理', code: 'system.role' },
          { id: 'm-system-setting', name: '系统设置', code: 'system.setting' }
        ]
      }
    ]
  },
  {
    id: 'p-open',
    name: '开放平台',
    code: 'open',
    children: [
      { id: 'm-open-key', name: 'API Key 管理', code: 'open.key' },
      { id: 'm-open-usage', name: '调用量报表', code: 'open.usage' }
    ]
  },
  {
    id: 'p-account',
    name: '个人中心',
    code: 'account',
    children: [
      { id: 'm-account-profile', name: '账号信息', code: 'account.profile' },
      { id: 'm-account-bind', name: '绑定管理', code: 'account.bind' },
      { id: 'm-account-security', name: '安全设置', code: 'account.security' }
    ]
  }
]

/**
 * 权限点：menuId 挂在菜单上，func 是页面内的功能区块（列表 / 表单 / 详情…）。
 * type：BUTTON 可点操作 / DISPLAY 可见（字段、区块、导出）/ API 纯接口鉴权。
 */
export const PERMISSIONS = [
  // 网关总览
  { id: 2001, menuId: 'm-overview', func: '看板', code: 'overview.view', name: '查看总览看板', type: 'DISPLAY', api: 'GET /api/overview/summary', enabled: true },
  { id: 2002, menuId: 'm-overview', func: '看板', code: 'overview.alert.ack', name: '处理告警', type: 'BUTTON', api: 'POST /api/overview/alerts/{id}/ack', enabled: true },

  // 数据统计
  { id: 2010, menuId: 'm-stats', func: '报表', code: 'stats.view', name: '查看统计报表', type: 'DISPLAY', api: 'GET /api/stats/*', enabled: true },
  { id: 2011, menuId: 'm-stats', func: '报表', code: 'stats.export', name: '导出报表', type: 'BUTTON', api: 'GET /api/stats/export', enabled: true },

  // 服务发现
  { id: 2020, menuId: 'm-discovery', func: '实例列表', code: 'discovery.view', name: '查看服务与实例', type: 'DISPLAY', api: 'GET /api/services', enabled: true },
  { id: 2021, menuId: 'm-discovery', func: '实例列表', code: 'discovery.instance.offline', name: '手动下线实例', type: 'BUTTON', api: 'PUT /api/services/{id}/offline', enabled: true },
  { id: 2022, menuId: 'm-discovery', func: '实例列表', code: 'discovery.topology', name: '查看服务拓扑', type: 'DISPLAY', api: 'GET /api/services/edges', enabled: true },

  // 日志追踪
  { id: 2030, menuId: 'm-trace', func: '检索', code: 'trace.view', name: '检索调用链', type: 'DISPLAY', api: 'GET /api/traces', enabled: true },
  { id: 2031, menuId: 'm-trace', func: '检索', code: 'trace.detail', name: '查看链路详情', type: 'DISPLAY', api: 'GET /api/traces/{traceId}', enabled: true },
  { id: 2032, menuId: 'm-trace', func: '检索', code: 'trace.body', name: '查看请求响应报文', type: 'DISPLAY', api: 'GET /api/traces/{id}/body', enabled: false },

  // 系统接入
  { id: 2040, menuId: 'm-onboard-system', func: '接入列表', code: 'onboard.system.view', name: '查看接入应用', type: 'DISPLAY', api: 'GET /api/onboard/apps', enabled: true },
  { id: 2041, menuId: 'm-onboard-system', func: '接入列表', code: 'onboard.system.edit', name: '编辑接入应用', type: 'BUTTON', api: 'PUT /api/onboard/apps/{id}', enabled: true },

  // 自动接入
  { id: 2050, menuId: 'm-onboard-auto', func: '扫描', code: 'onboard.auto.view', name: '查看自动接入', type: 'DISPLAY', api: 'GET /api/onboard/scan', enabled: true },
  { id: 2051, menuId: 'm-onboard-auto', func: '扫描', code: 'onboard.auto.exec', name: '执行接入', type: 'BUTTON', api: 'POST /api/onboard/scan/{id}/apply', enabled: true },

  // 路由配置
  { id: 2060, menuId: 'm-onboard-route', func: '路由表', code: 'onboard.route.view', name: '查看路由', type: 'DISPLAY', api: 'GET /api/routes', enabled: true },
  { id: 2061, menuId: 'm-onboard-route', func: '路由表', code: 'onboard.route.edit', name: '新增 / 编辑路由', type: 'BUTTON', api: 'POST|PUT /api/routes', enabled: true },
  { id: 2062, menuId: 'm-onboard-route', func: '路由表', code: 'onboard.route.delete', name: '删除路由', type: 'BUTTON', api: 'DELETE /api/routes/{id}', enabled: true },

  // 上线发布
  { id: 2070, menuId: 'm-onboard-release', func: '发布单', code: 'onboard.release.view', name: '查看发布单', type: 'DISPLAY', api: 'GET /api/releases', enabled: true },
  { id: 2071, menuId: 'm-onboard-release', func: '发布单', code: 'onboard.release.publish', name: '执行发布', type: 'BUTTON', api: 'POST /api/releases/{id}/publish', enabled: true },
  { id: 2072, menuId: 'm-onboard-release', func: '发布单', code: 'onboard.release.rollback', name: '回滚发布', type: 'BUTTON', api: 'POST /api/releases/{id}/rollback', enabled: true },

  // 用户管理
  { id: 2080, menuId: 'm-auth-user', func: '用户列表', code: 'auth.user.view', name: '查看用户列表', type: 'DISPLAY', api: 'GET /api/users', enabled: true },
  { id: 2081, menuId: 'm-auth-user', func: '用户列表', code: 'auth.user.create', name: '新增用户', type: 'BUTTON', api: 'POST /api/users', enabled: true },
  { id: 2082, menuId: 'm-auth-user', func: '用户列表', code: 'auth.user.edit', name: '编辑用户', type: 'BUTTON', api: 'PUT /api/users/{id}', enabled: true },
  { id: 2083, menuId: 'm-auth-user', func: '用户列表', code: 'auth.user.delete', name: '删除用户', type: 'BUTTON', api: 'DELETE /api/users/{id}', enabled: true },
  { id: 2084, menuId: 'm-auth-user', func: '用户列表', code: 'auth.user.resetPwd', name: '重置密码', type: 'BUTTON', api: 'PUT /api/users/{id}/password', enabled: true },
  { id: 2085, menuId: 'm-auth-user', func: '用户列表', code: 'auth.user.col.phone', name: '手机号列可见', type: 'DISPLAY', api: '—', enabled: true },

  // 登录方式
  { id: 2090, menuId: 'm-auth-login', func: '登录因子', code: 'auth.login.view', name: '查看登录因子', type: 'DISPLAY', api: 'GET /api/auth/login-methods', enabled: true },
  { id: 2091, menuId: 'm-auth-login', func: '登录因子', code: 'auth.login.edit', name: '开关登录因子', type: 'BUTTON', api: 'PUT /api/auth/login-methods', enabled: true },
  { id: 2092, menuId: 'm-auth-login', func: '验证码通道', code: 'auth.login.channel', name: '配置验证码通道', type: 'BUTTON', api: 'PUT /api/auth/login-methods/channel', enabled: true },

  // 单点登录
  { id: 2100, menuId: 'm-auth-sso', func: '身份提供方', code: 'auth.sso.view', name: '查看 IdP', type: 'DISPLAY', api: 'GET /api/auth/sso/providers', enabled: true },
  { id: 2101, menuId: 'm-auth-sso', func: '身份提供方', code: 'auth.sso.edit', name: '配置 IdP', type: 'BUTTON', api: 'POST|PUT /api/auth/sso/providers', enabled: true },
  { id: 2102, menuId: 'm-auth-sso', func: '登录审计', code: 'auth.sso.audit', name: '查看 SSO 审计', type: 'DISPLAY', api: 'GET /api/auth/sso/audits', enabled: true },

  // 令牌与会话
  { id: 2110, menuId: 'm-auth-session', func: '在线会话', code: 'auth.session.view', name: '查看在线会话', type: 'DISPLAY', api: 'GET /api/auth/sessions', enabled: true },
  { id: 2111, menuId: 'm-auth-session', func: '在线会话', code: 'auth.session.kick', name: '强制下线', type: 'BUTTON', api: 'DELETE /api/auth/sessions/{id}', enabled: true },
  { id: 2112, menuId: 'm-auth-session', func: '吊销记录', code: 'auth.session.revoke.log', name: '查看吊销记录', type: 'DISPLAY', api: 'GET /api/auth/revokes', enabled: true },

  // 安全策略
  { id: 2120, menuId: 'm-auth-policy', func: '登录保护', code: 'auth.policy.view', name: '查看安全策略', type: 'DISPLAY', api: 'GET /api/auth/policy', enabled: true },
  { id: 2121, menuId: 'm-auth-policy', func: '登录保护', code: 'auth.policy.edit', name: '修改安全策略', type: 'BUTTON', api: 'PUT /api/auth/policy', enabled: true },
  { id: 2122, menuId: 'm-auth-policy', func: '策略下发', code: 'auth.policy.reload', name: '策略热加载（服务间）', type: 'API', api: 'POST /internal/policy/reload', enabled: true },

  // 分组管理
  { id: 2130, menuId: 'm-perm-group', func: '分组列表', code: 'perm.group.view', name: '查看分组', type: 'DISPLAY', api: 'GET /api/perm/groups', enabled: true },
  { id: 2131, menuId: 'm-perm-group', func: '分组列表', code: 'perm.group.edit', name: '新增 / 编辑分组', type: 'BUTTON', api: 'POST|PUT /api/perm/groups', enabled: true },
  { id: 2132, menuId: 'm-perm-group', func: '成员', code: 'perm.group.member', name: '管理分组成员', type: 'BUTTON', api: 'PUT /api/perm/groups/{id}/members', enabled: true },
  { id: 2133, menuId: 'm-perm-group', func: '权限', code: 'perm.group.grant', name: '分配分组权限', type: 'BUTTON', api: 'PUT /api/perm/groups/{id}/perms', enabled: true },

  // 部门组织
  { id: 2140, menuId: 'm-perm-org', func: '组织树', code: 'perm.org.view', name: '查看组织架构', type: 'DISPLAY', api: 'GET /api/perm/orgs', enabled: true },
  { id: 2141, menuId: 'm-perm-org', func: '组织树', code: 'perm.org.edit', name: '维护部门', type: 'BUTTON', api: 'POST|PUT|DELETE /api/perm/orgs', enabled: true },
  { id: 2142, menuId: 'm-perm-org', func: '部门成员', code: 'perm.org.member', name: '调整部门成员', type: 'BUTTON', api: 'PUT /api/perm/orgs/{id}/members', enabled: true },

  // 平台功能与菜单
  { id: 2150, menuId: 'm-perm-menu', func: '权限点', code: 'perm.menu.view', name: '查看权限点', type: 'DISPLAY', api: 'GET /api/perm/menus', enabled: true },
  { id: 2151, menuId: 'm-perm-menu', func: '权限点', code: 'perm.menu.edit', name: '维护权限点', type: 'BUTTON', api: 'POST|PUT /api/perm/menus', enabled: true },
  { id: 2152, menuId: 'm-perm-menu', func: '权限点', code: 'perm.menu.sync', name: '同步菜单结构', type: 'BUTTON', api: 'POST /api/perm/menus/sync', enabled: true },

  // 角色管理 / 系统设置
  { id: 2160, menuId: 'm-system-role', func: '角色列表', code: 'system.role.view', name: '查看角色', type: 'DISPLAY', api: 'GET /api/roles', enabled: true },
  { id: 2161, menuId: 'm-system-role', func: '角色列表', code: 'system.role.edit', name: '维护角色', type: 'BUTTON', api: 'POST|PUT /api/roles', enabled: true },
  { id: 2170, menuId: 'm-system-setting', func: '基础设置', code: 'system.setting.view', name: '查看系统设置', type: 'DISPLAY', api: 'GET /api/settings', enabled: true },
  { id: 2171, menuId: 'm-system-setting', func: '基础设置', code: 'system.setting.edit', name: '修改系统设置', type: 'BUTTON', api: 'PUT /api/settings', enabled: true },

  // 开放平台
  { id: 2180, menuId: 'm-open-key', func: 'Key 列表', code: 'open.key.view', name: '查看 API Key', type: 'DISPLAY', api: 'GET /api/keys', enabled: true },
  { id: 2181, menuId: 'm-open-key', func: 'Key 列表', code: 'open.key.issue', name: '签发 API Key', type: 'BUTTON', api: 'POST /api/keys', enabled: true },
  { id: 2182, menuId: 'm-open-key', func: 'Key 列表', code: 'open.key.revoke', name: '吊销 API Key', type: 'BUTTON', api: 'DELETE /api/keys/{id}', enabled: true },
  { id: 2183, menuId: 'm-open-usage', func: '调用量', code: 'open.usage.view', name: '查看调用量', type: 'DISPLAY', api: 'GET /api/keys/usage', enabled: true },
  { id: 2184, menuId: 'm-open-key', func: 'Key 列表', code: 'open.key.rotate', name: '密钥轮换（服务间）', type: 'API', api: 'POST /api/keys/{id}/rotate', enabled: true },

  // 个人中心
  { id: 2190, menuId: 'm-account-profile', func: '账号', code: 'account.profile.view', name: '查看账号信息', type: 'DISPLAY', api: 'GET /api/account/profile', enabled: true },
  { id: 2191, menuId: 'm-account-bind', func: '绑定', code: 'account.bind.manage', name: '管理绑定关系', type: 'BUTTON', api: 'PUT /api/account/bindings', enabled: true },
  { id: 2192, menuId: 'm-account-security', func: '安全', code: 'account.security.manage', name: '安全设置', type: 'BUTTON', api: 'PUT /api/account/security', enabled: true }
]

export const PERM_TYPE_LABEL = { BUTTON: '按钮', DISPLAY: '展示', API: '接口' }
export const PERM_TYPE_TAG = { BUTTON: 'primary', DISPLAY: 'warning', API: 'success' }

/** 把扁平数组转成 el-tree 需要的 children 结构（不改原数据） */
export const toTree = (list, rootId = 0) => {
  const map = new Map(list.map((n) => [n.id, { ...n, children: [] }]))
  const roots = []
  map.forEach((n) => {
    const parent = map.get(n.parentId)
    if (n.parentId === rootId || !parent) roots.push(n)
    else parent.children.push(n)
  })
  const sortByOrder = (arr) => {
    arr.sort((a, b) => (a.order || 0) - (b.order || 0))
    arr.forEach((n) => n.children && sortByOrder(n.children))
  }
  sortByOrder(roots)
  return roots
}
