<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="info" effect="plain">设计稿</el-tag>
      <span class="gw-sm gw-muted">配置可交互，暂不落库；后端 /auth/login-methods 接口待接入。</span>
    </p>

    <!-- 登录因子：决定登录页呈现哪些入口 -->
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
      </div>
    </section>

    <!-- 验证码通道 -->
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
              <el-form-item label=" ">
                <el-button size="small" @click="testChannel('邮件')">发送测试邮件</el-button>
              </el-form-item>
            </el-form>
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
              <el-form-item label=" ">
                <el-button size="small" @click="testChannel('短信')">发送测试短信</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </div>
    </section>

    <div class="actions">
      <el-button type="primary" @click="save">保存配置</el-button>
      <el-button @click="resetAll">重置</el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

/** 登录因子：tier 区分主认证因子与二次验证因子 */
const factors = reactive([
  { key: 'username', name: '用户名 + 密码', desc: '传统账号密码登录，账号为后台分配的登录名', tier: '主因子', extra: '已开启', enabled: true },
  { key: 'email', name: '邮箱 + 密码', desc: '以已验证邮箱作为登录标识', tier: '主因子', extra: '需先完成邮箱验证', enabled: true },
  { key: 'phone', name: '手机号 + 密码', desc: '以已验证手机号作为登录标识', tier: '主因子', extra: '短信通道就绪后可用', enabled: true },
  { key: 'emailOtp', name: '邮箱验证码免密登录', desc: '发送 6 位验证码，验证通过即登录', tier: '主因子', extra: '验证码有效期 5 分钟', enabled: true },
  { key: 'smsOtp', name: '短信验证码免密登录', desc: '短信下发验证码，适合移动端快速登录', tier: '主因子', extra: '60 秒发送间隔', enabled: false },
  { key: 'qrcode', name: '扫码登录', desc: 'PC 端展示二维码，已登录移动端确认后完成登录', tier: '主因子', extra: '二维码有效期 90 秒', enabled: false },
  { key: 'sso', name: '单点登录 SSO', desc: '对接企业 IdP（企业微信 / 钉钉 / OIDC），在「单点登录」页配置', tier: '主因子', extra: '依赖 IdP 接入', enabled: true },
  { key: 'totp', name: '动态码二次验证（TOTP）', desc: '密码或验证码通过后，再输入 6 位动态码，30 秒刷新', tier: '二次验证', extra: '强制范围在「安全策略」配置', enabled: true }
])

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

/** 上次保存的快照：重置时深拷贝还原（纯前端设计稿行为） */
const snapshot = ref(JSON.stringify({ factors, mail, sms }))

const testChannel = (name) => {
  ElMessage.info(`${name}通道测试：后端接口待接入，当前仅演示交互`)
}

const save = () => {
  snapshot.value = JSON.stringify({ factors, mail, sms })
  ElMessage.success('配置已保存（设计稿，未落库）')
}

const resetAll = () => {
  const s = JSON.parse(snapshot.value)
  factors.splice(0, factors.length, ...s.factors)
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

.actions {
  display: flex;
  gap: 8px;
  padding: 12px 0 4px;
}
</style>
