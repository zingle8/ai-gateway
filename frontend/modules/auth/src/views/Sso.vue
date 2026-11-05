<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="info" effect="plain">设计稿</el-tag>
      <span class="gw-sm gw-muted">IdP 配置与审计为示例数据；后端 /auth/sso/providers 接口待接入。</span>
    </p>

    <!-- 配置与审计两类内容按 Tab 分开，各自占满整页 -->
    <PaneTabs v-model="tab" :tabs="tabs">
      <!-- 身份提供方 -->
      <template #provider>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>身份提供方（IdP）</span>
            <div class="hd-actions">
              <el-button size="small" @click="openConfig({})">新增 IdP</el-button>
            </div>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="providers" size="small" style="width: 100%">
              <el-table-column prop="name" label="名称" min-width="150">
                <template #default="{ row }">
                  <div class="prov">
                    <span class="prov-dot" :class="row.enabled ? 'on' : 'off'"></span>
                    <b>{{ row.name }}</b>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="protocol" label="协议" width="120">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ row.protocol }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="clientId" label="Client ID" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.clientId }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="bound" label="绑定用户" width="110" align="right">
                <template #default="{ row }">{{ row.bound }} 人</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                    {{ row.enabled ? '已启用' : '未启用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openConfig(row)">配置</el-button>
                  <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
                    {{ row.enabled ? '停用' : '启用' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="gw-card-ft">
            <span class="gw-sm gw-muted">
              IdP 未接入时「单点登录」入口不展示；回调地址需在 IdP 侧白名单登记
            </span>
          </div>
        </section>
      </template>

      <!-- SSO 登录审计 -->
      <template #audit>
        <section class="gw-card">
          <div class="gw-card-hd">
            <span>SSO 登录审计</span>
            <span class="gw-sm gw-muted">最近 {{ audits.length }} 条</span>
          </div>
          <div class="gw-card-bd no-pad">
            <el-table :data="audits" size="small" style="width: 100%" max-height="420">
              <el-table-column prop="time" label="时间" width="170" />
              <el-table-column prop="account" label="账号" width="140" />
              <el-table-column prop="provider" label="IdP" width="140" />
              <el-table-column prop="ip" label="来源 IP" width="140">
                <template #default="{ row }">
                  <span class="gw-mono gw-sm">{{ row.ip }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="result" label="结果" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.result === '成功' ? 'success' : 'danger'" effect="plain">
                    {{ row.result }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="说明" min-width="180" show-overflow-tooltip />
            </el-table>
          </div>
        </section>
      </template>
    </PaneTabs>

    <!-- IdP 配置抽屉 -->
    <el-drawer v-model="drawer" :title="form.id ? '编辑 IdP' : '新增 IdP'" size="440px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如：企业微信" />
        </el-form-item>
        <el-form-item label="协议">
          <el-select v-model="form.protocol" style="width: 100%">
            <el-option label="OIDC" value="OIDC" />
            <el-option label="OAuth2" value="OAuth2" />
            <el-option label="CAS 3.0" value="CAS" />
            <el-option label="企业微信" value="WeCom" />
            <el-option label="钉钉" value="DingTalk" />
          </el-select>
        </el-form-item>
        <el-form-item label="Client ID">
          <el-input v-model="form.clientId" placeholder="IdP 控制台申请" />
        </el-form-item>
        <el-form-item label="Client Secret">
          <el-input v-model="form.clientSecret" type="password" show-password placeholder="仅保存密文" />
        </el-form-item>
        <el-form-item label="授权端点">
          <el-input v-model="form.authorizeUrl" placeholder="https://idp.example.com/oauth/authorize" />
        </el-form-item>
        <el-form-item label="Token 端点">
          <el-input v-model="form.tokenUrl" placeholder="https://idp.example.com/oauth/token" />
        </el-form-item>
        <el-form-item label="回调地址">
          <el-input :model-value="callbackUrl" readonly>
            <template #append>
              <el-button @click="copyCallback">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="自动建号">
          <el-switch v-model="form.autoProvision" active-text="首次登录自动创建账号" inactive-text="" />
        </el-form-item>
        <el-form-item label="默认角色">
          <el-select v-model="form.defaultRole" style="width: 100%">
            <el-option label="只读" value="VIEWER" />
            <el-option label="运营" value="OPERATOR" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取 消</el-button>
        <el-button type="primary" @click="save">保 存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PaneTabs } from '@zimo/ui'

const callbackUrl = computed(() => `${window.location.origin}/api/auth/sso/callback`)

const providers = ref([
  { id: 1, name: '企业微信', protocol: 'WeCom', clientId: 'ww8f2c1a9b7d3e', bound: 26, enabled: true },
  { id: 2, name: '公司统一身份（OIDC）', protocol: 'OIDC', clientId: 'gateway-console', bound: 41, enabled: true },
  { id: 3, name: 'GitHub', protocol: 'OAuth2', clientId: 'Iv1.9a8b7c6d5e4f', bound: 3, enabled: false }
])

const audits = ref([
  { time: '2026-11-05 09:42:18', account: 'zhangsan', provider: '企业微信', ip: '10.12.3.45', result: '成功', remark: '首次登录，自动建号' },
  { time: '2026-11-05 09:31:07', account: 'lisi', provider: '公司统一身份（OIDC）', ip: '10.12.3.77', result: '成功', remark: '—' },
  { time: '2026-11-05 09:12:55', account: 'unknown', provider: 'GitHub', ip: '203.0.113.8', result: '失败', remark: 'IdP 未启用，拒绝本次登录' },
  { time: '2026-11-04 18:20:31', account: 'wangwu', provider: '企业微信', ip: '10.12.4.12', result: '成功', remark: '账号已绑定，直接登录' },
  { time: '2026-11-04 17:02:09', account: 'zhaoliu', provider: '公司统一身份（OIDC）', ip: '10.12.4.90', result: '失败', remark: 'email 不一致，绑定校验未通过' }
])

/** 页内 Tab：IdP 配置 / 登录审计，badge 显示条数 */
const tab = ref('provider')
const tabs = computed(() => [
  { name: 'provider', label: '身份提供方', badge: providers.value.length },
  { name: 'audit', label: '登录审计', badge: audits.value.length }
])

const drawer = ref(false)
const emptyForm = () => ({
  id: null,
  name: '',
  protocol: 'OIDC',
  clientId: '',
  clientSecret: '',
  authorizeUrl: '',
  tokenUrl: '',
  autoProvision: true,
  defaultRole: 'VIEWER'
})
const form = reactive(emptyForm())

const openConfig = (row) => {
  Object.assign(form, emptyForm(), row || {})
  drawer.value = true
}

const toggle = (row) => {
  row.enabled = !row.enabled
  ElMessage.success(`已${row.enabled ? '启用' : '停用'}「${row.name}」（设计稿，未落库）`)
}

const save = () => {
  if (!form.name) {
    ElMessage.warning('请填写 IdP 名称')
    return
  }
  if (form.id) {
    const target = providers.value.find((p) => p.id === form.id)
    if (target) Object.assign(target, { ...form, enabled: target.enabled })
  } else {
    providers.value.unshift({ ...form, id: Date.now(), bound: 0, enabled: false })
  }
  drawer.value = false
  ElMessage.success('已保存（设计稿，未落库）')
}

const copyCallback = async () => {
  try {
    await navigator.clipboard.writeText(callbackUrl.value)
    ElMessage.success('回调地址已复制')
  } catch (e) {
    ElMessage.warning('浏览器不允许访问剪贴板，请手动复制')
  }
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
</style>
