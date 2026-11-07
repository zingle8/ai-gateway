<template>
  <div class="page">
    <p class="demo-tip">
      <span class="gw-sm gw-muted">
        配置元数据（键 / 来源 / 归属）统一存在 MySQL，配置值可托管在 DB、Redis Hash 或 Nacos；保存后各模块 30 秒内生效
      </span>
    </p>

    <!-- 来源概览 -->
    <div class="source-grid">
      <div
        v-for="s in sourceCards"
        :key="s.value"
        class="source-card"
        :class="{ active: filter === s.value }"
        @click="filter = s.value"
      >
        <div class="source-name">
          <span class="source-dot" :class="s.tone" />
          {{ s.label }}
        </div>
        <div class="source-count">{{ s.count }} 项</div>
        <div class="source-desc">{{ s.desc }}</div>
      </div>
    </div>

    <section class="gw-card">
      <div class="gw-card-hd">
        <span>平台配置</span>
        <div class="hd-actions">
          <span class="gw-sm gw-muted">当前生效的鉴权缓存：{{ effectiveCacheMode || '—' }}</span>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate">新增配置</el-button>
        </div>
      </div>

      <div v-loading="loading" class="gw-card-bd">
        <div v-for="item in filtered" :key="item.key" class="config-row">
          <div class="config-main">
            <div class="config-title">
              {{ item.key }}
              <el-tag size="small" effect="plain" class="type-tag">{{ item.valueType }}</el-tag>
              <el-tag size="small" :type="sourceTone(item.source)" effect="dark" class="type-tag">
                {{ item.source }}
              </el-tag>
              <el-tag v-if="item.valueState === 'STALE'" size="small" type="warning" effect="plain" class="type-tag">
                来源不可达 · 显示最后写入值
              </el-tag>
            </div>

            <div class="config-meta gw-sm">
              <span><b>平台</b> {{ item.platformName }}</span>
              <span><b>应用</b> {{ item.appName }}</span>
              <span><b>服务</b> {{ item.serviceName }}</span>
              <span class="meta-source">{{ item.sourceLabel }}</span>
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

            <!-- 枚举型（鉴权豁免权控制）→ 单选 -->
            <el-radio-group
              v-else-if="item.key === 'platform.auth.exemption.control'"
              :model-value="item.value"
              :disabled="saving === item.key"
              @change="(v) => save(item, v)"
            >
              <el-radio-button label="CLIENT">CLIENT（client/sdk 端控制）</el-radio-button>
              <el-radio-button label="PLATFORM">PLATFORM（平台端控制）</el-radio-button>
            </el-radio-group>

            <!-- 调度时间 → 专用时间选择器（走调度中心接口双写 Redis/MySQL） -->
            <div v-else-if="item.key === 'scheduler.clean.run-time'" class="text-edit">
              <el-time-select
                v-model="item.value"
                size="small"
                start="00:00"
                step="00:30"
                end="23:30"
                style="width: 130px"
                placeholder="选择时间"
              />
              <el-button
                size="small"
                type="primary"
                :loading="saving === item.key"
                @click="saveCleanRunTime(item)"
              >
                保存
              </el-button>
            </div>

            <!-- 其他 → 输入框 + 保存 -->
            <div v-else class="text-edit">
              <el-input v-model="item.value" size="small" style="width: 200px" />
              <el-button size="small" type="primary" :loading="saving === item.key" @click="save(item, item.value)">
                保存
              </el-button>
            </div>

            <span class="config-time gw-sm gw-muted">{{ item.updateTime || '—' }}</span>
            <el-button link type="danger" size="small" @click="remove(item)">删除</el-button>
          </div>
        </div>

        <el-empty v-if="!loading && !filtered.length" description="暂无该来源的配置" :image-size="70" />
      </div>

      <div class="gw-card-ft">
        <span class="gw-sm gw-muted">
          鉴权缓存：auto = Redis 可用则用 Redis，否则本地 Caffeine；MySQL 始终保存源数据。缓存 TTL 30 秒，停用 Key / 退出登录最迟 30 秒生效。
        </span>
      </div>
    </section>

    <!-- 新增配置 -->
    <el-dialog v-model="createDialog" title="新增配置" width="560px">
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="配置键" required>
          <el-input v-model="form.key" placeholder="如 order.pay.timeout.seconds" />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="form.value" placeholder="如 30" />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select v-model="form.valueType" style="width: 100%">
            <el-option label="STRING" value="STRING" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
            <el-option label="NUMBER" value="NUMBER" />
          </el-select>
        </el-form-item>
        <el-form-item label="存储来源">
          <el-radio-group v-model="form.source">
            <el-radio-button label="DB">DB</el-radio-button>
            <el-radio-button label="REDIS">Redis</el-radio-button>
            <el-radio-button label="NACOS">Nacos</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.source !== 'DB'" label="来源定位">
          <el-input v-model="form.sourceRef" :placeholder="refPlaceholder" />
          <div class="form-hint gw-sm gw-muted">{{ refHint }}</div>
        </el-form-item>
        <el-form-item label="所属平台">
          <el-input v-model="form.platformName" placeholder="API网关平台" />
        </el-form-item>
        <el-form-item label="所属应用">
          <el-input v-model="form.appName" placeholder="jv-api-gateway" />
        </el-form-item>
        <el-form-item label="所属服务">
          <el-input v-model="form.serviceName" placeholder="* 表示平台级，或 jv-api-gateway-user" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="这项配置控制什么" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@zimo/shared'
