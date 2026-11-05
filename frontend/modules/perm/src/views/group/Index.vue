<template>
  <div class="page">
    <p class="demo-tip">
      <span class="gw-sm gw-muted">
        用户加入分组即继承该分组的全部权限点，多人多组取并集（数据来自后端 /perm/groups，已落库）
      </span>
    </p>

    <PaneTabs v-model="tab" :tabs="tabs">
      <!-- 部门分组：层级来自组织架构，主要承载数据范围 -->
      <template #org>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>部门分组</span>
            <div class="hd-actions">
              <el-input v-model="orgKeyword" placeholder="部门 / 编码" clearable size="small" style="width: 160px" />
              <el-button size="small" type="primary" @click="openCreate('ORG')">新建部门分组</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table
              v-loading="loading"
              :data="orgRows"
              row-key="id"
              size="small"
              default-expand-all
              :tree-props="{ children: 'children' }"
              style="width: 100%"
            >
              <el-table-column prop="name" label="分组名称" min-width="200">
                <template #default="{ row }">
                  <span class="group-name">
                    <el-icon><OfficeBuilding /></el-icon>
                    {{ row.name }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="code" label="编码" width="150">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.code }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="leader" label="负责人" width="120" />
              <el-table-column label="成员" width="90" align="right">
                <template #default="{ row }">{{ row.memberCount }} 人</template>
              </el-table-column>
              <el-table-column label="数据范围" width="150">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ SCOPE_LABEL[row.dataScope] || row.dataScope }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openMembers(row)">成员</el-button>
                  <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeGroup(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">
              部门分组的层级跟随「部门组织」维护，此处只决定该部门在平台内的数据可见范围
            </span>
          </div>
        </section>
      </template>

      <!-- 权限分组：扁平，绑定权限点集合 -->
      <template #perm>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>权限分组</span>
            <div class="hd-actions">
              <el-input v-model="permKeyword" placeholder="分组名称 / 编码" clearable size="small" style="width: 160px" />
              <el-button size="small" type="primary" @click="openCreate('PERM')">新建权限分组</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table v-loading="loading" :data="permRows" row-key="id" size="small" style="width: 100%">
              <el-table-column prop="name" label="分组名称" min-width="180">
                <template #default="{ row }">
                  <span class="group-name">
                    <el-icon><Collection /></el-icon>
                    {{ row.name }}
                    <el-tag v-if="row.builtin === 1" size="small" type="danger" effect="plain">内置</el-tag>
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="code" label="编码" width="150">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.code }}</span>
                </template>
              </el-table-column>
              <el-table-column label="成员" width="90" align="right">
                <template #default="{ row }">{{ row.memberCount }} 人</template>
              </el-table-column>
              <el-table-column label="权限点" width="150">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPerms(row)">
                    {{ row.builtin === 1 ? '全部（内置）' : `${row.permCount} 项` }}
                  </el-button>
                </template>
              </el-table-column>
              <el-table-column label="数据范围" width="140">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ SCOPE_LABEL[row.dataScope] || row.dataScope }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openMembers(row)">成员</el-button>
                  <el-button link type="primary" :disabled="row.builtin === 1" @click="openEdit(row)">编辑</el-button>
                  <el-button link type="danger" :disabled="row.builtin === 1" @click="removeGroup(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">
              内置分组不可删除；一个用户可同时属于多个权限分组，最终权限为并集
            </span>
          </div>
        </section>
      </template>
    </PaneTabs>

    <!-- 成员管理 -->
    <el-drawer v-model="memberDrawer" :title="`成员管理 · ${currentGroup?.name || ''}`" size="560px">
      <div class="drawer-tip">
        <span class="gw-sm gw-muted">
          共 {{ members.length }} 人，成员自动获得该分组的全部权限
        </span>
      </div>
      <el-table v-loading="memberLoading" :data="members" size="small" style="width: 100%">
        <el-table-column prop="username" label="账号" width="140" />
        <el-table-column prop="nickname" label="姓名" width="120" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ROLE_LABEL[row.role] || row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="add-member">
        <el-select v-model="addingIds" multiple filterable placeholder="选择用户加入分组" size="small" style="flex: 1">
          <el-option
            v-for="u in candidateUsers"
            :key="u.id"
            :label="`${u.nickname || u.username}（${u.username}）`"
            :value="u.id"
          />
        </el-select>
        <el-button size="small" type="primary" :disabled="!addingIds.length" @click="addMemberRows">添加</el-button>
      </div>
      <template #footer>
        <el-button @click="memberDrawer = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="saveMembers">保存成员</el-button>
      </template>
    </el-drawer>

    <!-- 权限分配 -->
    <el-drawer v-model="permDrawer" :title="`分配权限 · ${currentGroup?.name || ''}`" size="560px">
      <div class="drawer-tip">
        <span class="gw-sm gw-muted">勾选后立即生效，已勾选 {{ checkedCount }} 项</span>
      </div>
      <el-tree
        ref="permTreeRef"
        v-loading="dictLoading"
        :data="permTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'label', children: 'children' }"
        class="perm-tree"
        @check="onPermCheck"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.label }}</span>
            <el-tag v-if="data.tag" size="small" :type="data.tagType" effect="plain">{{ data.tag }}</el-tag>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="permDrawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePerms">保存权限</el-button>
      </template>
    </el-drawer>

    <!-- 新建 / 编辑分组 -->
    <el-dialog v-model="formDialog" :title="form.id ? '编辑分组' : '新建分组'" width="480px">
      <el-form :model="form" label-width="100px" size="small">
        <el-form-item label="分组类型">
          <el-radio-group v-model="form.type" :disabled="!!form.id">
            <el-radio-button label="ORG">部门分组</el-radio-button>
            <el-radio-button label="PERM">权限分组</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分组名称">
          <el-input v-model="form.name" placeholder="如：网关组 / 网关运维" />
        </el-form-item>
        <el-form-item label="分组编码">
          <el-input v-model="form.code" placeholder="唯一编码，如 RD_GATEWAY" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item v-if="form.type === 'ORG'" label="上级部门">
          <el-select v-model="form.parentId" style="width: 100%" placeholder="不选则为顶级组织">
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.label" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="负责人">
          <el-input v-model="form.leader" placeholder="选填" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="(label, key) in SCOPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PaneTabs } from '@zimo/ui'
