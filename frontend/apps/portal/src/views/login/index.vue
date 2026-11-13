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

      <!-- 扫码确认面板：?qr=<ticket>，手机端扫码后打开的确认页（演示确认流） -->
      <div v-if="confirmTicket" class="qr-confirm">
        <div class="qr-confirm-title">扫码确认登录</div>
        <p class="gw-muted qr-confirm-sub">另一台设备正在扫码登录，请使用本机账号确认授权</p>
        <el-form :model="confirmForm" size="large" @keyup.enter="onConfirm">
          <el-form-item>
            <el-input v-model="confirmForm.username" placeholder="请输入账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="confirmForm.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="onConfirm">确认授权</el-button>
        </el-form>
        <div class="qr-confirm-tip">确认后请回到扫码设备，登录将自动完成</div>
      </div>

      <el-tabs v-else v-model="mode" class="login-tabs" stretch>
        <el-tab-pane label="登 录" name="login">
          <!-- 登录因子切换：按「登录方式」页配置动态渲染 -->
          <div class="factor-tabs">
            <button
              v-for="t in factorTabs"
              :key="t.key"
              type="button"
              class="factor-tab"
              :class="{ active: factorTab === t.key }"
              @click="switchFactor(t.key)"
            >{{ t.label }}</button>
          </div>

          <!-- 密码因子：可配置图形验证码 / 弹框动态码 -->
          <el-form v-if="factorTab === 'password'" ref="formRef" :model="pwd" :rules="pwdRules" size="large" @keyup.enter="onLogin">
            <el-form-item prop="username">
              <el-input v-model="pwd.username" placeholder="请输入账号" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="pwd.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
            </el-form-item>

            <el-form-item v-if="cfg.captchaEnabled && cfg.captchaMode === 'image'" prop="code">
              <div class="captcha-row">
                <el-input v-model="pwd.code" placeholder="验证码（不区分大小写）" :prefix-icon="Key" />
                <img
                  v-if="captcha.image"
                  class="captcha-img"
                  :src="captcha.image"
                  alt="点击刷新"
                  title="点击刷新验证码"
                  @click="loadImageCaptcha"
                />
              </div>
            </el-form-item>

            <div v-if="cfg.captchaEnabled && cfg.captchaMode === 'dynamic'" class="dynamic-box" :class="{ on: !!captcha.code }">
              <template v-if="captcha.code">
                <div class="dynamic-code" @select="() => false">{{ captcha.code }}</div>
                <div class="dynamic-meta">
                  动态码 <b>{{ captcha.countdown }}s</b> 后自动更换，请尽快输入
                </div>
              </template>
              <el-button v-else size="small" plain @click="loadDynamicCode">获取动态码</el-button>
            </div>
            <el-form-item v-if="cfg.captchaEnabled && cfg.captchaMode === 'dynamic'" prop="code">
              <el-input v-model="pwd.code" placeholder="请输入上方动态码" :prefix-icon="Key" />
            </el-form-item>

            <div class="login-opts">
              <el-checkbox v-model="pwd.remember">记住我</el-checkbox>
            </div>

            <el-button type="primary" class="login-btn" :loading="loading" @click="onLogin">
              登 录
            </el-button>
          </el-form>

          <!-- 短信 / 邮件验证码免密登录（模拟通道，验证码写服务日志） -->
          <el-form v-else-if="factorTab === 'otp'" size="large" @keyup.enter="onOtpLogin">
            <div class="otp-channel">
              <el-radio-group v-model="otp.channel" size="small">
                <el-radio-button v-if="cfg.smsOtpEnabled" value="SMS">短信验证码</el-radio-button>
                <el-radio-button v-if="cfg.emailOtpEnabled" value="EMAIL">邮件验证码</el-radio-button>
              </el-radio-group>
            </div>
            <el-form-item>
              <el-input v-model="otp.username" placeholder="请输入账号" :prefix-icon="User" />
            </el-form-item>
            <el-form-item>
              <div class="captcha-row">
                <el-input v-model="otp.code" placeholder="6 位验证码" :prefix-icon="Key" />
                <el-button :disabled="otp.countdown > 0" @click="sendOtp">
                  {{ otp.countdown > 0 ? `${otp.countdown}s 后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <div v-if="otp.mock" class="otp-mock">
              模拟通道：验证码已写入 user 服务日志（grep SMS-MOCK / MAIL-MOCK）
            </div>
            <el-button type="primary" class="login-btn" :loading="loading" @click="onOtpLogin">登 录</el-button>
          </el-form>

          <!-- 扫码登录：演示确认流 / 扫码取码 + 手动输入流 -->
          <div v-else-if="factorTab === 'qr'" class="qr-box">
            <template v-if="qr.mode === 'confirm'">
              <img v-if="qr.image" class="qr-img" :src="qr.image" :data-ticket="qr.ticket" alt="扫码二维码" />
              <div class="qr-status" :class="{ ok: qr.status === 'CONFIRMED' }">
                <template v-if="qr.status === 'CONFIRMED'">已确认，正在登录…</template>
                <template v-else-if="qr.status === 'EXPIRED'">
                  二维码已过期
                  <el-button size="small" link type="primary" @click="startQrConfirm">刷新二维码</el-button>
                </template>
                <template v-else>请使用手机扫码，并在打开的页面输入账号密码确认</template>
              </div>
            </template>
            <template v-else>
              <div class="qr-code-panel">
                <div class="qr-code" @select="() => false">{{ qr.code || '------' }}</div>
                <div class="qr-status">
                  手机扫码读取动态码，在下方输入完成登录（<b>{{ qr.codeCountdown }}s</b> 后自动更换）
                </div>
              </div>
              <el-form size="large" @keyup.enter="onQrCodeLogin">
                <el-form-item>
                  <el-input v-model="qrCode.username" placeholder="请输入账号" :prefix-icon="User" />
                </el-form-item>
                <el-form-item>
                  <el-input v-model="qrCode.code" placeholder="二维码中的 6 位动态码" :prefix-icon="Key" />
                </el-form-item>
                <el-button type="primary" class="login-btn" :loading="loading" @click="onQrCodeLogin">登 录</el-button>
              </el-form>
            </template>
          </div>
        </el-tab-pane>

        <!-- 注册：成功即登录 -->
        <el-tab-pane label="注 册" name="register">
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
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar, Key } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { useUserStore, login, register, request } from '@zimo/shared'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const mode = ref('login')
const formRef = ref(null)
const regFormRef = ref(null)
const loading = ref(false)

/* ------------------------------------------------ 登录方式配置（登录方式页维护，实时生效） */
const cfg = reactive({
  captchaEnabled: false,
  captchaMode: 'image',
  smsOtpEnabled: false,
  emailOtpEnabled: false,
  qrEnabled: false,
  qrMode: 'confirm'
})

const loadMethods = async () => {
  try {
    // 公开视图（/auth/login-methods 匿名）：只含因子开关与形态，不含通道凭据
    const data = await request.get('/auth/login-methods')
    const enabled = (key) => {
      const f = (data?.factors || []).find((x) => x.key === key)
      return f ? f.enabled !== false : false
    }
    cfg.captchaEnabled = !!(data?.captcha && data.captcha.enabled)
    cfg.captchaMode = data?.captcha?.mode === 'dynamic' ? 'dynamic' : 'image'
    cfg.smsOtpEnabled = enabled('smsOtp')
    cfg.emailOtpEnabled = enabled('emailOtp')
    cfg.qrEnabled = enabled('qrcode')
    cfg.qrMode = data?.qrcode?.mode === 'code' ? 'code' : 'confirm'
  } catch (e) {
    /* 读取失败：只保留密码入口，不阻断登录 */
  }
}

const factorTabs = computed(() => {
  const tabs = [{ key: 'password', label: '账号密码' }]
  if (cfg.smsOtpEnabled || cfg.emailOtpEnabled) tabs.push({ key: 'otp', label: '验证码登录' })
  if (cfg.qrEnabled) tabs.push({ key: 'qr', label: '扫码登录' })
  return tabs
})

const factorTab = ref('password')
const switchFactor = (key) => {
  factorTab.value = key
  if (key === 'qr') startQr()
}

/* ------------------------------------------------ 密码因子 */
const pwd = reactive({ username: 'admin', password: 'Admin@123', code: '', remember: true })
const captcha = reactive({ ticket: '', image: '', code: '', countdown: 0 })
let captchaTimer = null

const pwdRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const loadImageCaptcha = async () => {
  try {
    const data = await request.get('/auth/captcha/image')
    captcha.ticket = data.ticket
    captcha.image = data.image
    captcha.code = ''
    pwd.code = ''
  } catch (e) { /* 静默，可点击重试 */ }
}

const loadDynamicCode = async () => {
  try {
    const data = await request.get('/auth/captcha/dynamic')
    captcha.ticket = data.ticket
    captcha.code = data.code
    captcha.countdown = data.expiresIn
    pwd.code = ''
    clearInterval(captchaTimer)
    captchaTimer = setInterval(() => {
      captcha.countdown -= 1
      if (captcha.countdown <= 0) {
        clearInterval(captchaTimer)
        loadDynamicCode() // 到点自动更换（保持会话有效）
      }
    }, 1000)
  } catch (e) { /* 静默 */ }
}

const onLogin = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({
      username: pwd.username,
      password: pwd.password,
      factor: 'PASSWORD',
      ticket: cfg.captchaEnabled ? captcha.ticket : undefined,
      code: cfg.captchaEnabled ? pwd.code : undefined
    })
    ElMessage.success('登录成功')
    enter(data, pwd.remember)
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
    // 验证码一次性：失败后立即换一张 / 换一个码
    if (cfg.captchaEnabled && cfg.captchaMode === 'image') loadImageCaptcha()
    if (cfg.captchaEnabled && cfg.captchaMode === 'dynamic') loadDynamicCode()
  } finally {
    loading.value = false
  }
}

/* ------------------------------------------------ 短信 / 邮件验证码 */
const otp = reactive({ channel: 'SMS', username: '', code: '', ticket: '', countdown: 0, mock: false })
let otpTimer = null

const sendOtp = async () => {
  if (!otp.username.trim()) {
    ElMessage.warning('请先输入账号')
    return
  }
  try {
    const data = await request.post('/auth/otp/send', {
      username: otp.username.trim(),
      channel: otp.channel
    })
    otp.ticket = data.ticket
    otp.mock = !!data.mock
    otp.countdown = 60
    otp.code = ''
    ElMessage.success(`验证码已发送（${data.channel === 'EMAIL' ? '邮件' : '短信'}，接收方 ${data.target}）`)
    clearInterval(otpTimer)
    otpTimer = setInterval(() => {
      otp.countdown -= 1
      if (otp.countdown <= 0) clearInterval(otpTimer)
    }, 1000)
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  }
}

const onOtpLogin = async () => {
  if (!otp.username.trim() || !otp.code.trim()) {
    ElMessage.warning('请输入账号与验证码')
    return
  }
  loading.value = true
  try {
    const data = await login({
      username: otp.username.trim(),
      factor: otp.channel,
      ticket: otp.ticket,
      code: otp.code.trim()
    })
    ElMessage.success('登录成功')
    enter(data, false)
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

/* ------------------------------------------------ 扫码登录 */
const qr = reactive({
  mode: 'confirm',
  ticket: '',
  content: '',
  code: '',
  image: '',
  status: 'WAITING',
  countdown: 0,
  codeCountdown: 0
})
const qrCode = reactive({ username: '', code: '' })
let qrTimer = null
let qrCodeTimer = null

const startQr = async () => {
  clearInterval(qrTimer)
  clearInterval(qrCodeTimer)
  qr.image = ''
  qr.code = ''
  qrCode.code = ''
  try {
    const data = await request.post('/auth/qr/create', { mode: cfg.qrMode })
    qr.mode = data.mode
    qr.ticket = data.ticket
    if (data.mode === 'confirm') {
      qr.content = data.content
      qr.image = await QRCode.toDataURL(data.content, { width: 190, margin: 1 })
      qr.status = 'WAITING'
      qrTimer = setInterval(pollQrStatus, 2000)
    } else {
      qr.code = data.code
      qr.codeCountdown = data.codeExpiresIn
      qrCodeTimer = setInterval(async () => {
        qr.codeCountdown -= 1
        if (qr.codeCountdown <= 0) {
          try {
            const d = await request.get('/auth/qr/code', { params: { ticket: qr.ticket } })
            qr.code = d.code
            qr.codeCountdown = d.expiresIn
          } catch (e) {
            clearInterval(qrCodeTimer)
          }
        }
      }, 1000)
    }
  } catch (e) {
    ElMessage.error(e.message || '二维码生成失败')
  }
}

const pollQrStatus = async () => {
  if (!qr.ticket) return
  try {
    const data = await request.get('/auth/qr/status', { params: { ticket: qr.ticket } })
    qr.status = data.status
    if (data.status === 'CONFIRMED') {
      clearInterval(qrTimer)
      loading.value = true
      try {
        const res = await login({ factor: 'QR_CONFIRM', username: '', ticket: qr.ticket })
        ElMessage.success('扫码登录成功')
        enter(res, false)
      } catch (e) {
        ElMessage.error(e.message || '扫码登录失败')
        startQrConfirm()
      } finally {
        loading.value = false
      }
    }
  } catch (e) { /* 网络抖动忽略，下轮重试 */ }
}

const startQrConfirm = () => {
  qr.status = 'WAITING'
  startQr()
}

const onQrCodeLogin = async () => {
  if (!qrCode.username.trim() || !qrCode.code.trim()) {
    ElMessage.warning('请输入账号与二维码动态码')
    return
  }
  loading.value = true
  try {
    const data = await login({
      username: qrCode.username.trim(),
      factor: 'QR',
      ticket: qr.ticket,
      code: qrCode.code.trim()
    })
    ElMessage.success('登录成功')
    enter(data, false)
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

/* ------------------------------------------------ 扫码确认面板（?qr=<ticket>） */
const confirmTicket = ref(typeof route.query.qr === 'string' ? route.query.qr : '')
const confirmForm = reactive({ username: '', password: '' })

const onConfirm = async () => {
  if (!confirmForm.username.trim() || !confirmForm.password) {
    ElMessage.warning('请输入账号与密码')
    return
  }
  loading.value = true
  try {
    await request.post('/auth/qr/confirm', {
      ticket: confirmTicket.value,
      username: confirmForm.username.trim(),
      password: confirmForm.password
    })
    ElMessage.success('已确认授权，请回到扫码设备完成登录')
    confirmTicket.value = ''
    mode.value = 'login'
  } catch (e) {
    ElMessage.error(e.message || '确认失败')
  } finally {
    loading.value = false
  }
}

/* ------------------------------------------------ 注册 */
const reg = reactive({ username: '', nickname: '', password: '', confirm: '' })
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
  } catch (e) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadMethods()
  if (cfg.captchaEnabled && cfg.captchaMode === 'image') {
    loadImageCaptcha()
  }
  // 扫码确认面板不轮询，登录 Tab 保持密码因子默认
})

onBeforeUnmount(() => {
  clearInterval(captchaTimer)
  clearInterval(otpTimer)
  clearInterval(qrTimer)
  clearInterval(qrCodeTimer)
})
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #F7F8FA 0%, #EDF0F4 100%);
}

.login-card { width: min(420px, calc(100vw - 32px)); padding: 28px; }

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

/* 登录因子切换（胶囊按钮组） */
.factor-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
  padding: 3px;
  background: #F2F4F7;
  border-radius: 6px;
}

.factor-tab {
  flex: 1;
  border: none;
  background: transparent;
  padding: 6px 0;
  font-size: 12.5px;
  color: var(--gw-text-secondary);
  border-radius: 5px;
  cursor: pointer;
  transition: all .15s;
}

.factor-tab.active {
  background: #fff;
  color: var(--gw-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0,0,0,.08);
}

/* 图形验证码行 */
.captcha-row { display: flex; gap: 8px; width: 100%; }
.captcha-img {
  height: 40px;
  width: 118px;
  border-radius: 5px;
  cursor: pointer;
  border: 1px solid var(--gw-border);
  flex-shrink: 0;
}

/* 弹框动态码 */
.dynamic-box {
  border: 1px dashed var(--gw-border);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
  text-align: center;
}
.dynamic-box.on { border-color: var(--gw-primary); background: #FAFCFF; }
.dynamic-code {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 8px;
  color: var(--gw-primary);
  user-select: none;
  -webkit-user-select: none;
}
.dynamic-meta { font-size: 11.5px; color: var(--gw-text-tertiary); margin-top: 4px; }

/* 短信 / 邮件 */
.otp-channel { margin-bottom: 12px; text-align: center; }
.otp-mock {
  font-size: 11.5px;
  color: #B8860B;
  background: #FFF8E6;
  border: 1px solid #F0DFB8;
  border-radius: 5px;
  padding: 7px 10px;
  margin-bottom: 10px;
}

/* 扫码 */
.qr-box { text-align: center; padding: 6px 0; }
.qr-img {
  width: 190px;
  height: 190px;
  border: 1px solid var(--gw-border);
  border-radius: 8px;
  background: #fff;
}
.qr-status { font-size: 12px; color: var(--gw-text-secondary); margin-top: 10px; }
.qr-status.ok { color: var(--gw-primary); font-weight: 600; }
.qr-code-panel { padding: 6px 0 12px; }
.qr-code {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 9px;
  color: var(--gw-primary);
  user-select: none;
  -webkit-user-select: none;
}
.qr-code-panel .qr-status { margin-top: 6px; }
.qr-box .el-form { margin-top: 6px; }

/* 扫码确认面板 */
.qr-confirm-title { font-size: 16px; font-weight: 600; }
.qr-confirm-sub { font-size: 12px; margin: 6px 0 16px; }
.qr-confirm-tip { font-size: 11.5px; color: var(--gw-text-tertiary); margin-top: 12px; text-align: center; }

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
