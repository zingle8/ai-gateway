<template>
  <div class="page">
    <p class="demo-tip">
      <span class="gw-sm gw-muted">
        平台级配置统一存在 tb_sys_config（jv-api-gateway-config 服务），保存后各模块在 30 秒内自动生效，无需重启
      </span>
    </p>

    <section class="gw-card">
      <div class="gw-card-hd">
        <span>平台配置</span>
        <div class="hd-actions">
          <span class="gw-sm gw-muted">当前生效的鉴权缓存：{{ effectiveCacheMode || '—' }}</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>

      <div v-loading="loading" class="gw-card-bd">
        <div v-for="item in rows" :key="item.key" class="config-row">
          <div class="config-main">
            <div class="config-title">
              {{ item.key }}
              <el-tag size="small" effect="plain" class="type-tag">{{ item.valueType }}</el-tag>
            </div>
            <div class="config-remark">{{ item.remark }}</div>
          </div>

          <div class="config-control">
            <!-- 布尔型 → 开关 -->
            <el-switch
              v-if="item.valueType === 'BOOLEAN'"
              :model-value="item.value === 'true'"
              :loading="saving === item.key"
              @change="(v) => save(item, v ? 'true' : 'false')"
            />

            <!-- 枚举型（鉴权缓存模式）→ 单选 -->
            <el-radio-group
              v-else-if="item.key === 'platform.auth.cache.mode'"
              :model-value="item.value"
              :disabled="saving === item.key"
              @change="(v) => save(item, v)"
            >
              <el-radio-button label="auto">auto</el-radio-button>
              <el-radio-button label="redis">redis</el-radio-button>
              <el-radio-button label="local">local</el-radio-button>
            </el-radio-group>

            <!-- 其他 → 输入框 + 保存 -->
            <div v-else class="text-edit">
              <el-input v-model="item.value" size="small" style="width: 220px" />
              <el-button size="small" type="primary" :loading="saving === item.key" @click="save(item, item.value)">
                保存
              </el-button>
            </div>

            <span class="config-time gw-sm gw-muted">{{ item.updateTime || '—' }}</span>
          </div>
        </div>
      </div>

      <div class="gw-card-ft">
        <span class="gw-sm gw-muted">
          鉴权缓存：auto = Redis 可用则用 Redis，否则本地 Caffeine；MySQL 始终保存源数据。缓存 TTL 30 秒，停用 Key / 退出登录最迟 30 秒生效。
        </span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchConfigs, updateConfig, fetchMenuHealth } from '../api.js'

const loading = ref(false)
const saving = ref('')
const rows = ref([])
const effectiveCacheMode = ref('')

const load = async () => {
  loading.value = true
  try {
    rows.value = await fetchConfigs()
    const health = await fetchMenuHealth().catch(() => null)
    effectiveCacheMode.value = health?.authCacheMode || ''
  } finally {
    loading.value = false
  }
}

const save = async (item, value) => {
  saving.value = item.key
  try {
    await updateConfig(item.key, value)
    item.value = value
    ElMessage.success('已保存，30 秒内生效')
    const health = await fetchMenuHealth().catch(() => null)
    effectiveCacheMode.value = health?.authCacheMode || ''
  } finally {
    saving.value = ''
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

.hd-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.config-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 0;
  border-bottom: 1px dashed var(--gw-border-light);
}

.config-row:last-child { border-bottom: 0; }

.config-main { min-width: 0; flex: 1; }

.config-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--gw-mono, monospace);
}

.type-tag { font-weight: 400; }

.config-remark {
  margin-top: 4px;
  font-size: 12px;
  color: var(--gw-text-tertiary);
  line-height: 1.6;
}

.config-control {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.config-time { min-width: 130px; text-align: right; }

.text-edit {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