import {
  listGroups, createGroup, updateGroup, deleteGroup,
  fetchGroupMembers, updateGroupMembers, fetchGroupPerms, updateGroupPerms, fetchDictionary, fetchUserOptions
} from '../../api.js'
import { ROLE_LABEL, SCOPE_LABEL, toTree } from '../../data.js'

const tab = ref('org')
const loading = ref(false)
const saving = ref(false)
const groups = ref([])
const users = ref([])
const orgKeyword = ref('')
const permKeyword = ref('')

const tabs = computed(() => [
  { name: 'org', label: '部门分组', badge: groups.value.filter((g) => g.type === 'ORG').length },
  { name: 'perm', label: '权限分组', badge: groups.value.filter((g) => g.type === 'PERM').length }
])

const load = async () => {
  loading.value = true
  try {
    const [groupList, userPage] = await Promise.all([
      listGroups(),
      fetchUserOptions({ page: 1, size: 200, status: 1 }).catch(() => ({ records: [] }))
    ])
    groups.value = groupList || []
    users.value = userPage?.records || []
  } finally {
    loading.value = false
  }
}

const orgGroups = computed(() => toTree(groups.value.filter((g) => g.type === 'ORG'), 0))
const permGroups = computed(() => groups.value.filter((g) => g.type === 'PERM'))

/** 部门表格：关键字过滤但保留祖先，避免子节点被过滤后失联 */
const orgRows = computed(() => {
  const k = orgKeyword.value.trim()
  if (!k) return orgGroups.value
  const walk = (nodes) => nodes
    .map((n) => {
      const children = walk(n.children || [])
      return n.name.includes(k) || n.code.includes(k) || children.length
        ? { ...n, children }
        : null
    })
    .filter(Boolean)
  return walk(orgGroups.value)
})

const permRows = computed(() => {
  const k = permKeyword.value.trim()
  if (!k) return permGroups.value
  return permGroups.value.filter((g) => g.name.includes(k) || g.code.includes(k))
})

// 成员与权限抽屉
const memberDrawer = ref(false)
const permDrawer = ref(false)
const currentGroup = ref(null)
const members = ref([])
const memberDraftIds = ref([])
const addingIds = ref([])
const memberLoading = ref(false)

const candidateUsers = computed(() => users.value.filter((u) => !memberDraftIds.value.includes(u.id)))

const openMembers = async (row) => {
  currentGroup.value = row
  memberDrawer.value = true
  memberLoading.value = true
  try {
    members.value = await fetchGroupMembers(row.id)
    memberDraftIds.value = members.value.map((m) => m.id)
  } finally {
    memberLoading.value = false
  }
  addingIds.value = []
}

const addMemberRows = () => {
  const picked = users.value.filter((u) => addingIds.value.includes(u.id))
  memberDraftIds.value = [...memberDraftIds.value, ...addingIds.value]
  members.value = [...members.value, ...picked.map((u) => ({
    id: u.id, username: u.username, nickname: u.nickname, role: u.role
  }))]
  addingIds.value = []
}

const removeMember = (member) => {
  memberDraftIds.value = memberDraftIds.value.filter((id) => id !== member.id)
  members.value = members.value.filter((m) => m.id !== member.id)
}

const saveMembers = async () => {
  saving.value = true
  try {
    await updateGroupMembers(currentGroup.value.id, memberDraftIds.value)
    memberDrawer.value = false
    ElMessage.success('成员已保存')
    await load()
  } finally {
    saving.value = false
  }
}

