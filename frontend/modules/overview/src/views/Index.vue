<template>
  <div class="page">
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
      <!-- QPS 趋势 -->
      <section class="gw-card">
        <div class="gw-card-hd">
          <span>QPS 趋势</span>
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
          <TrendChart
            :times="trend.times"
            :values="trend.values"
            unit=" 次"
            series-name="请求数"
            :height="160"
          />
          <div class="gw-sm gw-muted" style="margin-top: 4px">{{ activeDesc }}</div>
        </div>
      </section>

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
    </div>

    <!-- 已接入服务实例 -->
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>已接入服务实例</span>
        <span class="gw-sm gw-muted">按近 5 分钟调用量排名</span>
      </div>
      <div class="gw-card-bd no-pad">
        <el-table :data="instances" size="small" style="width: 100%">
          <el-table-column prop="name" label="服务名" min-width="160" />
          <el-table-column label="实例地址" width="180">
            <template #default="{ row }">{{ row.ip }}:{{ row.port }}</template>
          </el-table-column>
          <el-table-column prop="appName" label="归属应用" width="140" />
          <el-table-column prop="calls" label="近5分钟调用" width="110" />
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
import { ref, computed, onMounted } from 'vue'
import { TrendChart } from '@zimo/ui'
import { fetchOverviewSummary, fetchOverviewTrend, fetchOverviewRuntime, fetchOverviewInstances } from '../api'
import { Odometer, TrendCharts, Timer, Warning, Document, User, Files, Download, Upload } from '@element-plus/icons-vue'

const summary = ref({
  qps: 12480,
  todayRequests: 8642193,
  avgRt: 42,
  p95: 168,
  p99: 268,
  errorRate: 0.37,
  appCount: 12,
  serviceCount: 18,
  apiCount: 42,
  inbound: '1.86 TB',
  outbound: '12.42 TB'
})

const trend = ref({ times: [], values: [] })
const runtime = ref([])
const instances = ref([])

const stats = computed(() => {
  const s = summary.value
  return [
    { label: '实时 QPS', value: Number(s.qps).toLocaleString(), trend: '近 60 秒', dir: '', icon: 'primary', iconComp: Odometer },
    { label: '今日请求数', value: Number(s.todayRequests).toLocaleString(), trend: '今日 00:00 起', dir: '', icon: 'info', iconComp: TrendCharts },
    { label: 'P95 / P99 延迟', value: s.p95 + 'ms / ' + s.p99 + 'ms', trend: '平均 ' + s.avgRt + 'ms · 今日调用', dir: '', icon: 'success', iconComp: Timer },
    { label: '错误率（status ≥ 400）', value: s.errorRate + '%', trend: '今日调用', dir: '', icon: 'warning', iconComp: Warning },
    { label: '应用个数', value: s.appCount, trend: '已接入', dir: '', icon: 'purple', iconComp: User },
    { label: '服务个数', value: s.serviceCount, trend: '在线 ' + (s.onlineCount ?? s.serviceCount), dir: '', icon: 'cyan', iconComp: Document },
    { label: '接口个数', value: s.apiCount, trend: '已登记', dir: '', icon: 'success', iconComp: Files },
    { label: '今日入站流量', value: s.inbound, trend: '请求体合计', dir: '', icon: 'info', iconComp: Download },
    { label: '今日出站流量', value: s.outbound, trend: '响应体合计', dir: '', icon: 'purple', iconComp: Upload }
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
    summary.value = await fetchOverviewSummary()
  } catch (e) {
    /* 后端未启动时保留默认数据 */
  }
  try {
    trend.value = await fetchOverviewTrend(activeStep.value)
  } catch (e) {
    /* 同上 */
  }
  try {
    runtime.value = await fetchOverviewRuntime()
  } catch (e) {
    /* 同上 */
  }
  try {
    instances.value = await fetchOverviewInstances()
  } catch (e) {
    /* 同上 */
  }
}

/* --------------------------------------------------- 趋势图粒度切换 */

/** step = 桶宽分钟数；desc = 时间轴覆盖范围（后端固定 120 个点） */
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

const switchStep = async (step) => {
  if (activeStep.value === step) return
  activeStep.value = step
  try {
    trend.value = await fetchOverviewTrend(step)
  } catch (e) {
    /* 切换失败保留原图 */
  }
}

onMounted(load)
</script>

<style scoped>
/* 粒度切换按钮组 */
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

@media (max-width: 1400px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
