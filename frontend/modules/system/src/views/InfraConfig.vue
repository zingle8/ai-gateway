<template>
  <div class="page">
    <p class="demo-tip">
      <el-tag size="small" type="success" effect="plain">已接真</el-tag>
      <span class="gw-sm gw-muted">
        网关平台本地化配置集中在 Nacos（{{ dataId }}）：DB / Redis / Nacos 连接、JWT secret、上报密钥、
        各服务 URL 等；7 个服务均 <code>spring.config.import</code> 引入（本地 yml 保留同名默认值兜底），
        发布后服务重启或 Nacos 刷新时生效。client 采集模块不受影响（接入方自带配置）。
      </span>
    </p>

    <section class="gw-card">
      <div class="gw-card-hd">
        <span>配置内容（key=value，# 开头为注释）</span>
        <div class="hd-actions">
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="save">发布配置</el-button>
        </div>
      </div>
      <div class="gw-card-bd">
        <el-input
          v-model="content"
          type="textarea"
          :rows="18"
          spellcheck="false"
          class="infra-editor"
          placeholder="# spring.datasource.url=jdbc:mysql://127.0.0.1:3306/api_gateway?...&#10;# spring.data.redis.host=127.0.0.1&#10;# gateway.jwt.secret=...&#10;# infra.service.jv-api-gateway-user=http://127.0.0.1:28081/api/auth/login"
        />
        <div class="gw-sm gw-muted" style="margin-top: 8px">
          注意：发布即覆盖 Nacos 上的整份配置（先改再发布）；敏感项（password / secret / token）在下方条目预览中打码，编辑框内为原文。
        </div>
      </div>
    </section>

    <section class="gw-card" style="margin-top: 12px">
      <div class="gw-card-hd"><span>条目预览（{{ entries.length }} 项）</span></div>
      <div class="gw-card-bd no-pad">
        <el-table :data="entries" size="small" style="width: 100%">
          <el-table-column prop="key" label="配置项" min-width="280" show-overflow-tooltip>
            <template #default="{ row }"><span class="gw-mono">{{ row.key }}</span></template>
          </el-table-column>
          <el-table-column label="值" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="gw-mono">{{ row.sensitive ? '••••••（敏感项已打码）' : row.value }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.sensitive ? 'danger' : 'info'">
                {{ row.sensitive ? '敏感' : '普通' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchInfraConfig, saveInfraConfig } from '../api'

const loading = ref(false)
const saving = ref(false)
const content = ref('')
const entries = ref([])
const dataId = ref('gw-platform-infra.properties')

const load = async () => {
  loading.value = true
  try {
    const d = await fetchInfraConfig()
    content.value = d?.content || ''
    entries.value = d?.entries || []
    if (d?.dataId) dataId.value = d.dataId
  } catch (e) {
    ElMessage.error(e.message || '配置读取失败')
  } finally {
    loading.value = false
  }
}

const save = async () => {
  await ElMessageBox.confirm(
    '发布将整份覆盖 Nacos 上的基础设施配置，各服务重启或 Nacos 刷新时生效。确认发布？',
    '发布基础设施配置',
    { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' }
  )
  saving.value = true
  try {
    await saveInfraConfig(content.value)
    ElMessage.success('已发布到 Nacos，服务重启或刷新时生效')
    await load()
  } catch (e) { /* 拦截器已提示 */ } finally {
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
.infra-editor :deep(textarea) {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}
code {
  background: var(--gw-bg-soft, #f5f7fa);
  padding: 0 4px;
  border-radius: 3px;
}
</style>
