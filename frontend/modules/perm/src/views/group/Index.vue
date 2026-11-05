<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="info" effect="plain">设计稿</el-tag>
      <span class="gw-sm gw-muted">
        用户加入分组即继承该分组的全部权限点，多人多组取并集；后端 /api/perm/groups 接口待接入。
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
                <template #default="{ row }">{{ row.memberIds.length }} 人</template>
              </el-table-column>
              <el-table-column label="数据范围" width="150">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ SCOPE_LABEL[row.dataScope] }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openMembers(row, 'ORG')">成员</el-button>
                  <el-button link type="primary" @click="openEdit(row, 'ORG')">编辑</el-button>
                  <el-button link type="danger" :disabled="row.parentId === 0" @click="removeGroup(row, 'ORG')">删除</el-button>
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
            <el-table :data="permRows" row-key="id" size="small" style="width: 100%">
              <el-table-column prop="name" label="分组名称" min-width="180">
                <template #default="{ row }">
                  <span class="group-name">
                    <el-icon><Collection /></el-icon>
                    {{ row.name }}
                    <el-tag v-if="row.builtin" size="small" type="danger" effect="plain">内置</el-tag>
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="code" label="编码" width="150">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.code }}</span>
                </template>
              </el-table-column>
              <el-table-column label="成员" width="90" align="right">
                <template #default="{ row }">{{ row.memberIds.length }} 人</template>
              </el-table-column>
              <el-table-column label="权限点" width="150">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPerms(row)">
                    {{ row.builtin ? '全部（内置）' : `${row.permCodes.length} 项` }}
                  </el-button>
                </template>
              </el-table-column>
              <el-table-column label="数据范围" width="140">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ SCOPE_LABEL[row.dataScope] }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openMembers(row, 'PERM')">成员</el-button>
                  <el-button link type="primary" :disabled="row.builtin" @click="openEdit(row, 'PERM')">编辑</el-button>
                  <el-button link type="danger" :disabled="row.builtin" @click="removeGroup(row, 'PERM')">删除</el-button>
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
          共 {{ currentGroup?.memberIds.length || 0 }} 人，成员自动获得该分组的全部权限
        </span>
      </div>
      <el-table :data="groupMembers" size="small" style="width: 100%">
        <el-table-column prop="account" label="账号" width="120" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ROLE_LABEL[row.role] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属部门" min-width="120">
          <template #default="{ row }">{{ deptName(row.deptId) }}</template>
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
            :label="`${u.name}（${u.account}）`"
            :value="u.id"
          />
        </el-select>
        <el-button size="small" type="primary" :disabled="!addingIds.length" @click="addMembers">添加</el-button>
      </div>
      <template #footer>
        <el-button @click="memberDrawer = false">关闭</el-button>
        <el-button type="primary" @click="saveMembers">保存成员</el-button>
      </template>
    </el-drawer>

    <!-- 权限分配 -->
    <el-drawer v-model="permDrawer" :title="`分配权限 · ${currentGroup?.name || ''}`" size="560px">
      <div class="drawer-tip">
        <span class="gw-sm gw-muted">勾选后立即生效，已勾选 {{ checkedCount }} 项</span>
      </div>
      <el-tree
        ref="permTreeRef"
        :data="permTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :default-checked-keys="checkedKeys"
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
        <el-button type="primary" @click="savePerms">保存权限</el-button>
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
          <el-input v-model="form.code" placeholder="唯一编码，如 RD_GATEWAY" />
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
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PaneTabs } from '@zimo/ui'
import {
  ORG_TREE, PERM_GROUPS, PERMISSIONS, PLATFORM_TREE, USERS,
  ROLE_LABEL, SCOPE_LABEL, toTree
} from '../../data.js'

const tab = ref('org')
const orgKeyword = ref('')
const permKeyword = ref('')

// 部门分组：树形（深拷贝，页面内可增删改）
const orgGroups = ref(toTree(
  flattenOrg(ORG_TREE).map((n) => ({ ...n })),
  0
))

function flattenOrg(nodes, out = []) {
  nodes.forEach((n) => {
    out.push({ ...n, children: [] })
    if (n.children && n.children.length) flattenOrg(n.children, out)
  })
  return out
}

// 权限分组：扁平
const permGroups = ref(PERM_GROUPS.map((g) => ({ ...g, memberIds: [...g.memberIds], permCodes: [...g.permCodes] })))

const tabs = computed(() => [
  { name: 'org', label: '部门分组', badge: countOrg(orgGroups.value) },
  { name: 'perm', label: '权限分组', badge: permGroups.value.length }
])

function countOrg(nodes) {
  return nodes.reduce((sum, n) => sum + 1 + countOrg(n.children || []), 0)
}

