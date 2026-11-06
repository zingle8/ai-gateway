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

    <!-- Top 服务 -->
    <section class="gw-card">
      <div class="gw-card-hd"><span>Top 服务</span></div>
      <div class="gw-card-bd no-pad">
        <el-table :data="topServices" size="small" style="width: 100%">
          <el-table-column prop="name" label="服务ID" min-width="180" show-overflow-tooltip />
          <el-table-column prop="serviceLabel" label="服务名" width="110" show-overflow-tooltip />
          <el-table-column prop="appName" label="所属应用" width="140" show-overflow-tooltip />
          <el-table-column prop="count" label="请求数" width="90" />
          <el-table-column label="平均耗时" width="110">
            <template #default="{ row }">{{ row.avgRt }} ms</template>
          </el-table-column>
          <el-table-column label="错误率" width="90">
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
          <el-table-column prop="name" label="接口" min-width="240" show-overflow-tooltip />
          <el-table-column prop="serviceLabel" label="所属服务" width="130" show-overflow-tooltip />
          <el-table-column prop="appName" label="所属应用" width="130" show-overflow-tooltip />
          <el-table-column prop="count" label="请求数" width="90" />
          <el-table-column label="平均耗时" width="110">
            <template #default="{ row }">{{ row.avgRt }} ms</template>
          </el-table-column>
          <el-table-column label="错误率" width="90">
            <template #default="{ row }">
              <span :class="row.errorRate > 0 ? 'gw-down' : ''">{{ row.errorRate }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="占比" width="90">
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
import { fetchStatsFilters, fetchStatsSummary, fetchStatsTrend } from '../api'
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

const trend = ref({ times: [], values: [], requestValues: [] })
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
    trend.value = { times: [], values: [], requestValues: [] }
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

onMounted(async () => {
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
</style>
