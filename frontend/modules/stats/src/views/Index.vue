<template>
  <div class="page">
    <!-- 维度过滤 -->
    <section class="gw-card">
      <div class="gw-card-bd">
        <div class="filter-bar">
          <el-select v-model="query.appId" placeholder="全部应用" clearable filterable style="width: 165px;">
            <el-option v-for="a in apps" :key="a.appId" :label="a.appName || a.appId" :value="a.appId" />
          </el-select>
          <el-select v-model="query.serviceName" placeholder="全部服务" clearable filterable style="width: 180px;">
            <el-option v-for="s in services" :key="s" :label="s" :value="s" />
          </el-select>
          <el-select v-model="query.api" placeholder="全部接口" clearable filterable style="width: 250px;">
            <el-option v-for="a in apis" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="query.method" placeholder="全部方法" clearable style="width: 125px;">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
          <el-select v-model="query.hours" style="width: 130px;">
            <el-option v-for="h in hourOptions" :key="h.value" :label="h.label" :value="h.value" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <span class="gw-sm gw-muted">指标区间：{{ rangeLabel }}</span>
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

    <div class="grid-2">
      <!-- 请求趋势 -->
      <section class="gw-card">
        <div class="gw-card-hd">
          <span>请求量趋势</span>
          <div class="trend-switch">
            <button
              v-for="g in granularities"
              :key="g.step"
              class="trend-btn"
              :class="{ active: activeStep === g.step }"
              @click="switchStep(g.step)"
            >{{ g.label }}</button>
          </div>
        </div>
        <div class="gw-card-bd">
          <svg class="line-chart" viewBox="0 0 600 130" preserveAspectRatio="none">
            <polyline :points="areaPoints" class="area" />
            <polyline :points="linePoints" class="line" />
          </svg>
          <div class="axis">
            <span v-for="(t, i) in trend.times" :key="i">{{ i % labelEvery === 0 ? t : '' }}</span>
          </div>
          <div class="gw-sm gw-muted" style="margin-top: 4px">{{ activeDesc }}</div>
        </div>
      </section>

      <!-- 请求方法分布 -->
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
    </div>

    <div class="grid-2">
      <!-- 状态码分布 -->
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

      <!-- Top 服务 -->
      <section class="gw-card">
        <div class="gw-card-hd"><span>Top 服务</span></div>
        <div class="gw-card-bd no-pad">
          <el-table :data="topServices" size="small" style="width: 100%">
            <el-table-column prop="name" label="服务名" min-width="150" show-overflow-tooltip />
            <el-table-column prop="count" label="请求数" width="90" />
            <el-table-column label="平均耗时" width="100">
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
    </div>

    <!-- Top 接口 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>Top 接口</span>
        <span class="gw-sm gw-muted">按请求量排序，取前 8</span>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="topApis" size="small" style="width: 100%">
          <el-table-column prop="name" label="接口" min-width="280" show-overflow-tooltip />
          <el-table-column prop="count" label="请求数" width="100" />
          <el-table-column label="平均耗时" width="110">
            <template #default="{ row }">{{ row.avgRt }} ms</template>
          </el-table-column>
          <el-table-column label="错误率" width="100">
            <template #default="{ row }">
              <span :class="row.errorRate > 0 ? 'gw-down' : ''">{{ row.errorRate }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="占比" width="110">
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
import { fetchStatsFilters, fetchStatsSummary, fetchStatsTrend } from '../api'
import {
  DataAnalysis, TrendCharts, Timer, Warning, Download, Upload, CircleCheck, User, Document, Files
} from '@element-plus/icons-vue'

const query = reactive({
  appId: '',
  serviceName: '',
  api: '',
  method: '',
  hours: 24
})

const hourOptions = [
  { value: 1, label: '近 1 小时' },
  { value: 6, label: '近 6 小时' },
  { value: 12, label: '近 12 小时' },
  { value: 24, label: '近 24 小时' }
]

// 筛选项：兜底空数组，接口失败时下拉为空但不阻塞页面
const apps = ref([])
const services = ref([])
const apis = ref([])
const methods = ref(['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'])

const data = ref({
  range: { hours: 24, label: '近 24 小时' },
  summary: {},
  statusDist: [],
  methodDist: [],
  topServices: [],
  topApis: []
})

/* --------------------------------------------------- 趋势粒度切换 */

/** step = 桶宽分钟数；desc = 时间轴覆盖范围（后端固定 120 个点），与总览 QPS 趋势一致 */
const granularities = [
  { step: 1,    label: '1分钟',  desc: '1 分钟 / 点 · 近 2 小时' },
  { step: 5,    label: '5分钟',  desc: '5 分钟 / 点 · 近 6 小时' },
  { step: 10,   label: '10分钟', desc: '10 分钟 / 点 · 近 12 小时' },
  { step: 30,   label: '30分钟', desc: '30 分钟 / 点 · 近 24 小时' },
  { step: 60,   label: '1小时',  desc: '1 小时 / 点 · 近 3 天' },
  { step: 120,  label: '2小时',  desc: '2 小时 / 点 · 近 7 天' },
  { step: 1440, label: '24小时', desc: '24 小时 / 点 · 近 30 天' }
]

const activeStep = ref(1)
const activeDesc = computed(() =>
  (granularities.find(g => g.step === activeStep.value) || granularities[0]).desc)

// 趋势独立请求：切换粒度只重算趋势，不重算指标卡
const trend = ref({ times: [], values: [] })
const statusDist = computed(() => data.value.statusDist || [])
const methodDist = computed(() => data.value.methodDist || [])
const topServices = computed(() => data.value.topServices || [])
const topApis = computed(() => data.value.topApis || [])
const rangeLabel = computed(() => data.value.range?.label || '')
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

/** 趋势折线：与 overview 同一套算法，保证两页视觉一致 */
const linePoints = computed(() => {
  const values = trend.value.values || []
  if (!values.length) return ''
  const max = Math.max(...values)
  const min = Math.min(...values)
  const span = max - min || 1
  return values
    .map((v, i) => {
      const x = (i / Math.max(1, values.length - 1)) * 600
      const y = 118 - ((v - min) / span) * 100
      return x.toFixed(1) + ',' + y.toFixed(1)
    })
    .join(' ')
})

const areaPoints = computed(() => {
  const pts = linePoints.value
  return pts ? '0,130 ' + pts + ' 600,130' : ''
})

const labelEvery = computed(() => {
  const total = (trend.value.times || []).length
  return Math.max(1, Math.ceil(total / 8))
})

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

/** 过滤栏参数：hours 只作用于指标卡，趋势窗口由 step 决定 */
const buildParams = () => {
  const params = {}
  Object.keys(query).forEach((k) => {
    const v = query[k]
    if (v !== '' && v !== null && v !== undefined) params[k] = v
  })
  return params
}

const loadTrend = async () => {
  const params = buildParams()
  delete params.hours
  try {
    trend.value = await fetchStatsTrend({ ...params, step: activeStep.value })
  } catch (e) {
    trend.value = { times: [], values: [] }
  }
}

const load = async () => {
  try {
    data.value = await fetchStatsSummary(buildParams())
  } catch (e) {
    data.value = {
      range: { hours: query.hours, label: '近 ' + query.hours + ' 小时' },
      summary: {}, statusDist: [], methodDist: [], topServices: [], topApis: []
    }
  }
  await loadTrend()
}

const switchStep = async (step) => {
  if (activeStep.value === step) return
  activeStep.value = step
  await loadTrend()
}

const reset = () => {
  query.appId = ''
  query.serviceName = ''
  query.api = ''
  query.method = ''
  query.hours = 24
  load()
}

onMounted(async () => {
  await loadFilters()
  load()
})
</script>

<style scoped>
/* 粒度切换按钮组：与网关总览 QPS 趋势保持一致 */
.trend-switch {
  display: inline-flex;
  gap: 2px;
  background: var(--gw-bg-page, #f2f3f5);
  border-radius: 6px;
  padding: 2px;
}

.trend-btn {
  border: none;
  background: transparent;
  color: var(--gw-text-secondary, #606266);
  font-size: 12px;
  line-height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
}

.trend-btn:hover {
  color: var(--gw-primary, #409eff);
}

.trend-btn.active {
  background: #fff;
  color: var(--gw-primary, #409eff);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, .08);
}

.line-chart {
  width: 100%;
  height: 130px;
  display: block;
}

.line {
  fill: none;
  stroke: var(--gw-info);
  stroke-width: 1.6;
}

.area {
  fill: rgba(37, 99, 235, .10);
  stroke: none;
}

.axis {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 10px;
  color: var(--gw-text-tertiary);
}

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

@media (max-width: 1400px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
