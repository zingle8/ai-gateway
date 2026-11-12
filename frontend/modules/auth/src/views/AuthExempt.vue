<template>
  <div class="page">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="高危配置：这里的规则会让匹配路径绕过登录鉴权或限流（LOGIN / RATE / ALL）。规则为「追加放行」，yml 内置白名单不受影响；修改后 5 秒内在网关生效，请务必限定路径前缀与到期时间。"
      style="margin-bottom: 12px"
    />

    <div class="gw-card">
      <div class="gw-card-hd">
        鉴权豁免
        <span class="hd-hint">免登录 / 免限流 / 两者都免 · 支持到期自动失效</span>
      </div>
      <div class="gw-card-bd">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索名称 / 路径 / 备注" clearable style="width: 260px" />
          <el-button @click="load">刷新</el-button>
          <div class="spacer" />
          <span class="gw-sm gw-muted">共 {{ filtered.length }} 条 · 生效 {{ effectiveCount }} 条</span>
          <el-button type="primary" @click="openDialog()">新增豁免</el-button>
        </div>

        <el-table v-loading="loading" :data="pagedRows" size="small" border stripe>
          <el-table-column prop="name" label="规则名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="路径" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="path-code">{{ row.pathPattern }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="methods" label="方法" width="110" />
          <el-table-column label="豁免类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTag(row.exemptType)" effect="plain">
                {{ typeLabel(row.exemptType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="150">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTag(row)">
                {{ statusText(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="到期时间" width="160">
            <template #default="{ row }">
              <span class="gw-sm gw-muted">{{ row.expireAt ? row.expireAt.replace('T', ' ') : '永久' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button link @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && filtered.length === 0" description="还没有豁免规则" :image-size="70" />

        <div class="gw-card-ft exempt-pager">
          <span class="gw-sm gw-muted">共 {{ filtered.length }} 条豁免规则</span>
          <GwPager
            :total="filtered.length"
            :current="page"
            :size="size"
            @page-change="(p) => (page = p)"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑豁免规则' : '新增豁免规则'" width="560px">
      <el-alert
        v-if="!form.id"
        type="warning"
        :closable="false"
        show-icon
        title="新增即放行：请确认路径足够精确（避免 /**），并尽量设置到期时间。"
        style="margin-bottom: 12px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="form.name" placeholder="如：支付回调免登录" />
        </el-form-item>
        <el-form-item label="路径" prop="pathPattern">
          <el-input v-model="form.pathPattern" placeholder="/api/pay/callback/**（Ant 风格，需以 / 开头）" />
        </el-form-item>
        <el-form-item label="方法">
          <el-input v-model="form.methods" placeholder="* 或 GET,POST" />
        </el-form-item>
        <el-form-item label="豁免类型" prop="exemptType">
          <el-radio-group v-model="form.exemptType">
            <el-radio-button value="LOGIN">免登录</el-radio-button>
            <el-radio-button value="RATE">免限流</el-radio-button>
            <el-radio-button value="ALL">两者都免</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            placeholder="留空 = 永久"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GwPager } from '@zimo/ui'
import { fetchAuthExempts, createAuthExempt, updateAuthExempt, updateAuthExemptStatus, deleteAuthExempt } from '../api'

const rows = ref([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: null,
  name: '',
  pathPattern: '',
  methods: '*',
  exemptType: 'LOGIN',
  status: 1,
  expireAt: null,
  remark: ''
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  pathPattern: [{ required: true, message: '请输入匹配路径', trigger: 'blur' }],
  exemptType: [{ required: true, message: '请选择豁免类型', trigger: 'change' }]
}

const typeLabel = (t) => ({ LOGIN: '免登录', RATE: '免限流', ALL: '两者都免' }[t] || t)
const typeTag = (t) => ({ LOGIN: 'warning', RATE: 'primary', ALL: 'danger' }[t] || 'info')

const expired = (row) => row.expireAt && new Date(row.expireAt).getTime() < Date.now()
const statusText = (row) => (row.status !== 1 ? '停用' : expired(row) ? '已过期' : '生效中')
const statusTag = (row) => (row.status !== 1 ? 'info' : expired(row) ? 'warning' : 'success')

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter((r) =>
    [r.name, r.pathPattern, r.remark, r.methods, r.exemptType].some((f) => String(f ?? '').toLowerCase().includes(k))
  )
})

const effectiveCount = computed(() => rows.value.filter((r) => r.status === 1 && !expired(r)).length)
const pagedRows = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))

watch(keyword, () => {
  page.value = 1
})

const onSizeChange = (s) => {
  size.value = s
  page.value = 1
}

const load = async () => {
  loading.value = true
  try {
    rows.value = (await fetchAuthExempts()) || []
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  Object.assign(form, emptyForm(), row ? { ...row } : {})
  dialogVisible.value = true
  if (formRef.value) formRef.value.clearValidate()
}

const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const payload = {
      name: form.name,
      pathPattern: form.pathPattern,
      methods: form.methods || '*',
      exemptType: form.exemptType,
      status: form.status,
      expireAt: form.expireAt || null,
      remark: form.remark
    }
    if (payload.status === 1) {
      await ElMessageBox.confirm(
        `规则启用后，「${payload.pathPattern}」将${typeLabel(payload.exemptType)}，确认继续？`,
        '高危操作确认',
        { type: 'warning', confirmButtonText: '确认放行' }
      )
    }
    submitting.value = true
    try {
      if (form.id) {
        await updateAuthExempt(form.id, payload)
        ElMessage.success('规则已更新，5 秒内生效')
      } else {
        await createAuthExempt(payload)
        ElMessage.success('规则已创建，5 秒内生效')
      }
      dialogVisible.value = false
      await load()
    } finally {
      submitting.value = false
    }
  })
}

const toggle = async (row) => {
  const next = row.status === 1 ? 0 : 1
  if (next === 1) {
    await ElMessageBox.confirm(
      `启用后「${row.pathPattern}」将${typeLabel(row.exemptType)}，确认继续？`,
      '高危操作确认',
      { type: 'warning', confirmButtonText: '确认放行' }
    )
  }
  await updateAuthExemptStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用（5 秒内生效）' : '已停用（5 秒内生效）')
  await load()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除豁免规则「${row.name}」？删除后该路径恢复登录/限流校验。`, '删除确认', {
    type: 'warning'
  })
  await deleteAuthExempt(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.hd-hint {
  font-size: 12px;
  color: var(--gw-text-3);
  font-weight: 400;
  margin-left: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.spacer {
  flex: 1;
}

.path-code {
  font-family: var(--gw-font-mono, monospace);
  font-size: 12px;
  background: var(--gw-bg-soft, #f5f7fa);
  padding: 1px 6px;
  border-radius: 4px;
}

.exempt-pager {
  margin: 12px -16px -12px;
  border-top: 1px solid var(--gw-border-light);
}
</style>
