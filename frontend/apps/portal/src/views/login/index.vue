<template>
  <div class="login-page">
    <div class="login-card gw-card">
      <div class="login-hd">
        <div>
          <div class="login-title">
            API网关平台
            <span class="brand-mark">微服务</span>
          </div>
          <div class="login-sub">API Gateway Management Console</div>
        </div>
      </div>

      <el-tabs v-model="mode" class="login-tabs" stretch>
        <!-- 账号密码登录 -->
        <el-tab-pane label="账号登录" name="login">
          <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="请输入密码"
                :prefix-icon="Lock"
              />
            </el-form-item>

            <div class="login-opts">
              <el-checkbox v-model="form.remember">记住我</el-checkbox>
            </div>

            <el-button type="primary" class="login-btn" :loading="loading" @click="onLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <!-- 注册：成功即登录 -->
        <el-tab-pane label="注册账号" name="register">
          <el-form ref="regFormRef" :model="reg" :rules="regRules" size="large" @keyup.enter="onRegister">
            <el-form-item prop="username">
              <el-input v-model="reg.username" placeholder="账号（3-32 位字母 / 数字 / 下划线）" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="reg.nickname" placeholder="姓名（选填）" :prefix-icon="Avatar" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="reg.password"
                type="password"
                show-password
                placeholder="密码（至少 8 位）"
                :prefix-icon="Lock"
              />
            </el-form-item>
            <el-form-item prop="confirm">
              <el-input
                v-model="reg.confirm"
                type="password"
                show-password
                placeholder="确认密码"
                :prefix-icon="Lock"
              />
            </el-form-item>

            <el-button type="primary" class="login-btn" :loading="loading" @click="onRegister">
              注册并登录
            </el-button>
          </el-form>
          <div class="reg-tip">注册默认只读角色，权限由管理员通过「分组与权限」分配</div>
        </el-tab-pane>
      </el-tabs>

      <div class="login-tip">
        内置管理员账号：<b>admin</b> / <b>Admin@123</b>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import { useUserStore, login, register } from '@zimo/shared'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const mode = ref('login')
const formRef = ref(null)
const regFormRef = ref(null)
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'Admin@123', remember: true })
const reg = reactive({ username: '', nickname: '', password: '', confirm: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const regRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,32}$/, message: '3-32 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8-64 位', trigger: 'blur' }
  ],
  confirm: [
    {
      validator: (rule, value, callback) => {
        if (!value) callback(new Error('请再次输入密码'))
        else if (value !== reg.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const enter = (data, remember) => {
  store.setSession(data.token, {
    id: data.id,
    username: data.username,
    nickname: data.nickname,
    role: data.role
  }, remember)
  // SSO IdP 会话（v0.2 第 15 项）：令牌同时写入 gw_token Cookie（与控制台 localStorage 并存），
  // 授权端点凭该 Cookie 判定登录态；Cookie 不分端口，控制台 28080 与网关 28090 同 host 共享
  document.cookie = `gw_token=${data.token}; path=/; max-age=${8 * 3600}; SameSite=Lax`
  // SSO 授权回跳：?sso=<authorize 地址>（由 IdP 未登录时 302 带来），整页跳回授权端点
  const sso = typeof route.query.sso === 'string' ? route.query.sso : ''
  if (sso && sso.includes('/api/oauth/authorize')) {
    window.location.href = sso
    return
  }
  const redirect = route.query.redirect
  router.replace(typeof redirect === 'string' && redirect ? redirect : '/overview')
}

const onLogin = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    enter(data, form.remember)
  } finally {
    loading.value = false
  }
}

const onRegister = async () => {
  if (!regFormRef.value) return
  const valid = await regFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await register({
      username: reg.username,
      password: reg.password,
      nickname: reg.nickname
    })
    ElMessage.success('注册成功，已自动登录')
    enter(data, false)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #F7F8FA 0%, #EDF0F4 100%);
}

.login-card { width: min(400px, calc(100vw - 32px)); padding: 28px; }

.login-hd {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.brand-mark {
  /* 标题后缀小徽标，与标题同行显示 */
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 17px;
  padding: 0 5px;
  margin-left: 7px;
  border-radius: 3px;
  background: var(--gw-primary);
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: .02em;
  white-space: nowrap;
  vertical-align: 2px;
}

.login-title { font-size: 17px; font-weight: 600; }
.login-sub { font-size: 11.5px; color: var(--gw-text-tertiary); margin-top: 2px; }

.login-tabs { margin-bottom: 6px; }

.login-opts {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.login-btn { width: 100%; }

.reg-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--gw-text-tertiary);
  text-align: center;
}

.login-tip {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--gw-border-light);
  font-size: 12px;
  color: var(--gw-text-tertiary);
  text-align: center;
}
</style>
