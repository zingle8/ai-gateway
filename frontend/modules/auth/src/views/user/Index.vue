<template>
  <div class="page">
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>用户管理</span>
        <div class="hd-actions">
          <el-input v-model="query.keyword" placeholder="账号 / 姓名 / 标识" clearable style="width: 180px;" @keyup.enter="load" />
          <el-select v-model="query.userType" placeholder="全部类型" clearable style="width: 120px;">
            <el-option label="控制台账号" value="CONSOLE" />
            <el-option label="C 端账号" value="CONSUMER" />
          </el-select>
          <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 130px;">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="运营" value="OPERATOR" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px;">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
          <el-button @click="load">查询</el-button>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </div>

      <div class="gw-card-bd no-pad">
        <el-table :data="rows" size="small" style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="账号" width="130" />
          <el-table-column prop="nickname" label="姓名" width="120" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.userType === 'CONSUMER' ? 'warning' : 'info'" effect="plain">
                {{ userTypeLabel(row.userType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="role" label="角色" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="roleTagType(row.role)">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="登录标识" min-width="190">
            <template #default="{ row }">
              <span class="ident">
                <span v-if="row.email" class="ident-tag">邮箱</span>
                <span v-if="row.phone" class="ident-tag">手机</span>
                <span v-if="row.username" class="ident-tag">用户名</span>
                <span v-if="!row.email && !row.phone && !row.username" class="gw-muted gw-sm">—</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="2FA" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.mfaEnabled" size="small" type="success">已开启</el-tag>
              <span v-else class="gw-muted gw-sm">未开启</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" width="170">
            <template #default="{ row }">{{ fmtTime(row.lastLoginTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="gw-card-ft">
        <span class="gw-muted gw-sm">共 {{ total }} 条 · 每页 {{ query.size }} 条 · 第 {{ query.current }} / {{ pages }} 页</span>
        <el-pagination
          layout="prev, pager, next"
          :current-page="query.current"
          :page-size="query.size"
          :total="total"
          @current-change="onPage"
        />
      </div>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="form.id ? '留空表示不修改' : '初始登录密码'" />
        </el-form-item>
        <el-form-item label="姓名" prop="nickname">
          <el-input v-model="form.nickname" placeholder="用户姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%;">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="运营" value="OPERATOR" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0"
            active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchUsers, createUser, updateUser, deleteUser, updateUserStatus } from '../../api'
import { fmtTime } from '@zimo/shared'

const rows = ref([])
const total = ref(0)
const pages = ref(1)
const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const query = reactive({
  keyword: '',
  userType: '',
  role: '',
  status: null,
  current: 1,
  size: 10
})

const emptyForm = () => ({
  id: null,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  role: 'VIEWER',
  status: 1
})

const form = reactive(emptyForm())

const load = async () => {
  const params = {}
  Object.keys(query).forEach((k) => {
    const v = query[k]
    if (v !== '' && v !== null && v !== undefined) params[k] = v
  })
  try {
    const page = await fetchUsers(params)
    rows.value = page.records || []
    total.value = page.total || 0
    pages.value = page.pages || 1
  } catch (e) {
    rows.value = []
    total.value = 0
  }
}

const reset = () => {
  query.keyword = ''
  query.userType = ''
  query.role = ''
  query.status = null
  query.current = 1
  load()
}

const onPage = (p) => {
  query.current = p
  load()
}

const openCreate = () => {
  Object.assign(form, emptyForm())
  formVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

const openEdit = (row) => {
  Object.assign(form, emptyForm(), {
    id: row.id,
    username: row.username,
    password: '',
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    role: row.role,
    status: row.status
  })
  formVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (form.id) {
      await updateUser(form.id, { ...form })
      ElMessage.success('修改成功')
    } else {
      await createUser({ ...form })
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (row) => {
  const next = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '禁用'}账号「${row.username}」？`, '提示', {
    type: 'warning'
  })
  await updateUserStatus(row.id, next)
  ElMessage.success('操作成功')
  load()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确认删除账号「${row.username}」？`, '警告', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  load()
}


const ROLE_LABEL = { ADMIN: '管理员', OPERATOR: '运营', VIEWER: '只读' }
const roleLabel = (role) => ROLE_LABEL[role] || role
const roleTagType = (role) => (role === 'ADMIN' ? 'danger' : role === 'OPERATOR' ? 'warning' : 'info')

// 用户类型：CONSOLE 控制台账号（角色权限体系）/ CONSUMER C 端账号。
// 后端字段未就绪时按控制台账号兜底显示，避免整列为空。
const userTypeLabel = (t) => (t === 'CONSUMER' ? 'C 端账号' : '控制台账号')

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!form.id && !value) {
          callback(new Error('请输入初始密码'));
          return;
        }
        if (value && String(value).length < 6) {
          callback(new Error('密码长度不能少于 6 位'));
        return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
}

onMounted(load)
</script>

<style scoped>
.hd-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 登录标识：一个账号可同时绑定邮箱 / 手机 / 用户名，用小标签并列展示 */
.ident {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ident-tag {
  font-size: 11px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: 3px;
  border: 1px solid var(--gw-border);
  color: var(--gw-text-secondary);
  background: var(--gw-bg-subtle, #FAFAFA);
}
</style>
