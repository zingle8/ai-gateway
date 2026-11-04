<template>
  <div class="page">
    <!-- 时间区间：趋势、列表与详情共用同一口径 -->
    <div class="range-bar">
      <span class="gw-sm gw-muted">统计区间：{{ rangeDesc }}</span>
      <TimeRangePicker v-model="range" @change="switchRange" />
    </div>

    <!-- 调用趋势：请求量在前、QPS 在后，随上方筛选（应用 / 服务）联动 -->
    <div class="trend-stack">
      <section class="gw-card">
        <div class="gw-card-hd">
          <span>请求量趋势</span>
          <span class="gw-sm gw-muted">{{ trendScope }}</span>
        </div>
        <div class="gw-card-bd">
          <TrendChart
            :times="discoTrend.times"
            :values="discoTrend.requestValues || []"
            unit=" 次"
            series-name="请求量"
            :height="150"
          />
        </div>
      </section>

      <section class="gw-card">
        <div class="gw-card-hd">
          <span>QPS 趋势</span>
          <span class="gw-sm gw-muted">{{ trendScope }}</span>
        </div>
        <div class="gw-card-bd">
          <TrendChart
            :times="discoTrend.times"
            :values="discoTrend.values"
            series-name="QPS"
            :height="150"
          />
        </div>
      </section>
    </div>

    <section class="gw-card">
      <div class="gw-card-hd">
        <span>服务发现</span>
        <span class="gw-muted gw-sm">共 {{ total }} 个注册服务</span>
      </div>

      <!-- 过滤栏 -->
      <div class="gw-card-bd">
        <div class="filter-bar">
          <el-input v-model="query.keyword" placeholder="应用ID / 应用名 / 服务名" clearable style="width: 220px;" />
          <el-select v-model="query.appId" placeholder="全部应用" clearable filterable style="width: 200px;" @change="onAppChange">
            <el-option
              v-for="app in appOptions"
              :key="app.appId"
              :label="app.appName ? `${app.appName}（${app.appId}）` : app.appId"
              :value="app.appId"
            />
          </el-select>
          <el-select v-model="query.serviceName" placeholder="全部服务" clearable filterable style="width: 190px;">
            <el-option v-for="s in serviceOptions" :key="s.serviceName" :label="s.serviceName" :value="s.serviceName" />
          </el-select>
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px;">
            <el-option label="健康" value="健康" />
            <el-option label="亚健康" value="亚健康" />
            <el-option label="异常" value="异常" />
          </el-select>
          <el-select v-model="query.language" placeholder="全部语言" clearable style="width: 150px;">
            <el-option v-for="lang in langOptions" :key="lang" :label="lang" :value="lang" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <!-- 列表 -->
      <div class="gw-card-bd no-pad">
        <el-table :data="rows" size="small" style="width: 100%" @row-click="openDetail">
          <el-table-column prop="appId" label="应用ID" width="120" />
          <el-table-column prop="appName" label="应用名" width="110" />
          <el-table-column prop="serviceName" label="服务名" min-width="160" show-overflow-tooltip />
          <el-table-column label="接口数" width="80">
            <template #default="{ row }">{{ row.apiCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="调用次数" width="95">
            <template #default="{ row }">{{ (row.calls ?? 0).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="port" label="端口" width="80" />
          <el-table-column prop="language" label="语言类型" width="110" />
          <el-table-column prop="version" label="版本号" width="100" />
          <el-table-column label="健康状态" width="110">
            <template #default="{ row }">
              <span class="status-dot" :class="dotClass(row.status)"></span> {{ row.status }}
            </template>
          </el-table-column>
          <el-table-column label="入站 / 出网流量" width="170">
            <template #default="{ row }">{{ row.inFlow }} / {{ row.outFlow }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页条（吸底） -->
      <div class="gw-card-ft">
        <span class="gw-muted gw-sm">共 {{ total }} 条 · 每页 {{ query.size }} 条 · 第 {{ query.current }} / {{ pages }} 页</span>
        <el-pagination
          layout="prev, pager, next"
          :current-page="query.current"
          :page-size="query.size"
          :total="total"
          @current-change="onPage"
        />
      </div>
    </section>

    <!-- 服务详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="'服务详情 · ' + (detail.serviceName || '')" size="min(560px, 94vw)">
      <div v-if="detail.serviceName" class="drawer-sec">
        <div class="drawer-sec-title">基本信息</div>
        <div class="kv-grid">
          <div v-for="f in detailFields" :key="f.label" class="kv-item">
            <span class="gw-sm gw-muted">{{ f.label }}</span>
            <b>{{ f.value }}</b>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { TimeRangePicker, TrendChart } from '@zimo/ui'
import { DEFAULT_TIME_RANGE, fmtTime, dotClass, timeRangeLabel } from '@zimo/shared'
import { fetchServices, fetchServiceFilters, fetchDiscoveryTrend } from '../api'

