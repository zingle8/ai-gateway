<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="info" effect="plain">设计稿</el-tag>
      <span class="gw-sm gw-muted">会话数据为示例；后端 /auth/sessions 接口待接入。</span>
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
              <el-input v-model="keyword" placeholder="账号 / IP" clearable size="small" style="width: 170px" />
              <el-button size="small" @click="revokeAll">全部强制下线</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="filteredSessions" size="small" style="width: 100%">
              <el-table-column prop="account" label="账号" width="130" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="row.userType === 'CONSUMER' ? 'warning' : 'info'">
                    {{ row.userType === 'CONSUMER' ? 'C 端' : '控制台' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="method" label="登录方式" width="130">
                <template #default="{ row }">
                  <span class="gw-sm">{{ row.method }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="device" label="设备 / 浏览器" min-width="170" show-overflow-tooltip />
              <el-table-column prop="ip" label="IP" width="130">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.ip }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="loginAt" label="登录时间" width="160" />
              <el-table-column label="Access 到期" width="150">
                <template #default="{ row }">
                  <span class="gw-sm" :class="row.expiringSoon ? 'warn' : 'gw-muted'">{{ row.accessExpire }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" @click="revoke(row)">强制下线</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">
              强制下线 = 令牌版本 +1，该会话的 access 立即失效（无需等自然过期）
            </span>
          </div>
        </section>
      </template>

      <template #revokes>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>令牌吊销记录</span>
            <span class="gw-sm gw-muted">最近 {{ revokes.length }} 条</span>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="revokes" size="small" style="width: 100%" max-height="420">
              <el-table-column prop="time" label="时间" width="170" />
              <el-table-column prop="account" label="账号" width="140" />
              <el-table-column prop="scope" label="吊销范围" width="160">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="row.scope === '全部会话' ? 'danger' : 'info'">
                    {{ row.scope }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
              <el-table-column prop="operator" label="操作人" width="130" />
            </el-table>
          </div>
        </section>
      </template>
    </PaneTabs>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PaneTabs } from '@zimo/ui'

const stats = [
  { label: '在线会话', value: '12', hint: 'access 未过期的活跃会话', icon: 'User', tone: 'info' },
  { label: '今日签发 Access', value: '86', hint: '登录 + 静默刷新合计', icon: 'Timer', tone: 'success' },
  { label: '活跃 Refresh 家族', value: '9', hint: '每条会话一个家族', icon: 'Link', tone: 'purple' },
  { label: '30 天内吊销', value: '3', hint: '主动下线 / 泄露处置', icon: 'Lock', tone: 'warning' }
]

const keyword = ref('')

const sessions = ref([
  { id: 1, account: 'admin', userType: 'CONSOLE', method: '密码 + 动态码', device: 'Chrome 130 / macOS', ip: '10.12.3.45', loginAt: '2026-11-05 09:02:11', accessExpire: '11:02 过期', expiringSoon: true },
  { id: 2, account: 'zhangsan', userType: 'CONSOLE', method: '企业微信 SSO', device: 'Edge 129 / Windows', ip: '10.12.3.77', loginAt: '2026-11-05 09:42:18', accessExpire: '11:42 过期', expiringSoon: true },
  { id: 3, account: 'lisi', userType: 'CONSOLE', method: '密码', device: 'Safari 18 / macOS', ip: '10.12.4.12', loginAt: '2026-11-05 08:15:40', accessExpire: '10:15 过期', expiringSoon: false },
  { id: 4, account: 'user_1024', userType: 'CONSUMER', method: '手机验证码', device: 'iOS App 3.2.1', ip: '203.0.113.25', loginAt: '2026-11-05 07:58:02', accessExpire: '09:58 过期', expiringSoon: false },
  { id: 5, account: 'wangwu', userType: 'CONSOLE', method: '邮箱验证码', device: 'Chrome 130 / Windows', ip: '10.12.4.90', loginAt: '2026-11-04 18:20:31', accessExpire: '20:20 过期', expiringSoon: false }
])

const revokes = ref([
  { time: '2026-11-04 16:22:10', account: 'zhaoliu', scope: '全部会话', reason: '疑似密码泄露，管理员主动处置', operator: 'admin' },
  { time: '2026-11-03 11:05:44', account: 'wangwu', scope: '单个会话', reason: '用户主动退出（删除 refresh 家族）', operator: 'wangwu' },
  { time: '2026-11-01 09:31:02', account: 'user_1024', scope: '单个会话', reason: '更换手机，旧设备下线', operator: 'system' }
])

/** 页内 Tab：在线会话 / 吊销记录，badge 跟随实际条数 */
const tab = ref('sessions')
const tabs = computed(() => [
  { name: 'sessions', label: '在线会话', badge: sessions.value.length },
  { name: 'revokes', label: '令牌吊销记录', badge: revokes.value.length }
])

const filteredSessions = computed(() => {
  const k = keyword.value.trim()
  if (!k) return sessions.value
  return sessions.value.filter((s) => s.account.includes(k) || s.ip.includes(k))
})

const revoke = async (row) => {
  await ElMessageBox.confirm(
    `确认强制下线「${row.account}」在 ${row.device} 上的会话？该设备的 access 会立即失效。`,
    '强制下线',
    { type: 'warning' }
  )
  sessions.value = sessions.value.filter((s) => s.id !== row.id)
  ElMessage.success('已下线（设计稿，未调用后端）')
}

const revokeAll = async () => {
  await ElMessageBox.confirm('确认强制下线当前全部会话？所有用户需要重新登录。', '批量下线', { type: 'warning' })
  sessions.value = []
  ElMessage.success('已全部下线（设计稿，未调用后端）')
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

.warn { color: var(--gw-warning); }
</style>
