<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="success" effect="plain">已接真</el-tag>
      <span class="gw-sm gw-muted">
        上报通道（注册 / 心跳 / 指标 / 调用日志 / span / WS）在网关层按 IP 与服务名（appId）做黑白名单，
        配置存 tb_sys_config（platform.report.acl），保存后网关 10 秒内生效；IP 支持前缀通配（如 192.168.*）。
      </span>
    </p>

    <section class="gw-card">
      <div class="gw-card-hd"><span>访问控制</span></div>
      <div class="gw-card-bd">
        <el-form label-width="150px" style="max-width: 780px">
          <el-form-item label="总开关">
            <el-switch v-model="acl.enabled" />
            <span class="gw-sm gw-muted" style="margin-left: 10px">关闭后不做任何名单校验（共享密钥校验不受影响）</span>
          </el-form-item>

          <el-divider content-position="left">IP 维度</el-divider>
          <el-form-item label="IP 名单模式">
            <el-radio-group v-model="acl.ipMode">
              <el-radio-button value="OFF">不启用</el-radio-button>
              <el-radio-button value="WHITELIST">白名单（仅允许）</el-radio-button>
              <el-radio-button value="BLACKLIST">黑名单（仅拒绝）</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="IP 白名单">
            <el-input
              v-model="acl.ipWhitelistText"
              type="textarea" :rows="3"
              :disabled="acl.ipMode !== 'WHITELIST'"
              placeholder="每行一个，支持前缀通配：10.0.0.* / 192.168.1.100"
            />
          </el-form-item>
          <el-form-item label="IP 黑名单">
            <el-input
              v-model="acl.ipBlacklistText"
              type="textarea" :rows="3"
              :disabled="acl.ipMode !== 'BLACKLIST'"
              placeholder="每行一个，支持前缀通配：203.0.113.*"
            />
          </el-form-item>

          <el-divider content-position="left">服务名维度（X-Gateway-App-Id）</el-divider>
          <el-form-item label="服务名模式">
            <el-radio-group v-model="acl.serviceMode">
              <el-radio-button value="OFF">不启用</el-radio-button>
              <el-radio-button value="WHITELIST">白名单（仅允许）</el-radio-button>
              <el-radio-button value="BLACKLIST">黑名单（仅拒绝）</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="服务名白名单">
            <el-input
              v-model="acl.serviceWhitelistText"
              type="textarea" :rows="3"
              :disabled="acl.serviceMode !== 'WHITELIST'"
              placeholder="每行一个 appId 或服务名：order-service / jv-demo-app"
            />
          </el-form-item>
          <el-form-item label="服务名黑名单">
            <el-input
              v-model="acl.serviceBlacklistText"
              type="textarea" :rows="3"
              :disabled="acl.serviceMode !== 'BLACKLIST'"
              placeholder="每行一个：legacy-app"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
            <el-button @click="load">重置</el-button>
            <span class="gw-sm gw-muted" style="margin-left: 10px">
              SDK 需升级到携带 X-Gateway-App-Id 头的版本，服务名比对才生效；未携带的请求在白名单模式下会被拒绝
            </span>
          </el-form-item>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchReportAcl, saveReportAcl } from '../api'

const saving = ref(false)
const acl = ref({
  enabled: false,
  ipMode: 'OFF',
  ipWhitelistText: '',
  ipBlacklistText: '',
  serviceMode: 'OFF',
  serviceWhitelistText: '',
  serviceBlacklistText: ''
})

const toText = (list) => (Array.isArray(list) ? list.join('\n') : '')
const toList = (text) => String(text || '').split('\n').map((s) => s.trim()).filter(Boolean)

const load = async () => {
  try {
    const d = await fetchReportAcl()
    acl.value = {
      enabled: !!d?.enabled,
      ipMode: d?.ipMode || 'OFF',
      ipWhitelistText: toText(d?.ipWhitelist),
      ipBlacklistText: toText(d?.ipBlacklist),
      serviceMode: d?.serviceMode || 'OFF',
      serviceWhitelistText: toText(d?.serviceWhitelist),
      serviceBlacklistText: toText(d?.serviceBlacklist)
    }
  } catch (e) {
    ElMessage.error(e.message || '配置读取失败')
  }
}

const save = async () => {
  saving.value = true
  try {
    await saveReportAcl({
      enabled: acl.value.enabled,
      ipMode: acl.value.ipMode,
      ipWhitelist: toList(acl.value.ipWhitelistText),
      ipBlacklist: toList(acl.value.ipBlacklistText),
      serviceMode: acl.value.serviceMode,
      serviceWhitelist: toList(acl.value.serviceWhitelistText),
      serviceBlacklist: toList(acl.value.serviceBlacklistText)
    })
    ElMessage.success('已保存，网关 10 秒内生效')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
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
</style>
