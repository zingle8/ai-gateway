<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="info" effect="plain">设计稿</el-tag>
      <span class="gw-sm gw-muted">
        组织架构是部门分组的层级来源；部门决定数据范围，功能权限在「分组管理」里按权限点分配。
      </span>
    </p>

    <div class="org-layout">
      <!-- 组织树 -->
      <section class="gw-card org-tree">
        <div class="gw-card-hd">
          <span>组织架构</span>
          <el-button size="small" @click="addChild">新增子部门</el-button>
        </div>
        <div class="gw-card-bd">
          <el-input v-model="keyword" placeholder="搜索部门" size="small" clearable class="tree-search" />
          <el-tree
            ref="treeRef"
            :data="orgTree"
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
                <span class="tree-count gw-sm gw-muted">{{ data.memberIds.length }}</span>
              </span>
            </template>
          </el-tree>
        </div>
        <div class="gw-card-ft">
          <span class="gw-sm gw-muted">共 {{ totalNodes }} 个部门，{{ totalMembers }} 人</span>
        </div>
      </section>

      <!-- 部门详情 -->
      <section class="gw-card org-detail">
        <div class="gw-card-hd">
          <span>{{ current ? current.name : '未选择部门' }}</span>
          <div class="hd-actions">
            <el-button size="small" @click="save">保存</el-button>
            <el-button size="small" type="danger" plain :disabled="!current || current.parentId === 0" @click="removeDept">
              删除部门
            </el-button>
          </div>
        </div>

        <div v-if="current" class="gw-card-bd">
          <el-form :model="current" label-width="90px" size="small" class="dept-form">
            <el-form-item label="部门名称">
              <el-input v-model="current.name" />
            </el-form-item>
            <el-form-item label="部门编码">
              <el-input v-model="current.code" placeholder="如 RD_GATEWAY" />
            </el-form-item>
            <el-form-item label="负责人">
              <el-input v-model="current.leader" />
            </el-form-item>
            <el-form-item label="上级部门">
              <el-input :model-value="parentName" disabled />
            </el-form-item>
            <el-form-item label="数据范围">
              <el-select v-model="current.dataScope" style="width: 220px">
                <el-option v-for="(label, key) in SCOPE_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
              <span class="unit">该部门成员默认继承，可在分组管理里覆盖</span>
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="current.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>

          <div class="divider"></div>

          <div class="member-hd">
            <span class="member-title">部门成员（{{ current.memberIds.length }}）</span>
            <div class="hd-actions">
              <el-select v-model="addingIds" multiple filterable placeholder="添加成员" size="small" style="width: 240px">
                <el-option v-for="u in candidates" :key="u.id" :label="`${u.name}（${u.account}）`" :value="u.id" />
              </el-select>
              <el-button size="small" type="primary" :disabled="!addingIds.length" @click="addMembers">添加</el-button>
            </div>
          </div>

          <el-table :data="members" size="small" style="width: 100%" max-height="320">
            <el-table-column prop="account" label="账号" width="140" />
            <el-table-column prop="name" label="姓名" width="120" />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ ROLE_LABEL[row.role] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeMember(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else class="gw-card-bd empty">
          <span class="gw-muted gw-sm">从左侧选择一个部门</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ORG_TREE, USERS, ROLE_LABEL, SCOPE_LABEL, toTree } from '../../data.js'

const flatten = (nodes, out = []) => {
  nodes.forEach((n) => {
    out.push({ ...n, children: [] })
    if (n.children && n.children.length) flatten(n.children, out)
  })
  return out
}

const orgTree = ref(toTree(flatten(ORG_TREE), 0))
const keyword = ref('')
const treeRef = ref(null)
const currentId = ref(1)
const addingIds = ref([])

watch(keyword, (v) => treeRef.value?.filter(v))

const filterNode = (value, data) => !value || data.name.includes(value) || (data.code || '').includes(value)

const findNode = (nodes, id) => {
  for (const n of nodes) {
    if (n.id === id) return n
    const hit = findNode(n.children || [], id)
    if (hit) return hit
  }
  return null
}

const current = computed(() => findNode(orgTree.value, currentId.value))

const parentName = computed(() => {
  if (!current.value || !current.value.parentId) return '顶级组织'
  return findNode(orgTree.value, current.value.parentId)?.name || '—'
})

const select = (data) => {
  currentId.value = data.id
  addingIds.value = []
}

const allNodes = computed(() => {
  const out = []
  const walk = (nodes) => nodes.forEach((n) => {
    out.push(n)
    walk(n.children || [])
  })
  walk(orgTree.value)
  return out
})

const totalNodes = computed(() => allNodes.value.length)
const totalMembers = computed(() => allNodes.value.reduce((s, n) => s + n.memberIds.length, 0))

const members = computed(() =>
  (current.value?.memberIds || []).map((id) => USERS.find((u) => u.id === id)).filter(Boolean)
)

const candidates = computed(() =>
  USERS.filter((u) => !(current.value?.memberIds || []).includes(u.id))
)

const addChild = () => {
  if (!current.value) return
  const node = {
    id: Date.now(),
    name: '新部门',
    code: 'NEW_DEPT',
    parentId: current.value.id,
    leader: '',
    order: (current.value.children?.length || 0) + 1,
    dataScope: 'DEPT',
    remark: '',
    memberIds: [],
    children: []
  }
  current.value.children.push(node)
  currentId.value = node.id
  ElMessage.success('已新增子部门，请补充名称与编码')
}

const removeDept = async () => {
  if (!current.value || current.value.parentId === 0) return
  const { name, id, parentId } = current.value
  await ElMessageBox.confirm(
    `确认删除部门「${name}」？其子部门与成员关系会一并移除。`,
    '删除部门',
    { type: 'warning' }
  )
  const parent = findNode(orgTree.value, parentId)
  if (parent) parent.children = parent.children.filter((c) => c.id !== id)
  currentId.value = parentId
  ElMessage.success('已删除（设计稿，未落库）')
}

const addMembers = () => {
  current.value.memberIds = [...current.value.memberIds, ...addingIds.value]
  addingIds.value = []
  ElMessage.success('已添加成员')
}

const removeMember = (user) => {
  current.value.memberIds = current.value.memberIds.filter((id) => id !== user.id)
}

const save = () => {
  const { name, code, dataScope, memberIds } = current.value || {}
  if (!name || !code) {
    ElMessage.warning('部门名称与编码必填')
    return
  }
  ElMessage.success(`已保存「${name}」：${SCOPE_LABEL[dataScope]} / ${memberIds.length} 人（设计稿，未落库）`)
}
</script>

<style scoped>
.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
}

.org-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

@media (max-width: 1100px) {
  .org-layout { grid-template-columns: 1fr; }
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

.tree-count {
  font-size: 11px;
  background: var(--gw-border-light);
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
}

.dept-form { max-width: 620px; }
.dept-form :deep(.el-form-item) { margin-bottom: 12px; }

.unit {
  margin-left: 8px;
  font-size: 12px;
  color: var(--gw-text-secondary);
}

.divider {
  height: 1px;
  background: var(--gw-border);
  margin: 4px 0 14px;
}

.member-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.member-title {
  font-weight: 600;
  font-size: 13px;
}

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
}
</style>
