<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="success" effect="plain">已接真</el-tag>
      <span class="gw-sm gw-muted">配置落库 tb_sys_config 实时生效：密码因子关闭即拒；验证码 / 扫码登录已完整实现（短信、邮件为模拟通道，验证码写服务日志）。</span>
    </p>

    <!-- 页内两个内容块按 Tab 切换，避免整页纵向堆叠 -->
    <PaneTabs v-model="tab" :tabs="TABS">
      <!-- 登录因子：决定登录页呈现哪些入口 -->
      <template #factor>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>登录因子</span>
            <span class="gw-sm gw-muted">开启后登录页出现对应入口，至少保留一种</span>
          </div>
          <div class="gw-card-bd">
            <div class="factor-grid">
              <div v-for="f in factors" :key="f.key" class="factor" :class="{ off: !f.enabled }">
                <div class="factor-hd">
                  <span class="factor-name">{{ f.name }}</span>
                  <el-switch v-model="f.enabled" size="small" />
                </div>
                <p class="factor-desc">{{ f.desc }}</p>
                <div class="factor-foot">
                  <el-tag size="small" effect="plain" :type="f.tier === '主因子' ? 'info' : 'warning'">
                    {{ f.tier }}
                  </el-tag>
                  <span class="gw-sm gw-muted">{{ f.extra }}</span>
                </div>
              </div>
            </div>

            <!-- 密码验证码与扫码形态：登录页按此渲染 -->
            <div class="mode-grid">
              <div class="mode-card">
                <div class="mode-hd">
                  <span>密码登录验证码</span>
                  <el-switch v-model="captcha.enabled" size="small" />
                </div>
                <el-radio-group v-model="captcha.mode" size="small" :disabled="!captcha.enabled">
                  <el-radio-button value="image">随机图形验证码</el-radio-button>
                  <el-radio-button value="dynamic">弹框动态码（30 秒换）</el-radio-button>
                </el-radio-group>
                <p class="mode-desc">开启后登录页密码表单出现验证码；图形码 120 秒有效，动态码 30 秒自动更换</p>
              </div>
              <div class="mode-card">
                <div class="mode-hd">
                  <span>扫码登录形态</span>
                </div>
                <el-radio-group v-model="qr.mode" size="small" :disabled="!factorEnabled('qrcode')">
                  <el-radio-button value="confirm">演示确认流</el-radio-button>
                  <el-radio-button value="code">扫码取码 + 手动输入</el-radio-button>
                </el-radio-group>
                <p class="mode-desc">
                  确认流：二维码为确认链接，扫码后在确认页输账号密码授权；
                  取码流：二维码内容即 6 位动态码（30 秒换），读码后手动输入
                </p>
              </div>
            </div>
          </div>
        </section>
      </template>

      <!-- 验证码通道 -->
      <template #channel>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>验证码通道</span>
            <span class="gw-sm gw-muted">邮箱 / 短信验证码的下发通道，发送频率在「安全策略」中限制</span>
          </div>
          <div class="gw-card-bd">
            <div class="channel-grid">
              <div class="channel">
                <div class="channel-hd">
                  <span>邮件 SMTP</span>
                  <el-switch v-model="mail.enabled" size="small" />
                </div>
                <el-form :model="mail" label-width="88px" size="small" :disabled="!mail.enabled">
                  <el-form-item label="SMTP 主机">
                    <el-input v-model="mail.host" placeholder="smtp.example.com" />
                  </el-form-item>
                  <el-form-item label="端口">
                    <el-input-number v-model="mail.port" :min="1" :max="65535" controls-position="right" style="width: 130px" />
                    <el-select v-model="mail.ssl" style="width: 110px; margin-left: 8px">
                      <el-option label="SSL" value="SSL" />
                      <el-option label="STARTTLS" value="STARTTLS" />
                      <el-option label="无加密" value="NONE" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="发件人">
                    <el-input v-model="mail.from" placeholder="noreply@example.com" />
                  </el-form-item>
                  <el-form-item label="授权码">
                    <el-input v-model="mail.secret" type="password" show-password placeholder="邮箱授权码 / 应用密码" />
                  </el-form-item>
                </el-form>
                <!-- 测试按钮放在表单外：表单 disabled 时仍可点击并给出明确提示 -->
                <div class="channel-actions">
                  <el-button size="small" :loading="testing === 'EMAIL'" :disabled="!mail.enabled" @click="testChannel('EMAIL')">
                    发送测试邮件
                  </el-button>
                  <span class="gw-sm gw-muted">模拟通道：验证码写 user 服务日志，不发真实邮件</span>
                </div>
              </div>

              <div class="channel">
                <div class="channel-hd">
                  <span>短信服务</span>
                  <el-switch v-model="sms.enabled" size="small" />
                </div>
                <el-form :model="sms" label-width="88px" size="small" :disabled="!sms.enabled">
                  <el-form-item label="服务商">
                    <el-select v-model="sms.provider" style="width: 100%">
                      <el-option label="阿里云短信" value="ALIYUN" />
                      <el-option label="腾讯云短信" value="TENCENT" />
                      <el-option label="自定义 HTTP" value="CUSTOM" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="签名">
                    <el-input v-model="sms.sign" placeholder="短信签名（需报备）" />
                  </el-form-item>
                  <el-form-item label="模板号">
                    <el-input v-model="sms.template" placeholder="SMS_123456789" />
                  </el-form-item>
                  <el-form-item label="AccessKey">
                    <el-input v-model="sms.keyId" placeholder="AccessKeyId" />
                  </el-form-item>
                </el-form>
                <div class="channel-actions">
                  <el-button size="small" :loading="testing === 'SMS'" :disabled="!sms.enabled" @click="testChannel('SMS')">
                    发送测试短信
                  </el-button>
                  <span class="gw-sm gw-muted">模拟通道：验证码写 user 服务日志，不发真实短信</span>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>
    </PaneTabs>

    <div class="actions">
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      <el-button @click="resetAll">重置</el-button>
      <span class="gw-sm gw-muted" style="margin-left: 8px">保存会同时提交登录因子与验证码通道</span>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PaneTabs } from '@zimo/ui'
