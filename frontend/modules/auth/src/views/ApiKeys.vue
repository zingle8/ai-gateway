<template>
  <div class="page">
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
  fetchApiKeys, issueApiKey, setApiKeyStatus, rotateApiKey, revokeApiKey
} from '../api.js'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
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
    rows.value = await fetchApiKeys()
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
