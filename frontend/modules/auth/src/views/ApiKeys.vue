<template>
  <div class="page">
    <!-- 统计看板：总量 + 今日动作（数据源 tb_gw_api_key_log 审计表） -->
    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value" :class="s.tone">{{ s.value }}</div>
        <div class="stat-hint">{{ s.hint }}</div>
      </div>
    </div>

    <section class="gw-card">
      <div class="gw-card-hd">
        <span>API Key</span>
        <div class="hd-actions">
          <el-input v-model="keyword" placeholder="名称 / keyId" clearable size="small" style="width: 180px" />
          <el-button size="small" type="primary" @click="issueDialog = true">签发 API Key</el-button>
        </div>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table v-loading="loading" :data="filtered" size="small" style="width: 100%">
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column label="keyId" width="130">
            <template #default="{ row }">
              <span class="gw-mono gw-sm">{{ row.keyId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="调用方式" min-width="230">
            <template #default="{ row }">
              <span class="gw-mono gw-sm gw-muted">X-Api-Key: gwak-{{ row.keyId }}-****</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 1"
                size="small"
                @change="(v) => toggleStatus(row, v)"
              />
            </template>
          </el-table-column>
          <el-table-column label="有效期至" width="120">
            <template #default="{ row }">
              <span :class="{ 'gw-danger': expiring(row) }">{{ row.expireAt || '永久' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastUsedAt" label="最近使用" width="170">
            <template #default="{ row }">
              <span>{{ row.lastUsedAt || '从未使用' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="rotate(row)">轮换密钥</el-button>
              <el-button link type="danger" @click="remove(row)">吊销</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="gw-card-ft">
        <span class="gw-sm gw-muted">
          明文只在签发 / 轮换时返回一次，服务端仅存 SHA-256；网关校验失败 / 停用 / 过期一律 401，缓存 30 秒后失效
        </span>
      </div>
    </section>

    <!-- 签发 -->
    <el-dialog v-model="issueDialog" title="签发 API Key" width="460px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="Key 名称">
          <el-input v-model="form.name" placeholder="如：订单系统调用" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="form.expireDays" style="width: 100%">
            <el-option label="永久" :value="0" />
            <el-option label="30 天" :value="30" />
            <el-option label="90 天" :value="90" />
            <el-option label="365 天" :value="365" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="issue">签发</el-button>
      </template>
    </el-dialog>

    <!-- 明文一次性展示 -->
    <el-dialog v-model="secretDialog" :title="secretTitle" width="560px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="完整 Key 只显示这一次，请立即复制保存；服务端仅存哈希，丢失只能轮换或重新签发"
        class="secret-alert"
      />
      <div class="secret-box">
        <span class="gw-mono">{{ created?.fullKey }}</span>
        <el-button size="small" type="primary" plain @click="copyKey">复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretDialog = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchApiKeys, fetchApiKeyStats, issueApiKey, setApiKeyStatus, rotateApiKey, revokeApiKey
} from '../api.js'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const stats = ref(null)

/** 统计卡片：总量 + 今日动作（来源 tb_gw_api_key_log 审计表，删除/吊销也能统计到） */
const statCards = computed(() => {
  const s = stats.value || {}
  return [
    { label: 'Key 总数', value: s.total ?? 0, hint: `启用 ${s.enabled ?? 0} · 停用 ${s.disabled ?? 0}`, tone: '' },
    { label: '今日新增', value: s.todayCreated ?? 0, hint: '签发 CREATE', tone: 'is-green' },
    { label: '今日轮换', value: s.todayRotated ?? 0, hint: '更换密钥 ROTATE', tone: 'is-blue' },
    { label: '今日吊销', value: s.todayRevoked ?? 0, hint: '停用 DISABLE', tone: 'is-orange' },
    { label: '今日删除', value: s.todayDeleted ?? 0, hint: '删除 DELETE', tone: 'is-red' },
    { label: '7 日内到期', value: s.expiringIn7Days ?? 0, hint: '建议提前轮换', tone: 'is-orange' }
  ]
})
const keyword = ref('')
const issueDialog = ref(false)
const secretDialog = ref(false)
const secretTitle = ref('API Key 签发成功')
const created = ref(null)
const form = reactive({ name: '', expireDays: 90 })

const filtered = computed(() => {
  const k = keyword.value.trim()
  if (!k) return rows.value
  return rows.value.filter((r) => r.name.includes(k) || r.keyId.includes(k))
})

const expiring = (row) => row.expireAt && new Date(row.expireAt.replace(' ', 'T')).getTime() - Date.now() < 7 * 86400_000

const load = async () => {
  loading.value = true
  try {
    const [list, stat] = await Promise.all([
      fetchApiKeys(),
      fetchApiKeyStats().catch(() => null)
    ])
    rows.value = list
    stats.value = stat
  } finally {
    loading.value = false
  }
}

const showSecret = (vo, title) => {
  created.value = vo
  secretTitle.value = title
  secretDialog.value = true
}

const copyKey = async () => {
  await navigator.clipboard.writeText(created.value?.fullKey || '')
  ElMessage.success('已复制到剪贴板')
}

const issue = async () => {
  if (!form.name) {
    ElMessage.warning('请填写 Key 名称')
    return
  }
  saving.value = true
  try {
    const vo = await issueApiKey({ name: form.name, expireDays: form.expireDays })
    issueDialog.value = false
    showSecret(vo, 'API Key 签发成功')
    await load()
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row, enabled) => {
  await setApiKeyStatus(row.id, enabled ? 1 : 0)
  row.status = enabled ? 1 : 0
  ElMessage.success(`「${row.name}」已${enabled ? '启用' : '停用'}（网关 30 秒内生效）`)
}

const rotate = async (row) => {
  await ElMessageBox.confirm(
    `确认轮换「${row.name}」的密钥？旧 Key 将立即失效（缓存 30 秒），使用方需更新。`,
    '轮换密钥',
    { type: 'warning' }
  )
  const vo = await rotateApiKey(row.id)
  showSecret(vo, `「${row.name}」密钥已轮换`)
}

const remove = async (row) => {
  await ElMessageBox.confirm(
    `确认吊销并删除「${row.name}」？该 Key 将无法再调用网关。`,
    '吊销 API Key',
    { type: 'warning' }
  )
  await revokeApiKey(row.id)
  ElMessage.success('已吊销')
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 统计看板 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.stat-card {
  padding: 12px 14px;
  background: var(--gw-card);
  border: 1px solid var(--gw-border);
  border-radius: 8px;
}

.stat-label {
  font-size: 12px;
  color: var(--gw-text-tertiary);
}

.stat-value {
  margin: 6px 0 2px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat-value.is-green { color: #16A34A; }
.stat-value.is-blue { color: #2563EB; }
.stat-value.is-orange { color: #D97706; }
.stat-value.is-red { color: #DC2626; }

.stat-hint {
  font-size: 11px;
  color: var(--gw-text-tertiary);
}

@media (max-width: 1280px) {
  .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

.hd-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gw-danger { color: var(--gw-danger); }

.secret-alert { margin-bottom: 14px; }

.secret-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: var(--gw-border-light);
  border-radius: 6px;
  word-break: break-all;
  font-size: 13px;
}

.secret-box .gw-mono { flex: 1; }
</style>
