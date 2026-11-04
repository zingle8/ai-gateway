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
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore, login } from '@zimo/shared'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'Admin@123', remember: true })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onLogin = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    store.setSession(
      data.token,
      {
        id: data.id,
        username: data.username,
        nickname: data.nickname,
        role: data.role
      },
      form.remember
    )
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : '/overview')
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

.login-card { width: 380px; padding: 28px; }

.login-hd {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
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

.login-opts {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.login-btn { width: 100%; }

.login-tip {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--gw-border-light);
  font-size: 12px;
  color: var(--gw-text-tertiary);
  text-align: center;
}
</style>
