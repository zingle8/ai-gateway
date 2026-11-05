<template>
  <div class="page">
    <p class="demo-tip">
      <span class="gw-sm gw-muted">
        权限点按「平台 → 菜单 → 功能 → 按钮 / 展示 / 接口」四级组织，code 即后端鉴权标识（数据来自 /perm/dictionary，已落库）
      </span>
    </p>

    <div class="perm-layout">
      <!-- 平台 / 菜单树 -->
      <section class="gw-card perm-tree-card">
        <div class="gw-card-hd">
          <span>平台与菜单</span>
        </div>
        <div class="gw-card-bd">
          <el-input v-model="keyword" placeholder="搜索菜单" size="small" clearable class="tree-search" />
          <el-tree
            ref="treeRef"
            v-loading="loading"
            :data="menuTree"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            :props="{ label: 'name', children: 'children' }"
            @node-click="select"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span class="tree-label">{{ data.name }}</span>
                <span v-if="!data.parentId" class="tree-tag">平台</span>
                <span v-else-if="data.children && data.children.length" class="tree-tag">分组</span>
                <span v-else class="tree-count gw-sm gw-muted">{{ permCountOf(data.id) }}</span>
              </span>
            </template>
          </el-tree>
        </div>
        <div class="gw-card-ft">
          <span class="gw-sm gw-muted">
            {{ platformCount }} 个平台 · {{ menuCount }} 个菜单 · {{ items.length }} 个权限点
          </span>
        </div>
      </section>

      <!-- 权限点 -->
      <section class="gw-card">
        <div class="gw-card-hd">
          <span>{{ selectedName }} · 权限点</span>
          <div class="hd-actions">
            <el-input v-model="permKeyword" placeholder="名称 / code" clearable size="small" style="width: 160px" />
            <el-button size="small" type="primary" @click="openCreate">新增权限点</el-button>
          </div>
        </div>

        <div class="gw-card-bd">
          <div class="summary">
            <div v-for="s in summary" :key="s.label" class="summary-item">
              <span class="summary-num">{{ s.value }}</span>
              <span class="gw-sm gw-muted">{{ s.label }}</span>
            </div>
          </div>

          <el-table v-loading="loading" :data="permRows" size="small" style="width: 100%" max-height="480">
            <el-table-column label="菜单" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ menuName(row.menuId) }}</template>
            </el-table-column>
            <el-table-column prop="funcName" label="功能" width="120" />
            <el-table-column prop="name" label="权限点名称" min-width="140" />
            <el-table-column label="code" width="180">
              <template #default="{ row }">
                <span class="gw-mono gw-sm">{{ row.code }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="PERM_TYPE_TAG[row.type]" effect="plain">
                  {{ PERM_TYPE_LABEL[row.type] || row.type }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="api" label="对应接口" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="gw-mono gw-sm">{{ row.api }}</span>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled === 1"
                  size="small"
                  @change="(v) => togglePerm(row, v)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="removePerm(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="gw-card-ft">
          <span class="gw-sm gw-muted">
            「展示」类控制区块 / 字段可见性，「按钮」类控制可点操作，「接口」类只做后端鉴权、前端不感知
          </span>
        </div>
      </section>
    </div>

    <!-- 新增 / 编辑权限点 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑权限点' : '新增权限点'" width="520px">
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="所属菜单">
          <el-select v-model="form.menuId" filterable style="width: 100%">
            <el-option v-for="m in leafMenus" :key="m.id" :label="m.path" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="功能区块">
          <el-input v-model="form.funcName" placeholder="如：用户列表 / 分组列表" />
        </el-form-item>
        <el-form-item label="权限点名称">
          <el-input v-model="form.name" placeholder="如：新增用户" />
        </el-form-item>
        <el-form-item label="权限 code">
          <el-input v-model="form.code" placeholder="如：auth.user.create（唯一，后端鉴权用）" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button label="BUTTON">按钮</el-radio-button>
            <el-radio-button label="DISPLAY">展示</el-radio-button>
            <el-radio-button label="API">接口</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="对应接口">
          <el-input v-model="form.api" placeholder="如：POST /api/users，无接口填 —" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchDictionary, createPermItem, updatePermItem, deletePermItem
} from '../../api.js'
import { PERM_TYPE_LABEL, PERM_TYPE_TAG, toTree } from '../../data.js'

const loading = ref(false)
const saving = ref(false)
const menus = ref([])
const items = ref([])
const keyword = ref('')
const permKeyword = ref('')
const treeRef = ref(null)
const selected = ref(null)

watch(keyword, (v) => treeRef.value?.filter(v))

const filterNode = (value, data) => !value || data.name.includes(value)

const load = async () => {
  loading.value = true
  try {
    const dict = await fetchDictionary()
    menus.value = dict.menus || []
    items.value = dict.items || []
    if (!selected.value) selected.value = menus.value[0]
  } finally {
    loading.value = false
  }
}

const menuTree = computed(() => toTree(menus.value.map((m) => ({ ...m, children: undefined })), ''))

const select = (data) => {
  selected.value = data
}

/** 选中节点（平台 / 菜单分组 / 菜单）下所有叶子菜单 id */
const selectedMenuIds = computed(() => {
  if (!selected.value) return menus.value.map((m) => m.id)
  return collectMenuIds(selected.value)
})

function collectMenuIds(node) {
  if (node.children && node.children.length) return node.children.flatMap(collectMenuIds)
  return [node.id]
}

const selectedName = computed(() => selected.value?.name || '全部')

const permRows = computed(() => {
  const ids = new Set(selectedMenuIds.value)
  const k = permKeyword.value.trim()
  return items.value
    .filter((p) => ids.has(p.menuId))
    .filter((p) => !k || p.name.includes(k) || p.code.includes(k))
})

const summary = computed(() => {
  const rows = permRows.value
  return [
    { label: '权限点', value: rows.length },
    { label: '按钮', value: rows.filter((r) => r.type === 'BUTTON').length },
    { label: '展示', value: rows.filter((r) => r.type === 'DISPLAY').length },
    { label: '接口', value: rows.filter((r) => r.type === 'API').length }
  ]
})

const platformCount = computed(() => menus.value.filter((m) => !m.parentId).length)

const menuCount = computed(() => menus.value.filter((m) => m.parentId).length)

const permCountOf = (menuId) => items.value.filter((p) => p.menuId === menuId).length

const menuName = (menuId) => {
  const map = new Map(menus.value.map((m) => [m.id, m]))
  const names = []
  let cur = map.get(menuId)
  while (cur) {
    names.unshift(cur.name)
    cur = map.get(cur.parentId)
  }
  return names.join(' / ') || menuId
}

/** 供下拉选择：所有叶子菜单 + 完整路径 */
const leafMenus = computed(() => {
  const out = []
  const walk = (nodes, path) => nodes.forEach((n) => {
    const next = [...path, n.name]
    if (!n.children || !n.children.length) out.push({ id: n.id, path: next.join(' / ') })
    walk(n.children || [], next)
  })
  walk(menuTree.value, [])
  return out
})

const dialog = ref(false)
const form = reactive({
  id: null, menuId: '', funcName: '', name: '', code: '', type: 'BUTTON', api: '', enabled: 1
})

const openCreate = () => {
  const firstMenu = selectedMenuIds.value[0] || ''
  Object.assign(form, {
    id: null, menuId: firstMenu, funcName: '', name: '', code: '', type: 'BUTTON', api: '', enabled: 1
  })
  dialog.value = true
}

const openEdit = (row) => {
  Object.assign(form, { ...row, enabled: row.enabled })
  dialog.value = true
}

const submit = async () => {
  if (!form.name || !form.code) {
    ElMessage.warning('权限点名称与 code 必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updatePermItem(form.id, form)
    } else {
      await createPermItem(form)
    }
    dialog.value = false
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

const togglePerm = async (row, enabled) => {
  await updatePermItem(row.id, { ...row, enabled: enabled ? 1 : 0 })
  row.enabled = enabled ? 1 : 0
  ElMessage.info(`「${row.code}」已${enabled ? '启用' : '停用'}`)
}

const removePerm = async (row) => {
  await ElMessageBox.confirm(
    `确认删除权限点「${row.name}」？已分配该权限的分组会同步失去此权限。`,
    '删除权限点',
    { type: 'warning' }
  )
  await deletePermItem(row.id)
  ElMessage.success('已删除')
  await load()
}

load()
</script>

<style scoped>
.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
}

.perm-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

@media (max-width: 1100px) {
  .perm-layout { grid-template-columns: 1fr; }
}

.hd-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-search { margin-bottom: 8px; }

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 6px;
  font-size: 12.5px;
}

.tree-tag {
  font-size: 10px;
  line-height: 14px;
  padding: 0 5px;
  border-radius: 3px;
  background: var(--sidebar-active);
  color: var(--sidebar-active-text);
}

.tree-count {
  font-size: 11px;
  background: var(--gw-border-light);
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
}

.summary {
  display: flex;
  gap: 28px;
  padding: 2px 0 12px;
}

.summary-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.summary-num {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
