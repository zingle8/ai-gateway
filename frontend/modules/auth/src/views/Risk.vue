<template>
  <div class="page">
    <!-- 概览：今日事件 / 待处置 / 高风险 / 今日失败登录 -->
    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value" :class="s.tone">{{ s.value }}</div>
        <div class="stat-hint">{{ s.hint }}</div>
      </div>
    </div>

    <PaneTabs v-model="tab" :tabs="tabs">
      <!-- 风险事件 -->
      <template #events>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>风控事件</span>
            <div class="hd-actions">
              <el-select v-model="eventFilter.subjectType" placeholder="全部主体" clearable size="small"
                         style="width: 120px" @change="loadEvents(1)">
                <el-option label="用户" value="USER" />
                <el-option label="API Key" value="API_KEY" />
              </el-select>
              <el-select v-model="eventFilter.status" placeholder="全部状态" clearable size="small"
                         style="width: 120px" @change="loadEvents(1)">
                <el-option label="待处置" value="OPEN" />
                <el-option label="已处置" value="HANDLED" />
                <el-option label="已忽略" value="IGNORED" />
              </el-select>
              <el-select v-model="eventFilter.riskLevel" placeholder="全部等级" clearable size="small"
                         style="width: 120px" @change="loadEvents(1)">
                <el-option label="高" value="HIGH" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="低" value="LOW" />
              </el-select>
              <el-input v-model="eventFilter.keyword" placeholder="主体 / 类型" clearable size="small"
                        style="width: 160px" @keyup.enter="loadEvents(1)" @clear="loadEvents(1)" />
              <el-button size="small" @click="loadEvents(1)">查询</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="eventRows" size="small" v-loading="eventLoading" style="width: 100%">
              <el-table-column prop="createTime" label="时间" width="160">
                <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="主体" min-width="160">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="row.subjectType === 'USER' ? 'primary' : 'success'">
                    {{ row.subjectType === 'USER' ? '用户' : 'Key' }}
                  </el-tag>
                  <span class="subject-name">{{ row.subjectName || row.subjectId }}</span>
                </template>
              </el-table-column>
              <el-table-column label="风险类型" width="150">
                <template #default="{ row }">{{ typeLabel(row.riskType) }}</template>
              </el-table-column>
              <el-table-column label="等级" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="levelTag(row.riskLevel)" effect="dark">{{ levelLabel(row.riskLevel) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'OPEN' ? 'warning' : 'info'" effect="plain">
                    {{ statusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="处置" min-width="180">
                <template #default="{ row }">
                  <span v-if="row.status === 'OPEN'" class="gw-sm gw-muted">—</span>
                  <span v-else class="gw-sm">
                    {{ actionLabel(row.action) }} · {{ row.handler }}<br />
                    <span class="gw-muted">{{ fmtTime(row.handleTime) }}</span>
                    <span v-if="row.handleRemark" class="gw-muted"> · {{ row.handleRemark }}</span>
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'OPEN'" link type="primary" @click="openHandle(row)">处置</el-button>
                  <span v-else class="gw-sm gw-muted">已处理</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">
              共 {{ eventTotal }} 条 · 同一主体同类事件 10 分钟内去重 · 处置动作与操作人留在事件行上
            </span>
            <GwPager :total="eventTotal" :current="eventPage" :size="eventSize"
                     @page-change="loadEvents" @size-change="onEventSize" />
          </div>
        </section>
      </template>

      <!-- 登录历史 -->
      <template #logins>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>登录历史</span>
            <div class="hd-actions">
              <el-input v-model="loginFilter.username" placeholder="账号" clearable size="small"
                        style="width: 150px" @keyup.enter="loadLogins(1)" @clear="loadLogins(1)" />
              <el-select v-model="loginFilter.success" placeholder="全部结果" clearable size="small"
                         style="width: 120px" @change="loadLogins(1)">
                <el-option label="成功" :value="1" />
                <el-option label="失败" :value="0" />
              </el-select>
              <el-checkbox v-model="loginFilter.riskOnly" size="small" @change="loadLogins(1)">仅看风险</el-checkbox>
              <el-button size="small" @click="loadLogins(1)">查询</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="loginRows" size="small" v-loading="loginLoading" style="width: 100%">
              <el-table-column prop="loginTime" label="时间" width="160">
                <template #default="{ row }">{{ fmtTime(row.loginTime) }}</template>
              </el-table-column>
              <el-table-column prop="username" label="账号" width="120" />
              <el-table-column label="结果" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'" effect="plain">
                    {{ row.success === 1 ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="ip" label="来源 IP" width="130" />
              <el-table-column prop="appId" label="平台" width="110" />
              <el-table-column prop="failReason" label="失败原因" min-width="140" show-overflow-tooltip />
              <el-table-column label="风险标记" min-width="220">
                <template #default="{ row }">
                  <span v-if="!row.riskType" class="gw-sm gw-muted">—</span>
                  <span v-else>
                    <el-tag size="small" :type="levelTag(row.riskLevel)" effect="plain">
                      {{ levelLabel(row.riskLevel) }}
                    </el-tag>
                    <span class="gw-sm risk-reason">{{ row.riskReason }}</span>
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">共 {{ loginTotal }} 条 · 陌生 IP 按该账号历史成功登录 IP 判定，非工作时段为 22:00–07:00</span>
            <GwPager :total="loginTotal" :current="loginPage" :size="loginSize"
                     @page-change="loadLogins" @size-change="onLoginSize" />
          </div>
        </section>
      </template>

      <!-- Key 预警 -->
      <template #keys>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>API Key 预警</span>
            <span class="gw-sm gw-muted">临期（≤7 天，含已过期）与长期未用（&gt;30 天未调用）</span>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="warnings" size="small" v-loading="warningLoading" style="width: 100%">
              <el-table-column prop="name" label="名称" min-width="160" />
              <el-table-column label="keyId" width="130">
                <template #default="{ row }"><span class="gw-mono gw-sm">{{ row.keyId }}</span></template>
              </el-table-column>
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.type === 'UNUSED' ? 'info' : 'warning'" effect="plain">
                    {{ row.type === 'EXPIRED' ? '已过期' : row.type === 'EXPIRING' ? '临期' : '长期未用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="expireAt" label="有效期至" width="170">
                <template #default="{ row }">
                  <span v-if="!row.expireAt" class="gw-muted">永久</span>
                  <span v-else>{{ fmtTime(row.expireAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="lastUsedAt" label="最近使用" width="170">
                <template #default="{ row }">
                  <span>{{ row.lastUsedAt ? fmtTime(row.lastUsedAt) : '从未使用' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="lastUsedIp" label="最近来源 IP" width="140">
                <template #default="{ row }">{{ row.lastUsedIp || '-' }}</template>
              </el-table-column>
              <el-table-column prop="suggestion" label="说明" min-width="200" show-overflow-tooltip />
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">
              共 {{ warnings.length }} 个 Key 需要关注 · 预警只提示不自动处置，续期 / 吊销请在「API Key」页操作
            </span>
          </div>
        </section>
      </template>
    </PaneTabs>

    <!-- 处置弹窗 -->
    <el-dialog v-model="handleDialog" title="处置风控事件" width="480px">
      <div v-if="current" class="handle-target">
        <div><b>{{ current.subjectName || current.subjectId }}</b>
          <el-tag size="small" effect="plain" style="margin-left: 6px">
            {{ current.subjectType === 'USER' ? '用户' : 'API Key' }}
          </el-tag>
        </div>
        <div class="gw-sm gw-muted">{{ typeLabel(current.riskType) }} · {{ current.detail }}</div>
      </div>
      <el-form label-width="80px" size="small" style="margin-top: 12px">
        <el-form-item label="处置动作">
          <el-select v-model="handleForm.action" style="width: 100%">
            <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handleForm.remark" type="textarea" :rows="2" placeholder="处置说明（可选，落事件留痕）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialog = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">确认处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { PaneTabs, GwPager } from '@zimo/ui'
import {
  fetchRiskSummary, fetchRiskEvents, handleRiskEvent, fetchLoginLogs, fetchKeyWarnings
} from '../api'

const tab = ref('events')
const tabs = computed(() => ([
  { name: 'events', label: '风险事件', badge: summary.openEvents > 0 ? summary.openEvents : null },
  { name: 'logins', label: '登录历史' },
  { name: 'keys', label: 'Key 预警', badge: summary.keyWarnings > 0 ? summary.keyWarnings : null }
]))

const summary = reactive({
  todayEvents: 0, openEvents: 0, highToday: 0, failLoginsToday: 0, keyWarnings: 0
})

const statCards = computed(() => ([
  { label: '今日事件', value: summary.todayEvents, hint: '今天新登记的风控事件', tone: '' },
  { label: '待处置', value: summary.openEvents, hint: '状态 OPEN，需要人工确认', tone: summary.openEvents > 0 ? 'warn' : '' },
  { label: '高风险', value: summary.highToday, hint: '今日 HIGH 等级事件', tone: summary.highToday > 0 ? 'danger' : '' },
  { label: '今日失败登录', value: summary.failLoginsToday, hint: '连续失败达阈值会锁定账号', tone: '' },
  { label: 'Key 预警', value: summary.keyWarnings, hint: '临期 / 已过期 / 长期未用', tone: summary.keyWarnings > 0 ? 'warn' : '' }
]))

/* ------------------------------------------------------- 风险事件 */

const eventRows = ref([])
const eventTotal = ref(0)
const eventPage = ref(1)
const eventSize = ref(10)
const eventLoading = ref(false)
const eventFilter = reactive({ subjectType: '', status: '', riskLevel: '', keyword: '' })

const loadEvents = async (page = eventPage.value) => {
  eventLoading.value = true
  try {
    const d = await fetchRiskEvents({
      subjectType: eventFilter.subjectType || undefined,
      status: eventFilter.status || undefined,
      riskLevel: eventFilter.riskLevel || undefined,
      keyword: eventFilter.keyword || undefined,
      page,
      size: eventSize.value
    })
    eventRows.value = d.records || []
    eventTotal.value = d.total || 0
    eventPage.value = page
  } finally {
    eventLoading.value = false
  }
}
const onEventSize = (s) => {
  eventSize.value = s
  loadEvents(1)
}

/* ------------------------------------------------------- 登录历史 */

const loginRows = ref([])
const loginTotal = ref(0)
const loginPage = ref(1)
const loginSize = ref(10)
const loginLoading = ref(false)
const loginFilter = reactive({ username: '', success: '', riskOnly: false })

const loadLogins = async (page = loginPage.value) => {
  loginLoading.value = true
  try {
    const d = await fetchLoginLogs({
      username: loginFilter.username || undefined,
      success: loginFilter.success === '' ? undefined : loginFilter.success,
      riskOnly: loginFilter.riskOnly || undefined,
      page,
      size: loginSize.value
    })
    loginRows.value = d.records || []
    loginTotal.value = d.total || 0
    loginPage.value = page
  } finally {
    loginLoading.value = false
  }
}
const onLoginSize = (s) => {
  loginSize.value = s
  loadLogins(1)
}

/* ------------------------------------------------------- Key 预警 */

const warnings = ref([])
const warningLoading = ref(false)

const loadWarnings = async () => {
  warningLoading.value = true
  try {
    warnings.value = (await fetchKeyWarnings()) || []
  } finally {
    warningLoading.value = false
  }
}

/* ------------------------------------------------------- 概览 */

const loadSummary = async () => {
  const d = await fetchRiskSummary().catch(() => null)
  if (d) {
    Object.assign(summary, {
      todayEvents: d.todayEvents || 0,
      openEvents: d.openEvents || 0,
      highToday: d.highToday || 0,
      failLoginsToday: d.failLoginsToday || 0,
      keyWarnings: d.keyWarnings || 0
    })
  }
}

watch(tab, (v) => {
  if (v === 'logins' && !loginRows.value.length) loadLogins(1)
  if (v === 'keys' && !warnings.value.length) loadWarnings()
})

/* ------------------------------------------------------- 处置 */

const handleDialog = ref(false)
const handling = ref(false)
const current = ref(null)
const handleForm = reactive({ action: 'ACK', remark: '' })

const actionOptions = computed(() => current.value?.subjectType === 'API_KEY'
  ? [
      { value: 'ACK', label: '仅确认（不改状态）' },
      { value: 'DISABLE_KEY', label: '停用该 Key' },
      { value: 'ENABLE_KEY', label: '恢复启用该 Key' },
      { value: 'IGNORE', label: '忽略该事件' }
    ]
  : [
      { value: 'ACK', label: '仅确认（不改状态）' },
      { value: 'UNLOCK_USER', label: '解锁登录（清除失败计数）' },
      { value: 'KICK_USER', label: '强制下线（保留账号）' },
      { value: 'DISABLE_USER', label: '禁用账号并下线' },
      { value: 'ENABLE_USER', label: '恢复启用账号' },
      { value: 'IGNORE', label: '忽略该事件' }
    ])

const openHandle = (row) => {
  current.value = row
  handleForm.action = 'ACK'
  handleForm.remark = ''
  handleDialog.value = true
}

const submitHandle = async () => {
  handling.value = true
  try {
    await handleRiskEvent(current.value.id, { action: handleForm.action, remark: handleForm.remark })
    ElMessage.success('已处置')
    handleDialog.value = false
    loadEvents()
    loadSummary()
  } catch (e) { /* 全局拦截器已提示 */ } finally {
    handling.value = false
  }
}

/* ------------------------------------------------------- 展示映射 */

const TYPE_LABELS = {
  LOGIN_NEW_IP: '陌生 IP 登录',
  LOGIN_OFF_HOURS: '非工作时段登录',
  LOGIN_BRUTE_FORCE: '连续失败锁定',
  KEY_SPIKE: '调用量突增',
  KEY_ERROR_RATE: '错误率异常',
  KEY_EXPIRING: 'Key 临期',
  KEY_UNUSED: 'Key 长期未用'
}
const ACTION_LABELS = {
  ACK: '已确认',
  IGNORE: '已忽略',
  DISABLE_USER: '禁用账号',
  ENABLE_USER: '启用账号',
  UNLOCK_USER: '解锁登录',
  KICK_USER: '强制下线',
  DISABLE_KEY: '停用 Key',
  ENABLE_KEY: '恢复 Key'
}

const typeLabel = (t) => TYPE_LABELS[t] || t || '-'
const actionLabel = (a) => ACTION_LABELS[a] || a || '-'
const levelLabel = (l) => (l === 'HIGH' ? '高' : l === 'MEDIUM' ? '中' : l === 'LOW' ? '低' : '-')
const levelTag = (l) => (l === 'HIGH' ? 'danger' : l === 'MEDIUM' ? 'warning' : 'info')
const statusLabel = (s) => (s === 'OPEN' ? '待处置' : s === 'HANDLED' ? '已处置' : '已忽略')
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

onMounted(() => {
  loadSummary()
  loadEvents(1)
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
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

.stat-hint {
  font-size: 12px;
  color: var(--gw-text-tertiary);
}

@media (max-width: 1400px) {
  .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 900px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.hd-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.subject-name {
  margin-left: 6px;
}

.risk-reason {
  margin-left: 6px;
}

.handle-target {
  background: var(--gw-bg-muted, #f7f8fa);
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value.warn { color: var(--gw-warning); }
.stat-value.danger { color: var(--gw-danger); }
</style>