import { request, useUserStore } from '@zimo/shared'
import { fetchLoginMethods, saveLoginMethods } from '../api'

/** 页内 Tab：登录因子 / 验证码通道 */
const TABS = [
  { name: 'factor', label: '登录因子' },
  { name: 'channel', label: '验证码通道' }
]
const tab = ref('factor')
const userStore = useUserStore()

/** 登录因子默认描述：库里只存 key / enabled / name / tier，desc 与 extra 以本地为默认值 */
const DEFAULT_FACTORS = [
  { key: 'username', name: '用户名 + 密码', desc: '传统账号密码登录，账号为后台分配的登录名', tier: '主因子', extra: '已开启', enabled: true },
  { key: 'email', name: '邮箱 + 密码', desc: '以已验证邮箱作为登录标识', tier: '主因子', extra: '需先完成邮箱验证', enabled: true },
  { key: 'phone', name: '手机号 + 密码', desc: '以已验证手机号作为登录标识', tier: '主因子', extra: '短信通道就绪后可用', enabled: true },
  { key: 'emailOtp', name: '邮箱验证码免密登录', desc: '发送 6 位验证码，验证通过即登录', tier: '主因子', extra: '模拟通道：验证码写服务日志，有效期 5 分钟', enabled: false },
  { key: 'smsOtp', name: '短信验证码免密登录', desc: '短信下发验证码，适合移动端快速登录', tier: '主因子', extra: '模拟通道：验证码写服务日志，60 秒发送间隔', enabled: false },
  { key: 'qrcode', name: '扫码登录', desc: 'PC 端展示二维码，手机端确认 / 读码后完成登录', tier: '主因子', extra: '已实现：确认流 / 取码+输入流，180 秒有效', enabled: false },
  { key: 'sso', name: '单点登录 SSO', desc: '对接企业 IdP（企业微信 / 钉钉 / OIDC），在「单点登录」页配置', tier: '主因子', extra: '依赖 IdP 接入', enabled: true },
  { key: 'totp', name: '动态码二次验证（TOTP）', desc: '密码或验证码通过后，再输入 6 位动态码，30 秒刷新', tier: '二次验证', extra: '强制范围在「安全策略」配置', enabled: true }
]

const factors = reactive(DEFAULT_FACTORS.map((f) => ({ ...f })))

/** 密码验证码形态与扫码形态（登录页按此渲染） */
const captcha = reactive({ enabled: false, mode: 'image' })
const qr = reactive({ mode: 'confirm' })

