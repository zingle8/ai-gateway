<template>
  <div class="page">
    <div class="gw-card">
      <div class="gw-card-hd">
        限流策略
        <span class="hd-hint">令牌桶算法 · 规则保存后 5 秒内生效，无需重启网关</span>
      </div>
      <div class="gw-card-bd">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="每条启用规则都会参与匹配（GLOBAL 恒匹配 / ROUTE 按 Ant 路径 / API Key 按 keyId / IP 按来源地址），同一请求命中多条规则时分别扣令牌，任一不足即返回 429。Redis 优先，网关多实例共享令牌桶；Redis 不可用时自动回落本地计数。"
          style="margin-bottom: 14px"
        />

        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索规则名称 / 匹配目标 / 备注"
            clearable
            style="width: 280px"
          />
          <el-button @click="load">刷新</el-button>
          <div class="spacer" />
          <span class="gw-sm gw-muted">共 {{ filtered.length }} 条 · 启用 {{ enabledCount }} 条</span>
          <el-button type="primary" @click="openDialog()">新增规则</el-button>
        </div>

        <el-table v-loading="loading" :data="pagedRows" size="small" border stripe>
          <el-table-column prop="name" label="规则名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="维度" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="dimensionTag(row.dimension)" effect="plain">
                {{ dimensionLabel(row.dimension) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="匹配目标" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="target-code">{{ row.target }}</code>
            </template>
          </el-table-column>
          <el-table-column label="稳态速率" width="110" align="right">
            <template #default="{ row }">{{ row.replenishRate }} 次/秒</template>
          </el-table-column>
          <el-table-column label="桶容量" width="90" align="right">
            <template #default="{ row }">{{ row.burstCapacity }}</template>
          </el-table-column>
          <el-table-column label="每次消耗" width="90" align="right">
            <template #default="{ row }">{{ row.requestedTokens }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
              <el-button link @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && filtered.length === 0" description="还没有限流规则" :image-size="70" />

        <div class="gw-card-ft rate-pager">
          <span class="gw-sm gw-muted">第 {{ page }} / {{ pages }} 页</span>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑限流规则' : '新增限流规则'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="form.name" placeholder="如：订单服务限流" />
        </el-form-item>
        <el-form-item label="限流维度" prop="dimension">
          <el-select v-model="form.dimension" style="width: 100%" @change="onDimensionChange">
            <el-option label="全局（所有请求共享）" value="GLOBAL" />
            <el-option label="按路由（Ant 路径匹配）" value="ROUTE" />
            <el-option label="按 API Key（keyId）" value="API_KEY" />
            <el-option label="按来源 IP" value="IP" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配目标">
          <el-input
            v-model="form.target"
            :placeholder="targetPlaceholder"
          />
          <div class="field-tip">{{ targetTip }}</div>
        </el-form-item>
        <el-form-item label="每秒令牌数" prop="replenishRate">
          <el-input-number v-model="form.replenishRate" :min="1" :max="1000000" :step="10" style="width: 180px" />
          <span class="unit-tip">稳态 QPS</span>
        </el-form-item>
        <el-form-item label="桶容量" prop="burstCapacity">
          <el-input-number v-model="form.burstCapacity" :min="1" :max="1000000" :step="10" style="width: 180px" />
          <span class="unit-tip">允许的突发请求数</span>
        </el-form-item>
        <el-form-item label="每次消耗">
          <el-input-number v-model="form.requestedTokens" :min="1" :max="1000" style="width: 180px" />
          <span class="unit-tip">默认 1，重接口可调大</span>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GwPager } from '@zimo/ui'
import {
  fetchRateLimits,
  createRateLimit,
  updateRateLimit,
  updateRateLimitStatus,
  deleteRateLimit
} from '../api'

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
  dimension: 'GLOBAL',
  target: '*',
  replenishRate: 100,
  burstCapacity: 200,
  requestedTokens: 1,
  status: 1,
  remark: ''
})

const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  dimension: [{ required: true, message: '请选择限流维度', trigger: 'change' }],
  replenishRate: [{ required: true, message: '请输入每秒令牌数', trigger: 'blur' }],
  burstCapacity: [{ required: true, message: '请输入桶容量', trigger: 'blur' }]
}

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter((r) =>
    [r.name, r.target, r.remark, r.dimension]
      .some((f) => String(f ?? '').toLowerCase().includes(k))
  )
})

const enabledCount = computed(() => rows.value.filter((r) => r.status === 1).length)
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / size.value)))
const pagedRows = computed(() =>
  filtered.value.slice((page.value - 1) * size.value, page.value * size.value)
)

const dimensionLabel = (d) =>
  ({ GLOBAL: '全局', ROUTE: '路由', API_KEY: 'API Key', IP: 'IP' }[d] || d)

const dimensionTag = (d) =>
  ({ GLOBAL: 'danger', ROUTE: 'warning', API_KEY: 'primary', IP: 'info' }[d] || 'info')

const targetPlaceholder = computed(() =>
  form.dimension === 'ROUTE'
    ? '如 /gw-demo/**（Ant 风格）'
    : form.dimension === 'API_KEY'
      ? 'keyId，留空或 * 表示对所有 Key 分别限流'
      : form.dimension === 'IP'
        ? '具体 IP，留空或 * 表示对所有来源 IP 分别限流'
        : '*（全局规则无需指定目标）'
)

const targetTip = computed(() => {
  if (form.dimension === 'ROUTE') return '按请求路径匹配，需以 / 开头；同一路由的请求共享一个令牌桶'
  if (form.dimension === 'API_KEY') return '填 * 时每个 Key 独立计数；指定 keyId 只对该 Key 生效'
  if (form.dimension === 'IP') return '填 * 时每个来源 IP 独立计数；指定 IP 只对该地址生效'
  return '全局规则对所有经过网关的请求生效，目标固定为 *'
})

const onDimensionChange = () => {
  if (form.dimension !== 'ROUTE' && !form.target) form.target = '*'
  if (form.dimension === 'ROUTE' && (form.target === '*' || !form.target)) form.target = ''
}

const load = async () => {
  loading.value = true
  try {
    rows.value = (await fetchRateLimits()) || []
    if (page.value > pages.value) page.value = pages.value
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

const onSizeChange = (s) => {
  size.value = s
  page.value = 1
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
    submitting.value = true
    try {
      const payload = {
        name: form.name,
        dimension: form.dimension,
        target: form.target || '*',
        replenishRate: form.replenishRate,
        burstCapacity: form.burstCapacity,
        requestedTokens: form.requestedTokens,
        status: form.status,
        remark: form.remark
      }
      if (form.id) {
        await updateRateLimit(form.id, payload)
        ElMessage.success('规则已更新，5 秒内生效')
      } else {
        await createRateLimit(payload)
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
  await updateRateLimitStatus(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await load()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除限流规则「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteRateLimit(row.id)
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

.target-code {
  font-family: var(--gw-font-mono, monospace);
  font-size: 12px;
  background: var(--gw-bg-soft, #f5f7fa);
  padding: 1px 6px;
  border-radius: 4px;
}

.rate-pager {
  margin: 12px -16px -12px;
  border-top: 1px solid var(--gw-border-light);
}

.field-tip {
  font-size: 12px;
  color: var(--gw-text-3);
  line-height: 1.6;
  margin-top: 2px;
}

.unit-tip {
  font-size: 12px;
  color: var(--gw-text-3);
  margin-left: 10px;
}
</style>
