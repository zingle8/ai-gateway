/**
 * 分组与权限：展示常量与通用工具
 *
 * 数据全部来自后端接口（src/api.js），此处只保留标签映射与树构建工具。
 */

export const ROLE_LABEL = { ADMIN: '管理员', OPERATOR: '运营', VIEWER: '只读' }

export const SCOPE_LABEL = {
  ALL: '全部数据',
  DEPT_AND_CHILD: '本部门及下级',
  DEPT: '本部门',
  SELF: '仅本人'
}

export const PERM_TYPE_LABEL = { BUTTON: '按钮', DISPLAY: '展示', API: '接口' }
export const PERM_TYPE_TAG = { BUTTON: 'primary', DISPLAY: 'warning', API: 'success' }

/** 把扁平列表（parentId 关联）转成 el-tree 需要的 children 结构（不改原数据） */
export const toTree = (list, rootId = 0) => {
  const map = new Map(list.map((n) => [n.id, { ...n, children: [] }]))
  const roots = []
  map.forEach((n) => {
    const parent = map.get(n.parentId)
    if (n.parentId === rootId || !parent) roots.push(n)
    else parent.children.push(n)
  })
  const sortByOrder = (arr) => {
    arr.sort((a, b) => (a.sort || a.order || 0) - (b.sort || b.order || 0))
    arr.forEach((n) => n.children && sortByOrder(n.children))
  }
  sortByOrder(roots)
  return roots
}