const factorEnabled = (key) => {
  const f = factors.find((x) => x.key === key)
  return !!f && f.enabled !== false
}

const mail = reactive({
  enabled: true,
  host: '',
  port: 465,
  ssl: 'SSL',
  from: '',
  secret: ''
})

const sms = reactive({
  enabled: false,
  provider: 'ALIYUN',
  sign: '',
  template: '',
  keyId: ''
})

/** 上次保存的快照：重置时深拷贝还原 */
const snapshot = ref('')

const snapshotNow = () => JSON.stringify({ factors, captcha, qr, mail, sms })

/** 通道测试（接真）：调 /auth/otp/send 以当前账号为目标发一条动态码（模拟通道，写服务日志） */
const testing = ref('')
const testChannel = async (channel) => {
  const username = userStore.userInfo?.username
  if (!username) {
    ElMessage.warning('当前无登录账号信息，无法测试')
    return
  }
  testing.value = channel
  try {
    const data = await request.post('/auth/otp/send', { username, channel })
    ElMessage.success(
      `${channel === 'EMAIL' ? '邮件' : '短信'}测试已发出（模拟通道，接收方 ${data.target}），` +
      '验证码见 user 服务日志（grep MAIL-MOCK / SMS-MOCK）'
    )
  } catch (e) {
    ElMessage.error(e.message || '测试发送失败')
  } finally {
    testing.value = ''
  }
}

const saving = ref(false)
const save = async () => {
  saving.value = true
  try {
    const data = await saveLoginMethods({
      factors: [...factors],
      captcha: { ...captcha },
      qrcode: { ...qr },
      mail: { ...mail },
      sms: { ...sms }
    })
    applyConfig(data)
    snapshot.value = snapshotNow()
    ElMessage.success('配置已保存，登录流程实时生效')
  } finally {
    saving.value = false
  }
}

const applyConfig = (data) => {
  if (data && Array.isArray(data.factors)) {
    // 库中只保存开关键与名称：与本地默认描述合并，避免卡片描述被清空
    const merged = data.factors.map((f) => {
      const base = DEFAULT_FACTORS.find((d) => d.key === f.key) || {}
      return { ...base, ...f }
    })
    factors.splice(0, factors.length, ...merged)
  }
  if (data && data.captcha) Object.assign(captcha, data.captcha)
  if (data && data.qrcode) Object.assign(qr, data.qrcode)
  if (data && data.mail) Object.assign(mail, data.mail)
  if (data && data.sms) Object.assign(sms, data.sms)
}

onMounted(async () => {
  try {
    const data = await fetchLoginMethods()
    applyConfig(data)
  } catch (e) {
    /* 读取失败保持默认 */
  }
  snapshot.value = snapshotNow()
})

const resetAll = () => {
  const s = JSON.parse(snapshot.value)
  factors.splice(0, factors.length, ...s.factors)
  Object.assign(captcha, s.captcha)
  Object.assign(qr, s.qrcode)
  Object.assign(mail, s.mail)
  Object.assign(sms, s.sms)
  ElMessage.success('已还原为上次保存的配置')
}
</script>

<style scoped>
.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
}

.factor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
}

.factor {
  border: 1px solid var(--gw-border);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--gw-card);
  transition: border-color .15s;
}

.factor:hover { border-color: #d5d7dc; }
.factor.off { background: #FBFBFC; }

.factor-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.factor-name { font-weight: 600; font-size: 13px; }

.factor-desc {
  margin: 6px 0 8px;
  font-size: 12px;
  color: var(--gw-text-secondary);
  line-height: 1.5;
  min-height: 36px;
}

.factor-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

/* 密码验证码 / 扫码形态配置 */
.mode-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--gw-border);
}

@media (max-width: 1200px) {
  .mode-grid { grid-template-columns: 1fr; }
}

.mode-card {
  border: 1px solid var(--gw-border);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--gw-card);
}

.mode-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}

.mode-desc {
  margin: 8px 0 0;
  font-size: 11.5px;
  color: var(--gw-text-tertiary);
  line-height: 1.5;
}

.channel-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 1200px) {
  .channel-grid { grid-template-columns: 1fr; }
}

.channel-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 13px;
  padding-bottom: 8px;
  margin-bottom: 10px;
  border-bottom: 1px dashed var(--gw-border);
}

.channel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 2px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0 4px;
}
</style>
