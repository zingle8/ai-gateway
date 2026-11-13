<template>
  <div class="page">
    <!-- 接入指引：IdP 端点与协议要点 -->
    <section class="gw-card sso-guide">
      <div class="gw-card-hd">
        <span>平台即认证中心（IdP）</span>
        <span class="gw-sm gw-muted">OIDC 授权码模式 · 业务平台作为接入方（SP）接入</span>
      </div>
      <div class="gw-card-bd sso-endpoints">
        <div v-for="e in endpoints" :key="e.label" class="sso-endpoint">
          <span class="ep-label">{{ e.label }}</span>
          <span class="gw-mono gw-sm ep-url">{{ e.url }}</span>
          <el-button size="small" link type="primary" @click="copy(e.url)">复制</el-button>
        </div>
        <div class="gw-sm gw-muted sso-flow">
          流程：SP 跳 authorize（未登录 302 回控制台登录页）→ 登录后 302 回 SP 回调携带 code →
          SP 后端 POST token（client_secret + code）换 id_token / access_token → SP 调 userinfo 实时取身份；
          平台侧登出 / 被踢自增 token_version，userinfo 随即 401，接入方本地会话随之失效。
          签名为平台 HS256 对称密钥（jwks 返回空键集，SP 用共享 secret 验签或走 userinfo）。
        </div>
      </div>
    </section>

    <PaneTabs v-model="tab" :tabs="tabs">
      <!-- 接入方登记 -->
      <template #provider>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>接入方登记（业务平台）</span>
            <div class="hd-actions">
              <el-button size="small" type="primary" @click="openCreate">登记接入方</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="clients" size="small" v-loading="loading" style="width: 100%">
              <el-table-column label="接入方" min-width="150">
                <template #default="{ row }">
                  <div class="prov">
                    <span class="prov-dot" :class="row.status === 1 ? 'on' : 'off'"></span>
                    <b>{{ row.clientName }}</b>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="Client ID" min-width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.clientId }}</span>
                </template>
              </el-table-column>
              <el-table-column label="关联平台 appId" width="140">
                <template #default="{ row }">
                  <span v-if="row.appId" class="gw-mono gw-sm">{{ row.appId }}</span>
                  <span v-else class="gw-sm gw-muted">未关联（不校验授权）</span>
                </template>
              </el-table-column>
              <el-table-column label="回调地址" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.redirectUri }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                  <el-button link type="warning" @click="doRotate(row)">轮换密钥</el-button>
                  <el-button link :type="row.status === 1 ? 'info' : 'success'" @click="toggle(row)">
                    {{ row.status === 1 ? '停用' : '启用' }}
                  </el-button>
                  <el-button link type="danger" @click="doDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">
              共 {{ clients.length }} 个接入方 · client_secret 仅签发 / 轮换时返回一次，库内只存 SHA-256 摘要
            </span>
          </div>
        </section>
      </template>

      <!-- SSO 登录审计 -->
      <template #audit>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>SSO 登录审计</span>
            <div class="hd-actions">
              <el-select v-model="logFilter.action" placeholder="全部动作" clearable size="small"
                         style="width: 140px" @change="loadLogs(1)">
                <el-option label="授权 AUTHORIZE" value="AUTHORIZE" />
                <el-option label="换令牌 TOKEN" value="TOKEN" />
                <el-option label="取身份 USERINFO" value="USERINFO" />
              </el-select>
              <el-button size="small" @click="loadLogs(1)">查询</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="logs" size="small" v-loading="logLoading" style="width: 100%">
              <el-table-column label="时间" width="160">
                <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
              </el-table-column>
              <el-table-column prop="username" label="账号" width="120">
                <template #default="{ row }">{{ row.username || '-' }}</template>
              </el-table-column>
              <el-table-column label="动作" width="140">
                <template #default="{ row }">{{ actionLabel(row.action) }}</template>
              </el-table-column>
              <el-table-column label="接入方" width="150">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.clientId || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="来源 IP" width="130">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.ip || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">
                    {{ row.result === 'SUCCESS' ? '成功' : '拒绝' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="detail" label="说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">共 {{ logTotal }} 条 · 授权 / 换令牌 / 取身份全链路留痕</span>
            <GwPager :total="logTotal" :current="logPage" :size="logSize"
                     @page-change="loadLogs" @size-change="onLogSize" />
          </div>
        </section>
      </template>
    </PaneTabs>

    <!-- 接入方表单 -->
    <el-drawer v-model="drawer" :title="form.id ? '编辑接入方' : '登记接入方'" size="440px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="接入方名称" required>
          <el-input v-model="form.clientName" placeholder="如：工单系统" />
        </el-form-item>
        <el-form-item label="关联平台">
          <el-select v-model="form.appId" style="width: 100%" clearable filterable placeholder="选择开放平台（换 token 时校验账号-平台授权）">
            <el-option v-for="p in platforms" :key="p.appId" :label="`${p.appName}（${p.appId}）`" :value="p.appId" />
          </el-select>
        </el-form-item>
        <el-form-item label="回调地址" required>
          <el-input v-model="form.redirectUri" type="textarea" :rows="2"
                    placeholder="SP 接收 code 的地址，多个逗号分隔（精确匹配）" />
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <div v-if="!form.id" class="gw-sm gw-muted" style="margin: 0 0 12px">
          提交后生成 client_id / client_secret，<b>secret 仅展示一次</b>，请立即保存。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保 存</el-button>
      </template>
    </el-drawer>

    <!-- secret 一次性展示 -->
    <el-dialog v-model="secretDialog" title="client_secret（仅此一次）" width="520px">
      <div class="secret-box">
        <div class="gw-mono">{{ issued.clientId }}</div>
        <div class="gw-mono secret-value">{{ issued.clientSecret }}</div>
      </div>
      <div class="gw-sm gw-muted">{{ issued.hint }}</div>
      <template #footer>
        <el-button type="primary" @click="copy(issued.clientSecret)">复制 Secret</el-button>
        <el-button @click="secretDialog = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PaneTabs, GwPager } from '@zimo/ui'
import {
  fetchSsoClients, createSsoClient, updateSsoClient, rotateSsoSecret,
  deleteSsoClient, fetchSsoLogs, fetchOpenPlatforms
} from '../api'

/** IdP 协议端点（网关入口，skip-paths 已放行；user 服务 context-path=/api） */
const issuer = `${window.location.protocol}//${window.location.hostname}:28090`
const endpoints = [
  { label: '授权端点', url: `${issuer}/api/oauth/authorize` },
  { label: '令牌端点', url: `${issuer}/api/oauth/token` },
  { label: '身份端点', url: `${issuer}/api/oauth/userinfo` },
  { label: '元数据发现', url: `${issuer}/api/.well-known/openid-configuration` }
]

const tab = ref('provider')
const loading = ref(false)
const clients = ref([])
const platforms = ref([])

const tabs = computed(() => [
  { name: 'provider', label: '接入方登记', badge: clients.value.length },
  { name: 'audit', label: '登录审计' }
])

const loadClients = async () => {
  loading.value = true
  try {
    clients.value = (await fetchSsoClients()) || []
  } finally {
    loading.value = false
  }
}

/* ---------------------------------------- 登录审计 */

const logs = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = ref(10)
const logLoading = ref(false)
const logFilter = reactive({ action: '' })

const loadLogs = async (page = logPage.value) => {
  logLoading.value = true
  try {
    const d = await fetchSsoLogs({ action: logFilter.action || undefined, page, size: logSize.value })
    logs.value = d.records || []
    logTotal.value = d.total || 0
    logPage.value = page
  } finally {
    logLoading.value = false
  }
}
const onLogSize = (s) => {
  logSize.value = s
  loadLogs(1)
}

watch(tab, (v) => {
  if (v === 'audit' && !logs.value.length) loadLogs(1)
})

/* ---------------------------------------- 接入方表单 */

const drawer = ref(false)
const saving = ref(false)
const form = reactive({ id: null, clientName: '', appId: '', redirectUri: '', statusBool: true })

const openCreate = () => {
  Object.assign(form, { id: null, clientName: '', appId: '', redirectUri: '', statusBool: true })
  drawer.value = true
}

const openEdit = (row) => {
  Object.assign(form, {
    id: row.id,
    clientName: row.clientName,
    appId: row.appId || '',
    redirectUri: row.redirectUri,
    statusBool: row.status === 1
  })
  drawer.value = true
}

const save = async () => {
  if (!form.clientName || !form.redirectUri) {
    ElMessage.warning('请填写接入方名称与回调地址')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateSsoClient(form.id, {
        clientName: form.clientName,
        appId: form.appId || null,
        redirectUri: form.redirectUri,
        status: form.statusBool ? 1 : 0
      })
      ElMessage.success('已保存')
    } else {
      const issued = await createSsoClient({
        clientName: form.clientName,
        appId: form.appId || null,
        redirectUri: form.redirectUri
      })
      secretDialogData.clientId = issued.clientId
      secretDialogData.clientSecret = issued.clientSecret
      secretDialogData.hint = issued.hint || 'client_secret 仅本次返回，请立即保存；库内只存 SHA-256 摘要'
      secretDialog.value = true
    }
    drawer.value = false
    loadClients()
  } finally {
    saving.value = false
  }
}

const secretDialog = ref(false)
const secretDialogData = reactive({ clientId: '', clientSecret: '', hint: '' })
const issued = secretDialogData

const doRotate = async (row) => {
  const ok = await ElMessageBox.confirm(
    `轮换「${row.clientName}」的 client_secret？旧值立即失效，接入方需同步更新配置。`,
    '轮换密钥', { type: 'warning' }
  ).catch(() => false)
  if (!ok) return
  const issued2 = await rotateSsoSecret(row.id)
  secretDialogData.clientId = issued2.clientId
  secretDialogData.clientSecret = issued2.clientSecret
  secretDialogData.hint = issued2.hint || ''
  secretDialog.value = true
}

const toggle = async (row) => {
  await updateSsoClient(row.id, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success(`已${row.status === 1 ? '停用' : '启用'}「${row.clientName}」`)
  loadClients()
}

const doDelete = async (row) => {
  const ok = await ElMessageBox.confirm(
    `删除接入方「${row.clientName}」？删除后该平台的 SSO 接入立即失效。`,
    '删除接入方', { type: 'warning' }
  ).catch(() => false)
  if (!ok) return
  await deleteSsoClient(row.id)
  ElMessage.success('已删除')
  loadClients()
}

/* ---------------------------------------- 工具 */

const actionLabel = (a) => ({ AUTHORIZE: '授权 AUTHORIZE', TOKEN: '换令牌 TOKEN', USERINFO: '取身份 USERINFO', LOGOUT: '登出 LOGOUT' }[a] || a)
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

const copy = async (value) => {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('浏览器不允许访问剪贴板，请手动复制')
  }
}

onMounted(() => {
  loadClients()
  fetchOpenPlatforms().then((list) => {
    platforms.value = (list || []).filter((p) => p.status === 1)
  }).catch(() => {})
})
</script>

<style scoped>
.sso-guide {
  margin-bottom: 12px;
}

.sso-endpoints {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sso-endpoint {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ep-label {
  width: 76px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--gw-text-tertiary);
}

.ep-url {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sso-flow {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--gw-border-light);
  line-height: 1.7;
}

.hd-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.prov {
  display: flex;
  align-items: center;
  gap: 6px;
}

.prov-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.prov-dot.on { background: var(--gw-success); }
.prov-dot.off { background: var(--gw-text-tertiary); }

.secret-box {
  background: var(--gw-bg-muted, #f7f8fa);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  word-break: break-all;
  margin-bottom: 10px;
}

.secret-value { color: var(--gw-danger); }
</style>
