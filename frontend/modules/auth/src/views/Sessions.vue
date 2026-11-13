<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="success" effect="plain">已接真</el-tag>
      <span class="gw-sm gw-muted">
        登录即登记会话（Redis，含 IP / 设备 / 端类型 / 平台）；踢人支持单会话、按平台、按端、
        按设备类型（手机 / 平板 / 电脑 / 服务端）、全端五种粒度，被踢令牌在网关下一次校验即失效。
      </span>
    </p>

    <!-- 概览指标对两个 Tab 都成立，放在 Tab 外 -->
    <div class="gw-stats">
      <div v-for="s in stats" :key="s.label" class="gw-stat">
        <div>
          <div class="gw-stat-label">{{ s.label }}</div>
          <div class="gw-stat-value">{{ s.value }}</div>
          <div class="gw-stat-trend gw-muted">{{ s.hint }}</div>
        </div>
        <span class="gw-icon" :class="s.tone"><el-icon><component :is="s.icon" /></el-icon></span>
      </div>
    </div>

    <!-- 在线会话 / 吊销记录按 Tab 分开 -->
    <PaneTabs v-model="tab" :tabs="tabs">
      <template #sessions>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>在线会话</span>
            <div class="hd-actions">
              <el-select v-model="deviceTypeFilter" placeholder="全部设备" size="small" clearable style="width: 120px">
                <el-option v-for="t in DEVICE_TYPES" :key="t.value" :label="`${t.label}（${typeCount(t.value)}）`" :value="t.value" />
                <!-- 筛选下拉显示全局在线数 -->
              </el-select>
              <el-input
                v-model="keyword"
                placeholder="账号 / IP / 设备"
                clearable
                size="small"
                style="width: 200px"
              />
              <el-button size="small" :loading="loading" @click="load">刷新</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table v-loading="loading" :data="pagedSessions" size="small" style="width: 100%">
              <el-table-column label="账号" width="150">
                <template #default="{ row }">
                  <span class="acct">{{ row.username }}</span>
                  <span v-if="row.nickname" class="gw-sm gw-muted"> {{ row.nickname }}</span>
                </template>
              </el-table-column>
              <el-table-column label="平台" width="120">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="row.appId === 'console' ? 'info' : 'warning'">
                    {{ row.appId }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="device" label="设备 / 浏览器" min-width="160" show-overflow-tooltip />
              <el-table-column label="端" width="90">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="TYPE_TAG[row.deviceType]?.tag || 'info'">
                    <el-icon style="vertical-align: -2px"><component :is="TYPE_TAG[row.deviceType]?.icon || 'Monitor'" /></el-icon>
                    {{ TYPE_TAG[row.deviceType]?.label || row.deviceType || '未知' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="ip" label="IP" width="130">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.ip || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="登录时间" width="160">
                <template #default="{ row }">
                  <span class="gw-sm">{{ fmtTime(row.loginTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="在线时长" width="110">
                <template #default="{ row }">
                  <span class="gw-sm gw-muted">{{ onlineText(row.onlineSec) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" @click="kick(row, 'SESSION')">踢下线</el-button>
                  <el-dropdown trigger="click" @command="(cmd) => kick(row, cmd)">
                    <el-button link type="primary">
                      更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="DEVICE">踢该端（{{ row.device }}）</el-dropdown-item>
                        <el-dropdown-item
                          v-for="t in DEVICE_TYPES.filter((x) => x.value !== row.deviceType)"
                          :key="t.value"
                          :command="`TYPE:${t.value}`"
                        >
                          踢{{ t.label === '服务端' ? '服务端' : t.label + '端' }}（{{ typeCount(t.value, row.userId) }} 个在线）
                        </el-dropdown-item>
                        <el-dropdown-item command="PLATFORM">踢该平台（{{ row.appId }}）</el-dropdown-item>
                        <el-dropdown-item command="ALL" divided>全端踢（{{ row.username }}）</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!loading && filteredSessions.length === 0" description="当前没有在线会话" :image-size="70" />
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">共 {{ filteredSessions.length }} 个在线会话 · 单会话 / 按平台 / 按端踢 = 吊销该会话；全端踢 = 令牌版本 +1</span>
            <GwPager
              :total="filteredSessions.length"
              :current="sessionPage"
              :size="sessionSize"
              @page-change="onSessionPage"
              @size-change="onSessionSize"
            />
          </div>
        </section>
      </template>

      <template #revokes>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>令牌吊销记录</span>
            <div class="hd-actions">
              <span class="gw-sm gw-muted">最近 {{ revokes.length }} 条</span>
              <el-button size="small" :loading="loading" @click="load">刷新</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table v-loading="loading" :data="pagedRevokes" size="small" style="width: 100%" max-height="420">
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ fmtTime(row.revokeTime) }}</template>
              </el-table-column>
              <el-table-column prop="username" label="账号" width="140" />
              <el-table-column label="平台" width="110">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" type="info">{{ row.appId }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="device" label="设备" width="150" show-overflow-tooltip />
              <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
              <el-table-column prop="operator" label="操作人" width="130">
                <template #default="{ row }">{{ row.operator || 'system' }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">共 {{ revokes.length }} 条吊销记录</span>
            <GwPager
              :total="revokes.length"
              :current="revokePage"
              :size="revokeSize"
              @page-change="onRevokePage"
              @size-change="onRevokeSize"
            />
          </div>
        </section>
      </template>
    </PaneTabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Cellphone, Iphone, Monitor, Cpu } from '@element-plus/icons-vue'
import { GwPager, PaneTabs } from '@zimo/ui'
import { fetchSessions, fetchSessionRevokes, kickSessions } from '../api'

const loading = ref(false)
const keyword = ref('')
const deviceTypeFilter = ref('')
const sessions = ref([])
const revokes = ref([])

/** 设备端类型（与后端 parseDeviceType 对齐） */
const DEVICE_TYPES = [
  { value: 'PHONE', label: '手机', icon: Cellphone },
  { value: 'TABLET', label: '平板', icon: Iphone },
  { value: 'PC', label: '电脑', icon: Monitor },
  { value: 'SERVER', label: '服务端', icon: Cpu }
]
const TYPE_TAG = Object.fromEntries(DEVICE_TYPES.map((t) => [
  t.value,
  { label: t.label, icon: t.icon, tag: t.value === 'SERVER' ? 'warning' : t.value === 'PC' ? 'primary' : 'success' }
]))

const typeCount = (type, userId) =>
  sessions.value.filter((s) => s.deviceType === type && (userId == null || s.userId === userId)).length

const stats = computed(() => [
  { label: '在线会话', value: String(sessions.value.length), hint: '登录登记且未下线的会话', icon: 'User', tone: 'info' },
  {
    label: '在线账号',
    value: String(new Set(sessions.value.map((s) => s.userId)).size),
    hint: '当前有活跃会话的账号数',
    icon: 'UserFilled',
    tone: 'success'
  },
  {
    label: '接入平台',
    value: String(new Set(sessions.value.map((s) => s.appId)).size),
    hint: '控制台 + 开放认证各接入方',
    icon: 'Link',
    tone: 'purple'
  },
  { label: '吊销记录', value: String(revokes.value.length), hint: '最近 200 条内的下线操作', icon: 'Lock', tone: 'warning' }
])

/** 页内 Tab：在线会话 / 吊销记录，badge 跟随实际条数 */
const tab = ref('sessions')
const tabs = computed(() => [
  { name: 'sessions', label: '在线会话', badge: sessions.value.length },
  { name: 'revokes', label: '令牌吊销记录', badge: revokes.value.length }
])

const filteredSessions = computed(() => {
  let list = sessions.value
  if (deviceTypeFilter.value) {
    list = list.filter((s) => s.deviceType === deviceTypeFilter.value)
  }
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list
  return list.filter((s) =>
    [s.username, s.nickname, s.ip, s.device, s.appId]
      .some((f) => String(f ?? '').toLowerCase().includes(k))
  )
})

/** 分页：在线会话 / 吊销记录各自独立 */
const sessionPage = ref(1)
const sessionSize = ref(10)
const pagedSessions = computed(() =>
  filteredSessions.value.slice((sessionPage.value - 1) * sessionSize.value, sessionPage.value * sessionSize.value)
)
const onSessionPage = (p) => (sessionPage.value = p)
const onSessionSize = (s) => {
  sessionSize.value = s
  sessionPage.value = 1
}

const revokePage = ref(1)
const revokeSize = ref(10)
const pagedRevokes = computed(() =>
  revokes.value.slice((revokePage.value - 1) * revokeSize.value, revokePage.value * revokeSize.value)
)
const onRevokePage = (p) => (revokePage.value = p)
const onRevokeSize = (s) => {
  revokeSize.value = s
  revokePage.value = 1
}

/** 关键词筛选后回到第 1 页，避免停在越界页码 */
watch(keyword, () => {
  sessionPage.value = 1
})

const fmtTime = (ms) => {
  if (!ms) return '-'
  const d = new Date(Number(ms))
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const onlineText = (sec) => {
  const s = Number(sec) || 0
  if (s < 60) return `${s} 秒`
  if (s < 3600) return `${Math.floor(s / 60)} 分钟`
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  return m ? `${h} 小时 ${m} 分` : `${h} 小时`
}

const load = async () => {
  loading.value = true
  try {
    const [list, logs] = await Promise.all([fetchSessions(), fetchSessionRevokes(100)])
    sessions.value = list || []
    revokes.value = logs || []
    if (sessionPage.value > Math.ceil(filteredSessions.value.length / sessionSize.value) && sessionPage.value > 1) {
      sessionPage.value = 1
    }
  } catch (e) {
    sessions.value = []
    revokes.value = []
  } finally {
    loading.value = false
  }
}

const SCOPE_TEXT = {
  SESSION: '单会话',
  PLATFORM: '按平台',
  DEVICE: '按端',
  ALL: '全端'
}

const kick = async (row, scope) => {
  // scope 支持 "TYPE:PHONE" 形式（按设备类型踢）
  let realScope = scope
  let deviceType
  let scopeText = SCOPE_TEXT[scope]
  if (scope?.startsWith('TYPE:')) {
    deviceType = scope.slice(5)
    realScope = 'DEVICE_TYPE'
    scopeText = `按设备类型（${TYPE_TAG[deviceType]?.label || deviceType}）`
  }
  const typeLabel = TYPE_TAG[row.deviceType]?.label || row.deviceType
  const tip = realScope === 'SESSION'
    ? `确认让「${row.username}」在 ${row.device}（${row.ip}）上的本次登录立即失效？`
    : realScope === 'DEVICE'
      ? `确认踢掉「${row.username}」所有 ${row.device} 设备上的会话？`
      : realScope === 'DEVICE_TYPE'
        ? `确认踢掉「${row.username}」全部${typeLabel}端会话（${typeCount(row.deviceType)} 个在线）？其他设备类型不受影响。`
        : realScope === 'PLATFORM'
          ? `确认踢掉「${row.username}」在平台 ${row.appId} 的全部会话（其他平台不受影响）？`
          : `确认全端踢下线「${row.username}」？该账号所有令牌立即失效，需要重新登录。`
  await ElMessageBox.confirm(tip, `${scopeText}下线`, { type: 'warning' })
  const res = await kickSessions({
    scope: realScope,
    sid: realScope === 'SESSION' ? row.sid : undefined,
    userId: row.userId,
    appId: realScope === 'PLATFORM' ? row.appId : undefined,
    device: realScope === 'DEVICE' ? row.device : undefined,
    deviceType: realScope === 'DEVICE_TYPE' ? deviceType : undefined,
    reason: `控制台手动${scopeText}踢下线`
  })
  ElMessage.success(`已下线 ${res?.affected ?? 0} 个会话`)
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

.acct {
  font-weight: 500;
}
</style>
