<template>
  <div class="page">
    <p class="demo-tip">
      <span class="gw-sm gw-muted">
        统一日志规范：按小时滚动（运行日志保留 7 天、ERROR 保留 14 天，单类上限 1~2GB）；日志格式含 traceId；敏感字段打印前脱敏；高频节点按秒采样
      </span>
    </p>

    <!-- 动态级别 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>动态日志级别</span>
        <div class="hd-actions">
          <span class="gw-sm gw-muted">保存后各服务 10 秒内应用，免重启</span>
          <el-button size="small" type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </div>
      <div class="gw-card-bd">
        <el-table :data="levelRows" size="small" style="width: 100%">
          <el-table-column label="Logger（包 / 类名，root 为全局）" min-width="320">
            <template #default="{ row }">
              <el-input v-model="row.logger" size="small" placeholder="com.zimo.gateway.proxy" />
            </template>
          </el-table-column>
          <el-table-column label="级别" width="160">
            <template #default="{ row }">
              <el-select v-model="row.level" size="small" style="width: 120px">
                <el-option v-for="lv in LEVELS" :key="lv" :label="lv" :value="lv" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ $index }">
              <el-button link type="danger" @click="levelRows.splice($index, 1)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" plain class="add-row" @click="addRow">+ 新增 logger</el-button>
      </div>
    </section>

    <!-- 日志量 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>日志文件概览</span>
        <div class="hd-actions">
          <span class="gw-mono gw-sm gw-muted">{{ logInfo.logHome || 'logs/app' }}</span>
          <el-button size="small" plain :loading="loadingFiles" @click="loadFiles">刷新</el-button>
        </div>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="logInfo.modules || []" v-loading="loadingFiles" size="small" style="width: 100%">
          <el-table-column prop="module" label="模块" min-width="220" />
          <el-table-column prop="files" label="文件数" width="100" />
          <el-table-column label="占用" width="120">
            <template #default="{ row }">
              <span :class="{ 'gw-warn': row.sizeMb > 512 }">{{ row.sizeMb }} MB</span>
            </template>
          </el-table-column>
          <el-table-column label="ERROR 日志" width="130">
            <template #default="{ row }">
              <span :class="{ 'gw-danger': row.errorMb > 0 }">{{ row.errorMb }} MB</span>
            </template>
          </el-table-column>
          <el-table-column prop="latest" label="最近一批" width="180" />
        </el-table>
        <div v-if="!(logInfo.modules || []).length" class="empty-tip gw-sm gw-muted">
          未在 {{ logInfo.logHome || 'logs/app' }} 找到日志文件（服务需从 backend 目录启动，logback 输出到 logs/app）
        </div>
      </div>
      <div class="gw-card-ft">
        <span class="gw-sm gw-muted">
          滚动策略：运行 INFO / 采集上报 INFO 保留 {{ retention.runInfoHistoryHours || 168 }} 小时、ERROR 保留
          {{ retention.runErrorHistoryHours || 336 }} 小时；单类上限 {{ retention.infoSizeCap || '2GB' }}（INFO）/
          {{ retention.warnSizeCap || '1GB' }}（WARN、ERROR）
        </span>
      </div>
    </section>

    <!-- 脱敏与限速 -->
    <section class="gw-card">
      <div class="gw-card-hd"><span>脱敏与打印管控</span><el-tag size="small" type="success" effect="plain">已生效</el-tag></div>
      <div class="gw-card-bd">
        <ul class="rules">
          <li><b>traceId 贯穿</b>：各服务 Servlet Filter / 网关 WebFilter 从 <span class="gw-mono">X-Trace-Id</span> 透传或生成 32 位标识写入 MDC，日志格式 <span class="gw-mono">[traceId]</span> 串联一次请求的全部日志。</li>
          <li><b>敏感信息脱敏</b>：token / 密钥保留前 6 后 4、手机号保留前 3 后 4；配置项 key 命中 password / secret / token / key 时日志值整体遮罩。</li>
          <li><b>高频日志限速</b>：鉴权拦截、限流拒绝、IP 白名单拒绝等节点按「路径 / 主体」每秒 3~5 条采样，超出丢弃，防刷屏流量写爆磁盘。</li>
          <li><b>ERROR 日志</b>：单独文件保留 14 天，作为 v0.3 告警体系的数据源（当前不做告警推送）。</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchLogFiles, fetchLogLevels, saveLogLevels } from '../api.js'

const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF']

const levelRows = ref([])
const saving = ref(false)
const logInfo = ref({})
const loadingFiles = ref(false)

const retention = computed(() => logInfo.value.retention || {})

const addRow = () => levelRows.value.push({ logger: '', level: 'INFO' })

const loadLevels = async () => {
  const levels = await fetchLogLevels()
  levelRows.value = Object.entries(levels || {}).map(([logger, level]) => ({ logger, level }))
}

const loadFiles = async () => {
  loadingFiles.value = true
  try {
    logInfo.value = await fetchLogFiles()
  } finally {
    loadingFiles.value = false
  }
}

const save = async () => {
  const payload = {}
  for (const row of levelRows.value) {
    const logger = (row.logger || '').trim()
    if (!logger) continue
    payload[logger] = row.level
  }
  saving.value = true
  try {
    await saveLogLevels(payload)
    ElMessage.success('已保存，各服务 10 秒内应用（免重启）')
    await loadLevels()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadLevels().catch(() => {}), loadFiles().catch(() => {})])
})
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

.add-row { margin-top: 10px; }

.empty-tip { padding: 18px 16px; }

.rules {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 2;
  color: var(--gw-text-secondary);
}

.rules b { color: var(--gw-text); }

.gw-warn { color: var(--gw-warning, #e6a23c); }
</style>