/** 部门表格：关键字过滤但保留祖先，避免子节点被过滤后失联 */
const orgRows = computed(() => {
  const k = orgKeyword.value.trim()
  if (!k) return orgGroups.value
  const walk = (nodes) => nodes
    .map((n) => {
      const children = walk(n.children || [])
      const hit = n.name.includes(k) || n.code.includes(k)
      return hit || children.length ? { ...n, children } : null
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
const currentType = ref('PERM')
const addingIds = ref([])
const memberDraft = ref([])

const groupMembers = computed(() =>
  memberDraft.value.map((id) => USERS.find((u) => u.id === id)).filter(Boolean)
)

const candidateUsers = computed(() => USERS.filter((u) => !memberDraft.value.includes(u.id)))

const deptName = (deptId) => {
  const flat = flattenAll(ORG_TREE)
  return flat.find((d) => d.id === deptId)?.name || '—'
}

function flattenAll(nodes, out = []) {
  nodes.forEach((n) => {
    out.push(n)
    if (n.children && n.children.length) flattenAll(n.children, out)
  })
  return out
}

const openMembers = (row, type) => {
  currentGroup.value = row
  currentType.value = type
  memberDraft.value = [...row.memberIds]
  addingIds.value = []
  memberDrawer.value = true
}

const addMembers = () => {
  memberDraft.value = [...memberDraft.value, ...addingIds.value]
  addingIds.value = []
}

const removeMember = (user) => {
  memberDraft.value = memberDraft.value.filter((id) => id !== user.id)
}

const saveMembers = () => {
  if (currentGroup.value) currentGroup.value.memberIds = [...memberDraft.value]
  memberDrawer.value = false
  ElMessage.success(`已保存 ${memberDraft.value.length} 位成员（设计稿，未落库）`)
}

// 权限树：平台 → 菜单 → 功能 → 权限点
const permTreeRef = ref(null)
const checkedKeys = ref([])
const checkedCount = ref(0)

const permTree = computed(() =>
  PLATFORM_TREE.map((p) => ({
    id: `plat-${p.id}`,
    label: p.name,
    tag: '平台',
    tagType: 'info',
    children: (p.children || []).map(menuNode)
  }))
)

function menuNode(menu) {
  if (menu.children && menu.children.length) {
    return { id: `menu-${menu.id}`, label: menu.name, children: menu.children.map(menuNode) }
  }
  const perms = PERMISSIONS.filter((p) => p.menuId === menu.id)
  const funcs = [...new Set(perms.map((p) => p.func))]
  return {
    id: `menu-${menu.id}`,
    label: menu.name,
    tag: '菜单',
    tagType: 'success',
    children: funcs.map((f) => ({
      id: `func-${menu.id}-${f}`,
      label: `功能：${f}`,
      children: perms
        .filter((p) => p.func === f)
        .map((p) => ({ id: `perm-${p.code}`, label: `${p.name}  ${p.code}`, leaf: true }))
    }))
  }
}

const openPerms = (row) => {
  currentGroup.value = row
  checkedKeys.value = row.permCodes.map((c) => `perm-${c}`)
  checkedCount.value = row.permCodes.length
  permDrawer.value = true
  nextTick(() => {
    permTreeRef.value?.setCheckedKeys(checkedKeys.value)
    checkedCount.value = leafChecked().length
  })
}

const leafChecked = () => (permTreeRef.value?.getCheckedNodes(true) || []).filter((n) => n.id.startsWith('perm-'))

const onPermCheck = () => {
  checkedCount.value = leafChecked().length
}

const savePerms = () => {
  if (!currentGroup.value) return
  const codes = leafChecked().map((n) => n.id.replace('perm-', ''))
  currentGroup.value.permCodes = codes
  permDrawer.value = false
  ElMessage.success(`已保存 ${codes.length} 个权限点（设计稿，未落库）`)
}

// 新建 / 编辑
const formDialog = ref(false)
const form = reactive({
  id: null,
  type: 'ORG',
  name: '',
  code: '',
  parentId: 0,
  leader: '',
  dataScope: 'DEPT',
  remark: ''
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
    id: null,
    type,
    name: '',
    code: '',
    parentId: type === 'ORG' ? orgGroups.value[0]?.id || 0 : 0,
    leader: '',
    dataScope: type === 'ORG' ? 'DEPT' : 'SELF',
    remark: ''
  })
  formDialog.value = true
}

const openEdit = (row, type) => {
  Object.assign(form, {
    id: row.id,
    type,
    name: row.name,
    code: row.code,
    parentId: row.parentId || 0,
    leader: row.leader || '',
    dataScope: row.dataScope,
    remark: row.remark || ''
  })
  formDialog.value = true
}

const submitForm = () => {
  if (!form.name || !form.code) {
    ElMessage.warning('分组名称与编码必填')
    return
  }
  if (form.type === 'PERM') {
    if (form.id) {
      Object.assign(permGroups.value.find((g) => g.id === form.id), {
        name: form.name, code: form.code, dataScope: form.dataScope, remark: form.remark
      })
    } else {
      permGroups.value.unshift({
        id: Date.now(), name: form.name, code: form.code, dataScope: form.dataScope,
        memberIds: [], permCodes: [], remark: form.remark, builtin: false, status: 1
      })
    }
  } else if (form.id) {
    const node = findOrgNode(orgGroups.value, form.id)
    if (node) Object.assign(node, {
      name: form.name, code: form.code, dataScope: form.dataScope, remark: form.remark, leader: form.leader
    })
  } else {
    const parent = form.parentId ? findOrgNode(orgGroups.value, form.parentId) : null
    const node = {
      id: Date.now(), name: form.name, code: form.code, parentId: form.parentId || 0,
      leader: form.leader, order: (parent?.children?.length || 0) + 1,
      dataScope: form.dataScope, remark: form.remark, memberIds: [], children: []
    }
    if (parent) parent.children.push(node)
    else orgGroups.value.push(node)
  }
  formDialog.value = false
  ElMessage.success('已保存（设计稿，未落库）')
}

function findOrgNode(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n
    const hit = findOrgNode(n.children || [], id)
    if (hit) return hit
  }
  return null
}

const removeGroup = async (row, type) => {
  await ElMessageBox.confirm(`确认删除分组「${row.name}」？成员不会被删除，只解除与分组的关联。`, '删除分组', { type: 'warning' })
  if (type === 'PERM') {
    permGroups.value = permGroups.value.filter((g) => g.id !== row.id)
  } else {
    const parent = findOrgNode(orgGroups.value, row.parentId)
    if (parent) parent.children = parent.children.filter((c) => c.id !== row.id)
    else orgGroups.value = orgGroups.value.filter((c) => c.id !== row.id)
  }
  ElMessage.success('已删除（设计稿，未落库）')
}
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