import { fetchConfigs, createConfig, deleteConfig, updateConfig, fetchMenuHealth } from '../api.js'

const loading = ref(false)
const saving = ref('')
const creating = ref(false)
const rows = ref([])
const filter = ref('ALL')
const effectiveCacheMode = ref('')
const createDialog = ref(false)

const form = reactive({
  key: '',
  value: '',
  valueType: 'STRING',
  source: 'DB',
  sourceRef: '',
  platformName: 'API网关平台',
  appName: 'jv-api-gateway',
  serviceName: '*',
  remark: ''
})

const SOURCE_META = {
  DB: { label: 'MySQL', tone: 'db', desc: 'tb_sys_config 表内直存' },
  REDIS: { label: 'Redis', tone: 'redis', desc: 'Hash 存储，多实例共享' },
  NACOS: { label: 'Nacos', tone: 'nacos', desc: '配置中心 properties 文本' }
}

const filtered = computed(() =>
  filter.value === 'ALL' ? rows.value : rows.value.filter((r) => r.source === filter.value)
)

const sourceCards = computed(() => {
  const total = rows.value.length
  const cards = [{ value: 'ALL', label: '全部来源', tone: 'all', count: total, desc: '按来源筛选配置项' }]
  for (const [value, meta] of Object.entries(SOURCE_META)) {
    cards.push({
      value,
      label: meta.label,
      tone: meta.tone,
      count: rows.value.filter((r) => r.source === value).length,
      desc: meta.desc
    })
  }
  return cards
})

const sourceTone = (source) => (source === 'REDIS' ? 'danger' : source === 'NACOS' ? 'success' : 'primary')

const refPlaceholder = computed(() =>
  form.source === 'REDIS' ? 'gw:config:platform（Hash 键，可留空用默认）' : 'gw-platform-config.properties|DEFAULT_GROUP'
)

const refHint = computed(() =>
  form.source === 'REDIS'
    ? '留空使用默认 Hash：gw:config:platform，field 为配置键'
    : '格式 dataId|groupName，留空使用默认 gw-platform-config.properties|DEFAULT_GROUP'
)

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

const openCreate = () => {
  Object.assign(form, {
    key: '',
    value: '',
    valueType: 'STRING',
    source: 'DB',
    sourceRef: '',
    platformName: 'API网关平台',
    appName: 'jv-api-gateway',
    serviceName: '*',
    remark: ''
  })
  createDialog.value = true
}

const submitCreate = async () => {
  if (!form.key || !form.value) {
    ElMessage.warning('配置键与配置值不能为空')
    return
  }
  creating.value = true
  try {
    await createConfig({ ...form })
    createDialog.value = false
    ElMessage.success(`已创建，写入 ${form.source}`)
    await load()
  } finally {
    creating.value = false
  }
}

const remove = async (item) => {
  await ElMessageBox.confirm(
    `确认删除「${item.key}」？将同时清理 ${item.source} 中的值，且不可恢复。`,
    '删除配置',
    { type: 'warning' }
  )
  await deleteConfig(item.key)
  ElMessage.success('已删除')
  await load()
}

/** 调度时间专用保存：走调度中心接口（MySQL + Redis 双写，立即生效） */
const saveCleanRunTime = async (item) => {
  if (!/^\d{2}:\d{2}$/.test(item.value || '')) {
    ElMessage.warning('请选择有效时间（HH:mm）')
    return
  }
  saving.value = item.key
  try {
    await request.put('/scheduler/config', { runTime: item.value })
    ElMessage.success(`调度时间已更新为每日 ${item.value}，Redis/MySQL 双写已生效`)
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

.source-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.source-card {
  padding: 12px 14px;
  background: var(--gw-card);
  border: 1px solid var(--gw-border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.source-card:hover { border-color: var(--gw-primary, #2563eb); }

.source-card.active {
  border-color: var(--gw-primary, #2563eb);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12);
}

.source-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--gw-text-tertiary);
}

.source-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
}

.source-dot.db { background: #2563eb; }
.source-dot.redis { background: #dc2626; }
.source-dot.nacos { background: #16a34a; }
.source-dot.all { background: #64748b; }

.source-count {
  margin: 6px 0 2px;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.source-desc {
  font-size: 11px;
  color: var(--gw-text-tertiary);
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
  flex-wrap: wrap;
}

.type-tag { font-weight: 400; }

.config-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 5px;
  color: var(--gw-text-tertiary);
  flex-wrap: wrap;
}

.config-meta b {
  font-weight: 500;
  color: var(--gw-text-secondary);
}

.meta-source { font-family: var(--gw-mono, monospace); }

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

.form-hint { line-height: 1.5; margin-top: 2px; }
</style>