const rows = ref([])
const total = ref(0)
const pages = ref(1)
const detailVisible = ref(false)
const detail = ref({})

/** 统一时间区间：趋势、列表与详情共用同一口径 */
const range = ref(DEFAULT_TIME_RANGE)
const rangeDesc = computed(() => timeRangeLabel(range.value))
const switchRange = () => {
  query.current = 1
  load()
}

/** 调用趋势（请求量 + QPS 同源双序列），随区间与「应用 / 服务」筛选联动 */
const discoTrend = ref({ times: [], values: [], requestValues: [] })
const trendScope = computed(() => {
  const parts = []
  if (query.appId) {
    const app = appOptions.value.find((a) => a.appId === query.appId)
    parts.push(app?.appName || query.appId)
  }
  if (query.serviceName) parts.push(query.serviceName)
  return (parts.length ? parts.join(' · ') : '全部服务') + ' · ' + rangeDesc.value
})
const loadTrend = async () => {
  try {
    discoTrend.value = await fetchDiscoveryTrend({
      range: range.value,
      appId: query.appId || undefined,
      serviceName: query.serviceName || undefined
    })
  } catch (e) {
    discoTrend.value = { times: [], values: [], requestValues: [] }
  }
}

const query = reactive({
  keyword: '',
  appId: '',
  serviceName: '',
  status: '',
  language: '',
  current: 1,
  size: 10
})

// 下拉选项均由 /discovery/filters 按库中真实数据填充
const appOptions = ref([])        // [{ appId, appName }]
const allServices = ref([])       // [{ appId, serviceName }]
const langOptions = ref([])

// 「全部服务」选项随已选应用级联：选了应用只列该应用下的服务
const serviceOptions = computed(() =>
  query.appId ? allServices.value.filter((s) => s.appId === query.appId) : allServices.value
)

const loadFilters = async () => {
  try {
    const d = await fetchServiceFilters()
    appOptions.value = d.apps || []
    allServices.value = d.services || []
    langOptions.value = d.languages || []
  } catch (e) { /* 字典不可用不阻塞列表查询 */ }
}

// 切换应用后，原选中的服务若不属于新应用则清空，避免「应用 A + 服务 B」组合查空
const onAppChange = () => {
  if (query.serviceName && !serviceOptions.value.some((s) => s.serviceName === query.serviceName)) {
    query.serviceName = ''
  }
}

const load = async () => {
  loadTrend()
  try {
    const page = await fetchServices({ ...query, range: range.value })
    rows.value = page.records || []
    total.value = page.total || 0
    pages.value = page.pages || 1
  } catch (e) {
    rows.value = []
    total.value = 0
  }
}

// 首屏加载：先取筛选项字典，再查列表（字典失败不影响列表）
onMounted(() => {
  loadFilters()
  load()
})

const reset = () => {
  query.keyword = ''
  query.appId = ''
  query.serviceName = ''
  query.status = ''
  query.language = ''
  query.current = 1
  load()
}

const onPage = (p) => {
  query.current = p
  load()
}

const openDetail = (row) => {
  detail.value = row
  detailVisible.value = true
}


const detailFields = computed(() => {
  const d = detail.value
  return [
    { label: '应用ID', value: d.appId },
    { label: '应用名', value: d.appName },
    { label: '服务名', value: d.serviceName },
    { label: '端口', value: d.port },
    { label: '语言类型', value: d.language },
    { label: '版本号', value: d.version },
    { label: 'QPS（近 60 秒）', value: d.qps },
    { label: '调用次数（' + rangeDesc.value + '）', value: (d.calls ?? 0).toLocaleString() },
    { label: '已登记接口数', value: d.apiCount ?? 0 },
    { label: '平均 RT（' + rangeDesc.value + '）', value: d.avgRt + ' ms' },
    { label: '健康状态', value: d.status },
    { label: '入站 / 出网流量', value: (d.inFlow || '-') + ' / ' + (d.outFlow || '-') },
    { label: '实例数', value: d.instances },
    { label: '注册时间', value: fmtTime(d.registerTime) }
  ]
})
</script>

<style scoped>
/* .filter-bar 布局由 @zimo/shared 全局样式统一提供（含窄屏两列网格），此处不再重复定义 */

/* 两张趋势卡纵向堆叠：趋势图需要横向空间，全宽比并排更易读峰值 */
.trend-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
  background: var(--gw-text-tertiary);
}

.status-dot.success { background: var(--gw-success); }
.status-dot.warning { background: var(--gw-warning); }
.status-dot.danger { background: var(--gw-danger); }

.drawer-sec { margin-bottom: 18px; }

.drawer-sec-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid var(--gw-primary);
}

.kv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
}

.kv-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
</style>