// 权限树：平台 → 菜单 → 功能 → 权限点
const permTreeRef = ref(null)
const checkedCount = ref(0)
const dictLoading = ref(false)
let dictionary = { menus: [], items: [] }

const permTree = computed(() => {
  const menuName = new Map(dictionary.menus.map((m) => [m.id, m.name]))
  const byMenu = new Map()
  dictionary.items.forEach((item) => {
    if (!byMenu.has(item.menuId)) byMenu.set(item.menuId, [])
    byMenu.get(item.menuId).push(item)
  })
  // 菜单树（平台 → 菜单）+ 叶子菜单挂功能与权限点
  const build = (nodes) => nodes.map((m) => {
    const items = byMenu.get(m.id) || []
    if (m.children && m.children.length) {
      return { id: `menu-${m.id}`, label: m.name, tag: '菜单', tagType: 'success', children: build(m.children) }
    }
    const funcs = [...new Set(items.map((i) => i.funcName))]
    return {
      id: `menu-${m.id}`,
      label: m.name,
      tag: '菜单',
      tagType: 'success',
      children: funcs.map((f) => ({
        id: `func-${m.id}-${f}`,
        label: `功能：${f}`,
        children: items.filter((i) => i.funcName === f).map((i) => ({
          id: `perm-${i.code}`,
          label: `${i.name}  ${i.code}`,
          leaf: true
        }))
      }))
    }
  })
  // 兜底：字典树缺失但 items 里出现的菜单也纳入
  const treeMenus = new Map(dictionary.menus.map((m) => [m.id, m]))
  const orphans = [...byMenu.keys()].filter((id) => !treeMenus.has(id))
  const root = build(dictionary.menus)
  if (orphans.length) {
    root.push({
      id: 'menu-orphan',
      label: '未挂载菜单',
      children: orphans.map((menuId) => ({
        id: `menu-${menuId}`,
        label: menuName.get(menuId) || menuId,
        children: (byMenu.get(menuId) || []).map((i) => ({
          id: `perm-${i.code}`, label: `${i.name}  ${i.code}`, leaf: true
        }))
      }))
    })
  }
  return root
})

const leafChecked = () => (permTreeRef.value?.getCheckedNodes(true) || [])
  .filter((n) => n.id.startsWith('perm-'))
  .map((n) => n.id.replace('perm-', ''))

const onPermCheck = () => {
  checkedCount.value = leafChecked().length
}

const openPerms = async (row) => {
  currentGroup.value = row
  permDrawer.value = true
  dictLoading.value = true
  try {
    if (!dictionary.items.length) {
      dictionary = await fetchDictionary()
    }
    const codes = await fetchGroupPerms(row.id)
    await nextTick()
    permTreeRef.value?.setCheckedKeys((codes || []).map((c) => `perm-${c}`))
    checkedCount.value = codes?.length || 0
  } finally {
    dictLoading.value = false
  }
}

const savePerms = async () => {
  saving.value = true
  try {
    await updateGroupPerms(currentGroup.value.id, leafChecked())
    permDrawer.value = false
    ElMessage.success('权限已保存')
    await load()
  } finally {
    saving.value = false
  }
}

// 新建 / 编辑
const formDialog = ref(false)
const form = reactive({
  id: null, type: 'ORG', name: '', code: '', parentId: 0, leader: '', dataScope: 'DEPT', remark: ''
})

const orgOptions = computed(() => {
  const out = []
  const walk = (nodes, depth) => {
    nodes.forEach((n) => {
      out.push({ id: n.id, label: `${'　'.repeat(depth)}${depth ? '└ ' : ''}${n.name}` })
      walk(n.children || [], depth + 1)
    })
  }
  walk(orgGroups.value, 0)
  return out
})

const openCreate = (type) => {
  Object.assign(form, {
    id: null, type, name: '', code: '',
    parentId: type === 'ORG' ? orgGroups.value[0]?.id || 0 : 0,
    leader: '', dataScope: type === 'ORG' ? 'DEPT' : 'SELF', remark: ''
  })
  formDialog.value = true
}

const openEdit = (row) => {
  Object.assign(form, {
    id: row.id, type: row.type, name: row.name, code: row.code,
    parentId: row.parentId || 0, leader: row.leader || '',
    dataScope: row.dataScope, remark: row.remark || ''
  })
  formDialog.value = true
}

const submitForm = async () => {
  if (!form.name || !form.code) {
    ElMessage.warning('分组名称与编码必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateGroup(form.id, form)
    } else {
      await createGroup(form)
    }
    formDialog.value = false
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

const removeGroup = async (row) => {
  await ElMessageBox.confirm(
    `确认删除分组「${row.name}」？成员与权限关联会一并解除。`,
    '删除分组',
    { type: 'warning' }
  )
  await deleteGroup(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
}

.hd-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.drawer-tip { margin-bottom: 10px; }

.add-member {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.perm-tree {
  max-height: calc(100vh - 220px);
  overflow: auto;
  font-size: 12.5px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
