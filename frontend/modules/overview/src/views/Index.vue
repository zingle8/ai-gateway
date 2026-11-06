<template>
  <div class="page">
    <!-- 时间区间：全页指标卡、趋势图、实例榜统一口径 -->
    <div class="range-bar">
      <span class="gw-sm gw-muted">统计区间：{{ rangeDesc }}</span>
      <TimeRangePicker v-model="range" @change="switchRange" />
    </div>

    <!-- 指标卡 -->
    <div class="gw-stats">
      <div v-for="s in stats" :key="s.label" class="gw-stat">
        <div>
          <div class="gw-stat-label" :title="s.label">{{ s.label }}</div>
          <div class="gw-stat-value">{{ s.value }}</div>
          <div class="gw-stat-trend" :class="s.dir">{{ s.trend }}</div>
        </div>
        <div class="gw-icon" :class="s.icon"><el-icon><component :is="s.iconComp" /></el-icon></div>
      </div>
    </div>

    <div class="grid-2">
      <!-- 请求量趋势（区间内每桶请求数） -->
      <section class="gw-card">
        <div class="gw-card-hd">
          <span>请求量趋势</span>
          <span class="gw-sm gw-muted">{{ rangeDesc }}</span>
        </div>
        <div class="gw-card-bd">
          <TrendChart
            :times="trend.times"
            :values="requestTrend"
            unit=" 次"
            series-name="请求量"
            :height="160"
          />
          <div class="gw-sm gw-muted" style="margin-top: 4px">{{ sampleDesc }}</div>
        </div>
      </section>

      <!-- QPS 趋势（同一份数据按桶宽折算） -->
      <section class="gw-card">
        <div class="gw-card-hd">
          <span>QPS 趋势</span>
          <span class="gw-sm gw-muted">{{ rangeDesc }}</span>
        </div>
        <div class="gw-card-bd">
          <TrendChart
            :times="trend.times"
            :values="trend.values"
            series-name="QPS"
            :height="160"
          />
          <div class="gw-sm gw-muted" style="margin-top: 4px">{{ sampleDesc }}</div>
        </div>
      </section>
    </div>

    <!-- 运行时指标 -->
    <section class="gw-card">
      <div class="gw-card-hd"><span>Spring Boot 运行时指标</span></div>
      <div class="gw-card-bd">
        <div class="kv-grid">
          <div v-for="m in runtime" :key="m.label" class="kv-item">
            <span class="gw-sm gw-muted">{{ m.label }}</span>
            <b>{{ m.value }} <span class="gw-sm gw-muted">{{ m.hint }}</span></b>
          </div>
        </div>
      </div>
    </section>

    <!-- 已接入服务实例 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>已接入服务实例</span>
        <span class="gw-sm gw-muted">按{{ rangeDesc }}调用量排名</span>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="instances" size="small" style="width: 100%">
          <el-table-column prop="name" label="服务名" min-width="160" />
          <el-table-column label="实例地址" width="180">
            <template #default="{ row }">{{ row.ip }}:{{ row.port }}</template>
          </el-table-column>
          <el-table-column prop="appName" label="归属应用" width="140" />
          <el-table-column prop="calls" label="区间调用" width="110" />
          <el-table-column prop="qps" label="QPS" width="90" />
          <el-table-column label="平均耗时" width="110">
            <template #default="{ row }">{{ row.avgRt }} ms</template>
          </el-table-column>
          <el-table-column prop="lastHeartbeat" label="最后心跳" width="180" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="instances.length === 0" description="还没有服务接入" :image-size="70" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { TrendChart, TimeRangePicker } from '@zimo/ui'
import { DEFAULT_TIME_RANGE, timeRangeLabel, timeRangeOf, useAutoRefresh } from '@zimo/shared'
import { fetchOverviewSummary, fetchOverviewTrend, fetchOverviewRuntime, fetchOverviewInstances } from '../api'
import { Odometer, TrendCharts, Timer, Warning, Document, User, Files, Download, Upload } from '@element-plus/icons-vue'

