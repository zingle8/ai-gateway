<template>
  <div class="page">
    <p class="demo-tip">
      <span class="gw-sm gw-muted">
        其他平台通过以下接口复用网关平台的账号体系；总开关在「系统管理 → 系统设置 → platform.open.auth.enabled」，关闭后所有开放接口返回 403
      </span>
    </p>

    <!-- 接入说明 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>接入说明</span>
        <el-tag size="small" :type="enabled ? 'success' : 'info'" effect="plain">
          {{ enabled ? '开放认证：已开启' : '开放认证：已关闭' }}
        </el-tag>
      </div>
      <div class="gw-card-bd">
        <ol class="steps">
          <li>
            <b>基地址</b>：<span class="gw-mono">http://网关地址:28090/api/open/auth</span>
            （经网关统一转发到 jv-api-gateway-user）
          </li>
          <li>
            <b>鉴权</b>：五个接口均在网关白名单内，无需平台令牌；如需限制调用方，
            可在网关前置 API Key（<span class="gw-mono">X-Api-Key: gwak-&lt;keyId&gt;-&lt;secret&gt;</span>）。
          </li>
          <li>
            <b>用户令牌</b>：返回的 <span class="gw-mono">token</span> 与平台控制台令牌等价（同密钥 HS 签名、8 小时有效），
            可直接作为 <span class="gw-mono">Authorization: Bearer</span> 访问网关其余受保护接口。
          </li>
          <li>
            <b>失效时机</b>：调用 <span class="gw-mono">/logout</span> 或用户在平台退出登录（token_version 自增）后，
            历史用户令牌全部失效；账号被禁用后同样立即失效。
          </li>
          <li>
            <b>令牌校验支持两类</b>：
            <el-tag size="small" effect="plain" class="ml4">USER</el-tag>
            用户 JWT（返回身份 / 角色 / 权限点）、
            <el-tag size="small" effect="plain" class="ml4">API_KEY</el-tag>
            应用级 Key（返回 keyId / 名称 / 有效期，需先在「API Key 管理」签发）；
            不传 <span class="gw-mono">type</span> 时按 <span class="gw-mono">gwak-</span> 前缀自动识别。
          </li>
        </ol>

        <el-table :data="apis" size="small" style="width: 100%" class="api-table">
          <el-table-column prop="method" label="方法" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.method === 'GET' ? 'success' : 'primary'" effect="plain">
                {{ row.method }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="220">
            <template #default="{ row }">
              <span class="gw-mono gw-sm">{{ row.path }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="desc" label="说明" min-width="300" />
        </el-table>
      </div>
    </section>

    <!-- 调用方约束（v0.2 第 6 项，已接真） -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>调用方约束</span>
        <div class="hd-actions">
          <el-tag size="small" type="success" effect="plain">已接真</el-tag>
          <span class="gw-sm gw-muted">开放接口必须携带 API Key</span>
          <el-switch v-model="requireKey" :loading="policySaving" size="small" @change="toggleRequireKey" />
        </div>
      </div>
      <div class="gw-card-bd">
        <ul class="steps">
          <li>
            开关关闭（默认）：<span class="gw-mono">/api/open/**</span> 按白名单放行，行为与旧版本一致。
          </li>
          <li>
            开关开启：必须携带 <span class="gw-mono">X-Api-Key: gwak-&lt;keyId&gt;-&lt;secret&gt;</span>，
            不接受用户令牌；Key 的状态 / 有效期 / IP 白名单 / 服务与平台作用域全部生效；
            凭证无效 401、越界 403。
          </li>
          <li>
            每个 Key 的约束在「认证与安全 → API Key → 约束」维护（网关 30 秒内生效）；
            <span class="gw-mono">每分钟限流</span> 复用网关令牌桶，与「限流策略」中 API_KEY 维度规则叠加生效。
          </li>
        </ul>

        <el-table :data="keys" v-loading="keysLoading" size="small" style="width: 100%">
          <el-table-column prop="name" label="Key 名称" min-width="150" />
          <el-table-column label="keyId" width="120">
            <template #default="{ row }">
              <span class="gw-mono gw-sm">{{ row.keyId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="IP 白名单" min-width="170">
            <template #default="{ row }">
              <span v-if="row.allowedIps" class="gw-mono gw-sm">{{ row.allowedIps }}</span>
              <span v-else class="gw-muted">不限</span>
            </template>
          </el-table-column>
          <el-table-column label="服务 / 平台白名单" min-width="200">
            <template #default="{ row }">
              <span v-if="row.allowedServices" class="gw-sm">服务：{{ row.allowedServices }}</span>
              <span v-if="row.allowedPlatforms" class="gw-sm scope-line">平台：{{ row.allowedPlatforms }}</span>
              <span v-if="!row.allowedServices && !row.allowedPlatforms" class="gw-muted">不限</span>
            </template>
          </el-table-column>
          <el-table-column label="每分钟限流" width="110">
            <template #default="{ row }">
              <span v-if="row.rateLimitPerMinute > 0">{{ row.rateLimitPerMinute }} 次/分</span>
              <span v-else class="gw-muted">不限</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- 示例 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>调用示例</span>
        <div class="hd-actions">
          <el-select v-model="active" size="small" style="width: 190px">
            <el-option v-for="e in examples" :key="e.name" :label="e.name" :value="e.name" />
          </el-select>
          <el-button size="small" type="primary" plain @click="copy">复制</el-button>
        </div>
      </div>
      <div class="gw-card-bd">
        <pre class="code-block">{{ current.code }}</pre>
        <div class="resp-title gw-sm gw-muted">响应示例</div>
        <pre class="code-block">{{ current.response }}</pre>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchApiKeyList, fetchOpenAuthPolicy, saveOpenAuthPolicy } from '../api.js'

const enabled = ref(true)
/* 调用方约束：开关 + Key 约束清单（已接真） */
const requireKey = ref(false)
const policySaving = ref(false)
const keys = ref([])
const keysLoading = ref(false)

const loadKeys = async () => {
  keysLoading.value = true
  try {
    keys.value = await fetchApiKeyList()
  } catch (e) {
    keys.value = []
  } finally {
    keysLoading.value = false
  }
}

const toggleRequireKey = async (value) => {
  policySaving.value = true
  try {
    await saveOpenAuthPolicy(value)
    ElMessage.success(value ? '已开启调用方约束：开放接口必须携带 API Key（10 秒内生效）'
      : '已关闭调用方约束：开放接口恢复白名单放行（10 秒内生效）')
  } catch (e) {
    requireKey.value = !value
  } finally {
    policySaving.value = false
  }
}

const active = ref('登录')
const apis = [
  { method: 'POST', path: '/api/open/auth/register', desc: '注册账号（默认 VIEWER），成功直接返回令牌，免二次登录' },
  { method: 'POST', path: '/api/open/auth/login', desc: '账号密码登录，返回 token / userId / username / nickname / role' },
  { method: 'POST', path: '/api/open/auth/logout', desc: '退出登录：自增 token_version，该账号此前签发的用户令牌全部失效（API Key 不支持，返回 400）' },
  { method: 'POST', path: '/api/open/auth/token/verify', desc: '校验令牌：type=USER 校验用户 JWT，type=API_KEY 校验 API Key；不传 type 按 gwak- 前缀自动识别' }
]

const examples = [
  {
    name: '登录',
    code: `curl -X POST 'http://127.0.0.1:28090/api/open/auth/login' \\
  -H 'Content-Type: application/json' \\
  -d '{"username":"dev","password":"Dev@123"}'`,
    response: `{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "id": 2,
    "username": "dev",
    "nickname": "张开发",
    "role": "OPERATOR"
  },
  "success": true
}`
  },
  {
    name: '注册',
    code: `curl -X POST 'http://127.0.0.1:28090/api/open/auth/register' \\
  -H 'Content-Type: application/json' \\
  -d '{"username":"partner_user","password":"Partner@123","nickname":"合作方用户"}'`,
    response: `{
  "code": 200,
  "message": "操作成功",
  "data": { "token": "...", "id": 9, "username": "partner_user", "role": "VIEWER" },
  "success": true
}`
  },
  {
    name: '退出登录',
    code: `curl -X POST 'http://127.0.0.1:28090/api/open/auth/logout' \\
  -H 'Content-Type: application/json' \\
  -d '{"token":"eyJhbGciOiJIUzM4NCJ9..."}'`,
    response: `{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "success": true
}

// 之后再校验同一 token：
// { "valid": false, "tokenType": "USER", "reason": "账号已退出登录，令牌已失效" }`
  },
  {
    name: '校验用户令牌',
    code: `curl -X POST 'http://127.0.0.1:28090/api/open/auth/token/verify' \\
  -H 'Content-Type: application/json' \\
  -d '{"token":"eyJhbGciOiJIUzM4NCJ9...","type":"USER"}'`,
    response: `{
  "code": 200,
  "data": {
    "valid": true,
    "tokenType": "USER",
    "userId": 2,
    "username": "dev",
    "role": "OPERATOR",
    "expiresAt": "2026-11-05T23:08:23.000+00:00",
    "permCodes": ["overview.view", "trace.view", "..."]
  }
}`
  },
  {
    name: '校验 API Key',
    code: `curl -X POST 'http://127.0.0.1:28090/api/open/auth/token/verify' \\
  -H 'Content-Type: application/json' \\
  -d '{"token":"gwak-ab3k9xzm-9f2c4d6e8a0b1c3d5e7f9a1b2c3d4e5f6a7b8c9d","type":"API_KEY"}'`,
    response: `{
  "code": 200,
  "data": {
    "valid": true,
    "tokenType": "API_KEY",
    "keyId": "ab3k9xzm",
    "keyName": "订单系统调用",
    "expiresAt": "2027-02-03T10:00:00.000+00:00"
  }
}`
  }
]

const current = computed(() => examples.find((e) => e.name === active.value) || examples[0])

const copy = async () => {
  await navigator.clipboard.writeText(current.value.code)
  ElMessage.success('已复制')
}

onMounted(async () => {
  const flags = await fetch('/api/configs/public').then((r) => (r.ok ? r.json() : null)).catch(() => null)
  enabled.value = flags?.data?.['platform.open.auth.enabled'] !== 'false'
  await Promise.all([
    fetchOpenAuthPolicy().then((vo) => (requireKey.value = !!vo?.requireKey)).catch(() => {}),
    loadKeys()
  ])
})
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

.ml4 { margin: 0 4px; }

.scope-line { display: block; }

.steps {
  margin: 0 0 14px;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 2;
  color: var(--gw-text-secondary);
}

.steps b { color: var(--gw-text); }

.api-table { margin-bottom: 4px; }

.code-block {
  margin: 0 0 12px;
  padding: 12px 14px;
  background: #0F172A;
  color: #E2E8F0;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  overflow-x: auto;
  font-family: var(--gw-mono, monospace);
  white-space: pre-wrap;
  word-break: break-all;
}

.resp-title { margin-bottom: 6px; }
</style>
