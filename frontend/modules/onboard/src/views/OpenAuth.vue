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
            <b>鉴权</b>：三个接口均在网关白名单内，无需平台令牌；如需限制调用方，
            可在网关前置 API Key（<span class="gw-mono">X-Api-Key: gwak-&lt;keyId&gt;-&lt;secret&gt;</span>）。
          </li>
          <li>
            <b>令牌语义</b>：返回的 <span class="gw-mono">token</span> 与平台控制台令牌等价（同密钥 HS 签名、8 小时有效），
            可直接作为 <span class="gw-mono">Authorization: Bearer</span> 访问网关其余受保护接口。
          </li>
          <li>
            <b>失效时机</b>：用户在平台退出登录（token_version 自增）或被禁用后，
            <span class="gw-mono">/token/verify</span> 立即返回 valid=false。
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
          <el-table-column prop="path" label="路径" min-width="200">
            <template #default="{ row }">
              <span class="gw-mono gw-sm">{{ row.path }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="desc" label="说明" min-width="260" />
        </el-table>
      </div>
    </section>

    <!-- 示例 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>调用示例</span>
        <div class="hd-actions">
          <el-select v-model="active" size="small" style="width: 170px">
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

const enabled = ref(true)
const active = ref('登录')
const apis = [
  { method: 'POST', path: '/api/open/auth/register', desc: '注册账号（默认 VIEWER），成功直接返回令牌，免二次登录' },
  { method: 'POST', path: '/api/open/auth/login', desc: '账号密码登录，返回 token / userId / username / nickname / role' },
  { method: 'POST', path: '/api/open/auth/token/verify', desc: '校验令牌是否仍然有效（签名、过期、退出登录、账号禁用）' }
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
    name: '校验令牌',
    code: `curl -X POST 'http://127.0.0.1:28090/api/open/auth/token/verify' \\
  -H 'Content-Type: application/json' \\
  -d '{"token":"eyJhbGciOiJIUzM4NCJ9..."}'`,
    response: `{
  "code": 200,
  "data": {
    "valid": true,
    "userId": 2,
    "username": "dev",
    "role": "OPERATOR",
    "expiresAt": "2026-11-05T23:08:23.000+00:00",
    "permCodes": ["overview.view", "trace.view", "..."]
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