const summary = ref({
  qps: 0,
  requests: 0,
  todayRequests: 0,
  avgRt: 0,
  p95: 0,
  p99: 0,
  errorRate: 0,
  appCount: 0,
  serviceCount: 0,
  apiCount: 0,
  inbound: '0 B',
  outbound: '0 B'
})

const trend = ref({ times: [], values: [], requestValues: [] })
// 请求量趋势与 QPS 趋势同源：后端一次查询返回两套序列，这里取请求数那份
const requestTrend = computed(() => trend.value.requestValues || [])
const runtime = ref([])
const instances = ref([])

const stats = computed(() => {
  const s = summary.value
  return [
    { label: '区间 QPS', value: Number(s.qps).toLocaleString(), trend: rangeDesc.value, dir: '', icon: 'primary', iconComp: Odometer },
    { label: '区间请求数', value: Number(s.requests ?? s.todayRequests).toLocaleString(), trend: rangeDesc.value, dir: '', icon: 'info', iconComp: TrendCharts },
    { label: 'P95 / P99 延迟', value: s.p95 + 'ms / ' + s.p99 + 'ms', trend: '平均 ' + s.avgRt + 'ms · ' + rangeDesc.value, dir: '', icon: 'success', iconComp: Timer },
    { label: '错误率（status ≥ 400）', value: s.errorRate + '%', trend: rangeDesc.value, dir: '', icon: 'warning', iconComp: Warning },
    { label: '应用个数', value: s.appCount, trend: '已接入', dir: '', icon: 'purple', iconComp: User },
    { label: '服务个数', value: s.serviceCount, trend: '在线 ' + (s.onlineCount ?? s.serviceCount), dir: '', icon: 'cyan', iconComp: Document },
    { label: '接口个数', value: s.apiCount, trend: '已登记', dir: '', icon: 'success', iconComp: Files },
    { label: '区间入站流量', value: s.inbound, trend: '请求体合计', dir: '', icon: 'info', iconComp: Download },
    { label: '区间出站流量', value: s.outbound, trend: '响应体合计', dir: '', icon: 'purple', iconComp: Upload }
  ]
})

/** 服务状态标签颜色：健康绿、离线灰、其余橙 */
const statusType = (status) => {
  if (status === '健康') return 'success'
  if (status === '离线') return 'info'
  return 'warning'
}

const load = async () => {
  try {
    summary.value = await fetchOverviewSummary(range.value)
  } catch (e) {
    /* 后端未启动时保留默认数据 */
  }
  try {
    trend.value = await fetchOverviewTrend(range.value)
  } catch (e) {
    /* 同上 */
  }
  try {
    runtime.value = await fetchOverviewRuntime()
  } catch (e) {
    /* 同上 */
  }
  try {
    instances.value = await fetchOverviewInstances(range.value)
  } catch (e) {
    /* 同上 */
  }
}

/* --------------------------------------------------- 时间区间切换 */

/** 全页统一区间：指标卡、QPS 趋势、实例榜都按所选区间统计 */
const range = ref(DEFAULT_TIME_RANGE)
const rangeDesc = computed(() => timeRangeLabel(range.value))
const sampleDesc = computed(() => timeRangeOf(range.value).desc)

const switchRange = () => load()

// 数据自动刷新：频率来自系统设置 platform.ui.refresh.interval（30 秒内生效）
useAutoRefresh(load)
</script>

<style scoped>
/* 顶部时间区间工具条：左文案 + 右选择器，与各页面 filter-bar 视觉一致 */
.range-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

/* 折线图已抽到 @zimo/ui 的 TrendChart（含 Y 轴刻度、X 轴时间、悬浮 tooltip） */

.kv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px 18px;
}

.kv-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
</style>
