<template>
  <div class="page">
    <!-- 时间区间：指标卡、趋势、分布、Top 榜统一口径 -->
    <div class="range-bar">
      <span class="gw-sm gw-muted">统计区间：{{ rangeLabel }}</span>
      <TimeRangePicker v-model="range" @change="switchRange" />
    </div>

    <!-- 维度过滤 -->
    <section class="gw-card">
      <div class="gw-card-bd">
        <div class="filter-bar">
          <el-select v-model="query.appId" placeholder="全部应用" clearable filterable style="width: 165px;">
            <el-option v-for="a in apps" :key="a.appId" :label="a.appName || a.appId" :value="a.appId" />
          </el-select>
          <el-select v-model="query.serviceId" placeholder="全部服务ID" clearable filterable style="width: 180px;">
            <el-option v-for="s in services" :key="s" :label="s" :value="s" />
          </el-select>
          <el-select v-model="query.api" placeholder="全部接口" clearable filterable style="width: 250px;">
            <el-option v-for="a in apis" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="query.method" placeholder="全部方法" clearable style="width: 125px;">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>
    </section>

    <!-- 指标卡 -->
    <div class="gw-stats">
      <div v-for="s in cards" :key="s.label" class="gw-stat">
        <div>
          <div class="gw-stat-label" :title="s.label">{{ s.label }}</div>
          <div class="gw-stat-value">{{ s.value }}</div>
          <div class="gw-stat-trend">{{ s.trend }}</div>
        </div>
        <div class="gw-icon" :class="s.icon"><el-icon><component :is="s.iconComp" /></el-icon></div>
      </div>
    </div>

    <!-- 请求量趋势（整行） -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>请求量趋势</span>
        <span class="gw-sm gw-muted">{{ sampleDesc }}</span>
      </div>
      <div class="gw-card-bd">
        <TrendChart
          :times="trend.times"
          :values="requestTrend"
          unit=" 次"
          series-name="请求数"
          :height="160"
        />
        <div class="gw-sm gw-muted" style="margin-top: 4px">{{ activeDesc }}</div>
      </div>
    </section>

    <!-- QPS 趋势（整行，与请求量趋势同源一次查询） -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>QPS 趋势</span>
        <span class="gw-sm gw-muted">{{ sampleDesc }}</span>
      </div>
      <div class="gw-card-bd">
        <TrendChart
          :times="trend.times"
          :values="trend.values"
          series-name="QPS"
          :height="160"
        />
        <div class="gw-sm gw-muted" style="margin-top: 4px">{{ activeDesc }}</div>
      </div>
    </section>

    <!-- 入站 & 出站流量趋势（整行，双折线：蓝=入站、绿=出站） -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>入站 &amp; 出站流量趋势</span>
        <span class="gw-sm gw-muted">{{ sampleDesc }}</span>
      </div>
      <div class="gw-card-bd">
        <TrendChart
          :times="trend.times"
          :values="trend.inboundValues"
          :values2="trend.outboundValues"
          series-name="入站"
          series-name2="出站"
          unit=" KB"
          scale-second
          :height="160"
        />
        <div class="gw-sm gw-muted" style="margin-top: 4px">{{ activeDesc }}</div>
      </div>
    </section>

    <!-- 请求方法分布 + 状态码分布：并排一行 -->
    <div class="grid-2">
      <section class="gw-card">
        <div class="gw-card-hd"><span>请求方法分布</span></div>
        <div class="gw-card-bd">
          <div v-for="d in methodDist" :key="d.name" class="bar-row">
            <span class="bar-label mono">{{ d.name }}</span>
            <div class="bar-track">
              <div class="bar-fill info" :style="{ width: barWidth(d.count, methodMax) }"></div>
            </div>
            <span class="bar-value">{{ d.count }}</span>
          </div>
          <el-empty v-if="methodDist.length === 0" description="该区间无调用" :image-size="60" />
        </div>
      </section>

      <section class="gw-card">
        <div class="gw-card-hd"><span>状态码分布</span></div>
        <div class="gw-card-bd">
          <div v-for="d in statusDist" :key="d.name" class="bar-row">
            <span class="bar-label mono">{{ d.name }}</span>
            <div class="bar-track">
              <div class="bar-fill" :class="statusBarClass(d.name)" :style="{ width: barWidth(d.count, statusMax) }"></div>
            </div>
            <span class="bar-value">{{ d.count }}</span>
          </div>
          <el-empty v-if="statusDist.length === 0" description="该区间无调用" :image-size="60" />
        </div>
      </section>
    </div>

    <!-- 主机资源（v0.2 第 10 项：SDK 每 60 秒上报） -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>主机资源</span>
        <div class="host-actions">
          <el-select v-model="hostMinutes" size="small" style="width: 110px" @change="loadHosts">
            <el-option label="近 15 分钟" :value="15" />
            <el-option label="近 1 小时" :value="60" />
            <el-option label="近 6 小时" :value="360" />
            <el-option label="近 24 小时" :value="1440" />
          </el-select>
          <el-select v-model="hostname" size="small" style="width: 220px" placeholder="选择主机" @change="loadHostTrend">
            <el-option v-for="h in hostnames" :key="h" :label="h" :value="h" />
          </el-select>
          <el-button size="small" plain :loading="hostLoading" @click="loadHosts">刷新</el-button>
        </div>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="hosts" v-loading="hostLoading" size="small" style="width: 100%">
          <el-table-column prop="hostname" label="主机" min-width="200" />
          <el-table-column prop="appId" label="应用" width="140" />
          <el-table-column label="CPU 均值 / 峰值" width="150">
            <template #default="{ row }">{{ row.cpuAvg ?? '-' }}% / {{ row.cpuMax ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="内存" width="110">
            <template #default="{ row }">{{ row.memAvg ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="堆" width="100">
            <template #default="{ row }">{{ row.heapAvg ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="磁盘" width="100">
            <template #default="{ row }">{{ row.diskUsedPct ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="网络 入 / 出" width="190">
            <template #default="{ row }">
              {{ fmtBps(row.netRxAvg) }} / {{ fmtBps(row.netTxAvg) }}
            </template>
          </el-table-column>
          <el-table-column label="GPU" width="110">
            <template #default="{ row }">
              <span v-if="row.gpuCount > 0">{{ row.gpuUtil }}% × {{ row.gpuCount }}</span>
              <span v-else class="gw-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastReportTime" label="最近上报" width="170" />
        </el-table>
        <div v-if="!hostLoading && hosts.length === 0" class="host-empty gw-sm gw-muted">
          暂无主机指标：接入方引入 SDK 后每 60 秒自动上报（zimo.gateway.client.metrics.enabled 默认开启）
        </div>
      </div>
      <div class="gw-card-bd host-trend" v-if="hostname">
        <div class="gw-sm gw-muted host-trend-title">资源使用率趋势（{{ hostname }}）</div>
        <TrendChart :times="hostTrend.times" :values="hostTrendMetricValues" :unit="' %'"
                    :series-name="hostMetricLabel" :height="150" />
        <div class="gw-sm gw-muted host-trend-title">网络速率（入 / 出）</div>
        <TrendChart :times="hostTrend.times" :values="hostTrend.rxKb" :values2="hostTrend.txKb"
                    unit=" KB/s" series-name="入站" series-name2="出站" scale-second :height="150" />
        <el-radio-group v-model="hostMetric" size="small" class="host-metric-pick">
          <el-radio-button label="cpu">CPU</el-radio-button>
          <el-radio-button label="mem">内存</el-radio-button>
          <el-radio-button label="heap">堆</el-radio-button>
          <el-radio-button label="disk">磁盘</el-radio-button>
        </el-radio-group>
      </div>
    </section>

    <!-- Top 服务 -->
    <section class="gw-card">
      <div class="gw-card-hd"><span>Top 服务</span></div>
      <div class="gw-card-bd no-pad">
        <el-table :data="topServices" size="small" style="width: 100%">
          <el-table-column prop="name" label="服务ID" min-width="260" show-overflow-tooltip />
          <el-table-column prop="serviceLabel" label="服务名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="appName" label="所属应用" width="120" show-overflow-tooltip />
          <el-table-column prop="count" label="请求数" width="80" />
          <el-table-column label="平均耗时" width="100">
            <template #default="{ row }">{{ row.avgRt }} ms</template>
          </el-table-column>
          <el-table-column label="错误率" width="80">
            <template #default="{ row }">
              <span :class="row.errorRate > 0 ? 'gw-down' : ''">{{ row.errorRate }}%</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="topServices.length === 0" description="该区间无调用" :image-size="60" />
      </div>
    </section>

    <!-- Top 接口 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>Top 接口</span>
        <span class="gw-sm gw-muted">按请求量排序，取前 8</span>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="topApis" size="small" style="width: 100%">
          <el-table-column prop="name" label="接口" min-width="320" show-overflow-tooltip />
          <el-table-column prop="serviceLabel" label="所属服务" min-width="130" show-overflow-tooltip />
          <el-table-column prop="appName" label="所属应用" width="120" show-overflow-tooltip />
          <el-table-column prop="count" label="请求数" width="80" />
          <el-table-column label="平均耗时" width="100">
            <template #default="{ row }">{{ row.avgRt }} ms</template>
          </el-table-column>
          <el-table-column label="错误率" width="80">
            <template #default="{ row }">
              <span :class="row.errorRate > 0 ? 'gw-down' : ''">{{ row.errorRate }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="占比" width="80">
            <template #default="{ row }">{{ percentOf(row.count) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="topApis.length === 0" description="该区间无调用" :image-size="70" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { TrendChart, TimeRangePicker } from '@zimo/ui'
import { DEFAULT_TIME_RANGE, timeRangeLabel, timeRangeOf, useAutoRefresh } from '@zimo/shared'
import { fetchStatsFilters, fetchStatsSummary, fetchStatsTrend, fetchHostMetrics, fetchHostnames, fetchHostMetricTrend } from '../api'
import {
  DataAnalysis, TrendCharts, Timer, Warning, Download, Upload, CircleCheck, User, Document, Files
} from '@element-plus/icons-vue'

/** 统一时间区间：指标卡 / 趋势 / 分布 / Top 榜共用同一个 range */
const range = ref(DEFAULT_TIME_RANGE)
const sampleDesc = computed(() => timeRangeOf(range.value).desc)

const query = reactive({
  appId: '',
  serviceId: '',
  api: '',
  method: ''
})

// 筛选项：兜底空数组，接口失败时下拉为空但不阻塞页面
const apps = ref([])
const services = ref([])
const apis = ref([])
const methods = ref(['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'])

const data = ref({
  range: { code: DEFAULT_TIME_RANGE, label: timeRangeLabel(DEFAULT_TIME_RANGE) },
  summary: {},
  statusDist: [],
  methodDist: [],
  topServices: [],
  topApis: []
})

const trend = ref({ times: [], values: [], requestValues: [], inboundValues: [], outboundValues: [] })
// 请求量趋势与 QPS 趋势同源：后端一次查询返回两套序列（values=QPS、requestValues=请求量）
const requestTrend = computed(() => trend.value.requestValues || [])
const statusDist = computed(() => data.value.statusDist || [])
const methodDist = computed(() => data.value.methodDist || [])
const topServices = computed(() => data.value.topServices || [])
const topApis = computed(() => data.value.topApis || [])
const rangeLabel = computed(() => data.value.range?.label || timeRangeLabel(range.value))
const statusMax = computed(() => Math.max(1, ...statusDist.value.map((d) => d.count)))
const methodMax = computed(() => Math.max(1, ...methodDist.value.map((d) => d.count)))

const cards = computed(() => {
  const s = data.value.summary || {}
  return [
    { label: '请求总数', value: num(s.totalRequests), trend: rangeLabel.value, icon: 'primary', iconComp: DataAnalysis },
    { label: '成功率', value: (s.successRate ?? 0) + '%', trend: '成功 ' + num(s.successCount) + ' 次', icon: 'success', iconComp: CircleCheck },
    { label: '错误请求数', value: num(s.errorCount), trend: '错误率 ' + (s.errorRate ?? 0) + '%', icon: 'warning', iconComp: Warning },
    { label: 'P95 / P99 耗时', value: (s.p95 ?? 0) + 'ms / ' + (s.p99 ?? 0) + 'ms', trend: '平均 ' + (s.avgRt ?? 0) + 'ms · 区间内调用', icon: 'info', iconComp: Timer },
    { label: '平均 QPS', value: s.qps ?? 0, trend: '区间总量 / 秒数', icon: 'purple', iconComp: TrendCharts },
    { label: '入站流量', value: s.inbound || '0 B', trend: '请求体合计', icon: 'info', iconComp: Download },
    { label: '出站流量', value: s.outbound || '0 B', trend: '响应体合计', icon: 'cyan', iconComp: Upload },
    { label: '应用个数', value: s.appCount ?? 0, trend: '全平台', icon: 'purple', iconComp: User },
    { label: '服务个数', value: s.serviceCount ?? 0, trend: '已注册', icon: 'success', iconComp: Document },
    { label: '接口个数', value: s.apiCount ?? 0, trend: '有调用记录', icon: 'info', iconComp: Files }
  ]
})

const num = (v) => Number(v ?? 0).toLocaleString()

const barWidth = (count, max) => Math.max(2, Math.round((count / max) * 100)) + '%'

const statusBarClass = (code) => {
  const c = Number(code)
  if (c >= 500) return 'danger'
  if (c >= 400) return 'warning'
  return 'success'
}

const percentOf = (count) => {
  const total = data.value.summary?.totalRequests || 0
  return total === 0 ? '0%' : ((count * 100) / total).toFixed(1) + '%'
}

const loadFilters = async () => {
  try {
    const d = await fetchStatsFilters()
    if (d.apps?.length) apps.value = d.apps
    if (d.services?.length) services.value = d.services
    if (d.apis?.length) apis.value = d.apis
    if (d.methods?.length) methods.value = d.methods
  } catch (e) { /* 字典不可用不阻塞 */ }
}

/** 请求参数：维度过滤 + 统一时间区间，指标卡与趋势共用 */
const buildParams = () => {
  const params = { range: range.value }
  Object.keys(query).forEach((k) => {
    const v = query[k]
    if (v !== '' && v !== null && v !== undefined) params[k] = v
  })
  return params
}

const loadTrend = async () => {
  try {
    trend.value = await fetchStatsTrend(buildParams())
  } catch (e) {
    trend.value = { times: [], values: [], requestValues: [], inboundValues: [], outboundValues: [] }
  }
}

const load = async () => {
  try {
    data.value = await fetchStatsSummary(buildParams())
  } catch (e) {
    data.value = {
      range: { code: range.value, label: timeRangeLabel(range.value) },
      summary: {}, statusDist: [], methodDist: [], topServices: [], topApis: []
    }
  }
  await loadTrend()
}

/** 切换区间：指标卡与趋势一起重算，保证页面只有一个时间口径 */
const switchRange = () => load()

const reset = () => {
  query.appId = ''
  query.serviceId = ''
  query.api = ''
  query.method = ''
  range.value = DEFAULT_TIME_RANGE
  load()
}

/* ---------------- 主机资源（v0.2 第 10 项） ---------------- */
const hosts = ref([])
const hostnames = ref([])
const hostLoading = ref(false)
const hostMinutes = ref(60)
const hostname = ref('')
const hostMetric = ref('cpu')
const hostTrend = ref({ times: [], cpu: [], mem: [], heap: [], disk: [], rxKb: [], txKb: [] })

const METRIC_LABELS = { cpu: 'CPU 使用率', mem: '内存使用率', heap: '堆使用率', disk: '磁盘使用率' }
const hostMetricLabel = computed(() => METRIC_LABELS[hostMetric.value] || '使用率')
const hostTrendMetricValues = computed(() => {
  const key = hostMetric.value
  return (hostTrend.value[key] || []).map((v) => (v == null ? 0 : v))
})

const fmtBps = (bps) => {
  const value = Number(bps || 0)
  if (value >= 1024 * 1024) return (value / 1024 / 1024).toFixed(1) + ' MB/s'
  if (value >= 1024) return (value / 1024).toFixed(1) + ' KB/s'
  return value + ' B/s'
}

const loadHostTrend = async () => {
  if (!hostname.value) return
  try {
    const vo = await fetchHostMetricTrend(hostname.value, hostMinutes.value)
    hostTrend.value = {
      times: vo?.times || [],
      cpu: vo?.cpu || [],
      mem: vo?.mem || [],
      heap: vo?.heap || [],
      disk: vo?.disk || [],
      rxKb: (vo?.rx || []).map((v) => Math.round((v / 1024) * 10) / 10),
      txKb: (vo?.tx || []).map((v) => Math.round((v / 1024) * 10) / 10)
    }
  } catch (e) {
    hostTrend.value = { times: [], cpu: [], mem: [], heap: [], disk: [], rxKb: [], txKb: [] }
  }
}

const loadHosts = async () => {
  hostLoading.value = true
  try {
    const [list, names] = await Promise.all([
      fetchHostMetrics(hostMinutes.value).catch(() => []),
      fetchHostnames().catch(() => [])
    ])
    hosts.value = list || []
    hostnames.value = names || []
    if (!hostname.value && hostnames.value.length) {
      hostname.value = hostnames.value[0]
    }
    await loadHostTrend()
  } finally {
    hostLoading.value = false
  }
}

onMounted(async () => {
  await loadHosts()
  await loadFilters()
  // 数据自动刷新：频率来自系统设置 platform.ui.refresh.interval（30 秒内生效）
  useAutoRefresh(load)
})
</script>

<style scoped>
/* 顶部时间区间工具条：与网关总览、服务发现、服务拓扑保持同一视觉 */
.range-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

/* 折线图已抽到 @zimo/ui 的 TrendChart（含 Y 轴刻度、X 轴时间、悬浮 tooltip） */

/* 横向条形：状态码 / 方法分布 */
.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 0;
}

.bar-label {
  width: 64px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--gw-text-secondary);
  text-align: right;
}

.bar-label.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.bar-track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--gw-border-light);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--gw-text-tertiary);
  transition: width .25s ease;
}

.bar-fill.success { background: var(--gw-success); }
.bar-fill.warning { background: var(--gw-warning); }
.bar-fill.danger { background: var(--gw-danger); }
.bar-fill.info { background: var(--gw-info); }

.bar-value {
  width: 64px;
  flex-shrink: 0;
  font-size: 12px;
  text-align: right;
  color: var(--gw-text);
}

.host-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.host-empty { padding: 18px 16px; }

.host-trend { border-top: 1px solid var(--gw-border, #ebeef5); }

.host-trend-title { margin: 6px 0; }

.host-metric-pick { margin-top: 10px; }
</style>
