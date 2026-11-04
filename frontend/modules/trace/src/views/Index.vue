<template>
  <div class="page">
    <section class="gw-card">
      <div class="gw-card-hd">
        <span>日志追踪</span>
        <span class="gw-muted gw-sm">共 {{ total }} 条调用日志</span>
      </div>

      <!-- 过滤栏 -->
      <div class="gw-card-bd">
        <div class="filter-bar">
          <el-input v-model="query.traceId" placeholder="输入 traceId 查询" clearable style="width: 200px;" />
          <el-input v-model="query.params" placeholder="搜索请求参数 / 报文" clearable style="width: 200px;" />
          <el-select v-model="query.method" placeholder="全部方法" clearable style="width: 130px;">
            <el-option v-for="m in methodOptions" :key="m" :label="m" :value="m" />
          </el-select>
          <el-select v-model="query.api" placeholder="全部接口名" clearable filterable style="width: 240px;">
            <el-option v-for="a in apiOptions" :key="a" :label="a" :value="a" />
          </el-select>
          <el-select v-model="query.appId" placeholder="全部应用" clearable filterable style="width: 200px;" @change="onAppChange">
            <el-option
              v-for="a in appOptions"
              :key="a.appId"
              :label="a.appName ? `${a.appName}（${a.appId}）` : a.appId"
              :value="a.appId"
            />
          </el-select>
          <el-select v-model="query.service" placeholder="全部服务" clearable filterable style="width: 170px;">
            <el-option v-for="s in serviceOptions" :key="s.serviceName" :label="s.serviceName" :value="s.serviceName" />
          </el-select>
          <el-select v-model="query.status" placeholder="全部状态码" clearable style="width: 150px;">
            <el-option v-for="s in statusOptions" :key="s" :label="String(s)" :value="s" />
          </el-select>
          <el-select v-model="query.errType" placeholder="全部错误类型" clearable style="width: 160px;">
            <el-option v-for="e in errTypeOptions" :key="e" :label="e" :value="e" />
          </el-select>
          <el-select v-model="query.protocol" placeholder="全部协议" clearable style="width: 130px;">
            <el-option v-for="p in protocolOptions" :key="p" :label="p" :value="p" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <!-- 列表 -->
      <div class="gw-card-bd no-pad">
        <el-table :data="rows" size="small" style="width: 100%" @row-click="openDetail">
          <el-table-column prop="serviceName" label="服务名" width="140" />
          <el-table-column prop="api" label="接口名" min-width="220" show-overflow-tooltip />
          <el-table-column label="请求参数" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="gw-mono">{{ row.reqParams || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="traceId" label="traceId" width="150" />
          <el-table-column prop="protocol" label="协议" width="90" />
          <el-table-column prop="reqFormat" label="请求格式" width="110" />
          <el-table-column prop="resFormat" label="返回格式" width="110" />
          <el-table-column prop="reqIp" label="请求IP" width="130" />
          <el-table-column label="上游地址" width="150">
            <template #default="{ row }">{{ row.resIp || '-' }}:{{ row.resPort || '-' }}</template>
          </el-table-column>
          <el-table-column prop="statusCode" label="状态码" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.statusCode)">{{ row.statusCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="请求时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.reqTime) }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.cost }} ms</template>
          </el-table-column>
          <el-table-column label="操作" :width="narrow ? 88 : 130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">
                {{ narrow ? '详情' : '请求参数 · 返回参数' }}
              </el-button>
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

    <!-- 日志详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="'请求详情 · ' + (detail.traceId || '')" size="min(620px, 94vw)">
      <div v-if="detail.traceId" v-loading="detailLoading">
        <div class="drawer-sec">
          <div class="drawer-sec-title">基本信息</div>
        <div class="kv-grid">
          <div v-for="f in basicFields" :key="f.label" class="kv-item">
            <span class="gw-sm gw-muted">{{ f.label }}</span>
            <b>{{ f.value }}</b>
          </div>
        </div>
      </div>

        <!-- 调用链路瀑布图：span 表有数据才显示（旧日志无 span） -->
        <div v-if="spans.length" class="drawer-sec">
          <div class="drawer-sec-title">
            调用链路
            <span class="gw-sm gw-muted" style="margin-left: 8px">
              {{ spans.length }} 个 span · 总耗时 {{ traceDuration }} ms
            </span>
          </div>
          <div class="waterfall">
            <div v-for="n in spanTree" :key="n.key" class="wf-row">
              <span class="wf-name" :style="{ paddingLeft: n.depth * 14 + 'px' }" :title="n.api">
                <span class="wf-dot" :class="n.err ? 'err' : ''" />{{ n.label }}
              </span>
              <span class="wf-cost gw-mono">{{ n.cost }} ms</span>
              <span class="wf-track">
                <span
                  class="wf-bar"
                  :class="n.err ? 'err' : ''"
                  :style="{ left: n.left + '%', width: Math.max(0.8, n.width) + '%' }"
                />
              </span>
            </div>
          </div>
        </div>

        <div class="drawer-sec">
          <div class="drawer-sec-title">请求 Header</div>
          <div class="hdr-box">
            <div v-for="(h, i) in reqHeaders" :key="i" :class="h.value === null ? 'hdr-start' : 'hdr-line'">
              <span v-if="h.value === null" class="gw-mono">{{ h.key }}</span>
              <template v-else>
                <span class="hdr-k gw-mono">{{ h.key }}</span>
                <span class="hdr-v gw-mono">{{ h.value }}</span>
              </template>
            </div>
          </div>
        </div>

        <div class="drawer-sec">
          <div class="drawer-sec-title">请求参数（Query）</div>
          <div v-if="queryParams.length" class="hdr-box">
            <div v-for="(p, i) in queryParams" :key="i" class="hdr-line">
              <span class="hdr-k gw-mono">{{ p.key }}</span>
              <span class="hdr-v gw-mono">{{ p.value }}</span>
            </div>
          </div>
          <div v-else class="gw-sm gw-muted">无 Query 参数</div>
        </div>

        <div v-if="formParams.length" class="drawer-sec">
          <div class="drawer-sec-title">表单参数（Form）</div>
          <div class="hdr-box">
            <div v-for="(p, i) in formParams" :key="i" class="hdr-line">
              <span class="hdr-k gw-mono">{{ p.key }}</span>
              <span class="hdr-v gw-mono">{{ p.value }}</span>
            </div>
          </div>
        </div>

        <div class="drawer-sec">
          <div class="drawer-sec-title">请求报文体（Body）</div>
          <pre class="code-block">{{ detail.reqBody || '无请求体（GET 或二进制内容不采集）' }}</pre>
        </div>

        <div class="drawer-sec">
          <div class="drawer-sec-title">响应 Header</div>
          <div class="hdr-box">
            <div v-for="(h, i) in resHeaders" :key="i" :class="h.value === null ? 'hdr-start' : 'hdr-line'">
              <span v-if="h.value === null" class="gw-mono">{{ h.key }}</span>
              <template v-else>
                <span class="hdr-k gw-mono">{{ h.key }}</span>
                <span class="hdr-v gw-mono">{{ h.value }}</span>
              </template>
            </div>
          </div>
        </div>

        <div class="drawer-sec">
          <div class="drawer-sec-title">响应报文体 / 返回参数</div>
          <pre class="code-block">{{ detail.resBody || '无响应体（二进制内容不采集）' }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { fetchTraceLogs, fetchTraceFilters, fetchTraceLogDetail, fetchTraceSpans } from '../api'
import { fmtTime, fmtBytes, statusTagType, parseHeaders } from '@zimo/shared'

const route = useRoute()

const rows = ref([])
const total = ref(0)
const pages = ref(1)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})

const query = reactive({
  traceId: '',
  params: '',
  method: '',
  appId: '',
  service: '',
  api: '',
  status: null,
  errType: '',
  protocol: '',
  reqFormat: '',
  resFormat: '',
  startTime: '',
  costMin: null,
  current: 1,
  size: 10
})

// 筛选项默认值仅作兜底；真实值由 /trace/logs/filters 按库中数据动态填充
const appOptions = ref([])      // [{ appId, appName }]
const allServices = ref([])     // [{ appId, serviceName }]

// 「全部服务」随已选应用级联：选了应用只列该应用下的服务
const serviceOptions = computed(() =>
  query.appId ? allServices.value.filter((s) => s.appId === query.appId) : allServices.value
)

// 切换应用后，原选中的服务若不属于新应用则清空，避免「应用 A + 服务 B」组合查空
const onAppChange = () => {
  if (query.service && !serviceOptions.value.some((s) => s.serviceName === query.service)) {
    query.service = ''
  }
}
const apiOptions = ref([])
const methodOptions = ref(['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'])
const protocolOptions = ref(['HTTP/1.1', 'HTTP/2', 'gRPC', 'WebSocket'])
const reqFormatOptions = ref(['JSON', 'Protobuf', 'form', '无'])
const resFormatOptions = ref(['JSON', 'Protobuf', '无'])
const statusOptions = [200, 400, 403, 429, 502, 503, 504]
const errTypeOptions = ['无', '鉴权失败', '路由失败', '限流拒绝', '参数校验失败', '上游响应超时', '上游响应异常']

/** 拉筛选项字典：失败时保留兜底值，不影响列表加载 */
const loadFilters = async () => {
  try {
    const d = await fetchTraceFilters()
    if (d.protocols?.length) protocolOptions.value = d.protocols
    if (d.methods?.length) methodOptions.value = d.methods
    if (d.apps?.length) appOptions.value = d.apps
    if (d.services?.length) allServices.value = d.services
    if (d.apis?.length) apiOptions.value = d.apis
    if (d.reqFormats?.length) reqFormatOptions.value = d.reqFormats
    if (d.resFormats?.length) resFormatOptions.value = d.resFormats
  } catch (e) { /* 字典不可用不阻塞查询 */ }
}

const load = async () => {
  const params = {}
  Object.keys(query).forEach((k) => {
    const v = query[k]
    if (v !== '' && v !== null && v !== undefined) params[k] = v
  })
  try {
    const page = await fetchTraceLogs(params)
    rows.value = page.records || []
    total.value = page.total || 0
    pages.value = page.pages || 1
  } catch (e) {
    rows.value = []
    total.value = 0
  }
}

const reset = () => {
  query.traceId = ''
  query.params = ''
  query.method = ''
  query.appId = ''
  query.service = ''
  query.api = ''
  query.status = null
  query.errType = ''
  query.protocol = ''
  query.reqFormat = ''
  query.resFormat = ''
  query.startTime = ''
  query.costMin = null
  query.current = 1
  load()
}

const onPage = (p) => {
  query.current = p
  load()
}

/** 列表接口不返回 Header / 报文体大字段，必须按 id 拉详情，否则抽屉恒为空 */
const openDetail = async (row) => {
  detail.value = row
  detailVisible.value = true
  detailLoading.value = true
  spans.value = []
  try {
    const d = await fetchTraceLogDetail(row.id)
    if (d) detail.value = d
    // 有 traceId 才拉链路；旧日志（功能上线前）span 表无数据，不显示瀑布图
    if (detail.value.traceId) {
      spans.value = await fetchTraceSpans(detail.value.traceId).catch(() => [])
    }
  } catch (e) {
    // 详情不可用至少保留列表字段，不让抽屉白屏
    detail.value = row
  } finally {
    detailLoading.value = false
  }
}

// ---------------------------------------------------------------- 调用链路瀑布图

const spans = ref([])

/** 链路总耗时：根 span 起点 → 最晚 span 终点 */
const traceDuration = computed(() => {
  const list = spans.value
  if (!list.length) return 0
  const starts = list.map((s) => new Date(s.startTime.replace(' ', 'T')).getTime())
  const min = Math.min.apply(null, starts)
  const maxEnd = Math.max.apply(null, list.map((s, i) => starts[i] + (s.cost || 0)))
  return Math.max(0, maxEnd - min)
})

/**
 * span 列表 → 树形瀑布行：按 parent 组树、层级缩进，
 * 条形 left/width 按全链路时长归一化；无 parent 的多个根按时间先后并列。
 */
const spanTree = computed(() => {
  const list = spans.value
  if (!list.length) return []
  const times = list.map((s) => new Date(s.startTime.replace(' ', 'T')).getTime())
  const rootStart = Math.min.apply(null, times)
  const duration = Math.max(1, traceDuration.value)

  const byParent = new Map()
  list.forEach((s, i) => {
    const key = s.parentSpanId || '__root__'
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push({ s, t: times[i] })
  })
  // 同层按开始时间排序，瀑布自上而下与时间一致
  byParent.forEach((arr) => arr.sort((a, b) => a.t - b.t))

  const rows = []
  const walk = (parentId, depth) => {
    const children = byParent.get(parentId) || []
    for (const { s, t } of children) {
      rows.push({
        key: s.spanId,
        label: s.serviceName + (s.peerService ? ' → ' + s.peerService : ''),
        api: s.api || '',
        cost: s.cost || 0,
        err: (s.statusCode || 0) >= 400 || (s.errType && s.errType !== '无'),
        depth,
        left: ((t - rootStart) / duration) * 100,
        width: ((s.cost || 0) / duration) * 100
      })
      walk(s.spanId, depth + 1)
    }
  }
  walk('__root__', 0)
  // 防御：parent 指向不存在的 span（脏数据）时按根补挂，避免行丢失
  if (rows.length < list.length) {
    const shown = new Set(rows.map((r) => r.key))
    list.forEach((s, i) => {
      if (!shown.has(s.spanId)) {
        rows.push({
          key: s.spanId, label: s.serviceName + (s.peerService ? ' → ' + s.peerService : ''),
          api: s.api || '', cost: s.cost || 0,
          err: (s.statusCode || 0) >= 400, depth: 0,
          left: ((times[i] - rootStart) / duration) * 100,
          width: ((s.cost || 0) / duration) * 100
        })
      }
    })
  }
  return rows
})

/** query string / form-urlencoded 原文解析成键值列表 */
const parseParams = (text) => {
  if (!text) return []
  try {
    return [...new URLSearchParams(text.replace(/^\?/, ''))].map(([key, value]) => ({ key, value }))
  } catch (e) {
    return []
  }
}

const reqHeaders = computed(() => parseHeaders(detail.value.reqHeader))
const resHeaders = computed(() => parseHeaders(detail.value.resHeader))
const queryParams = computed(() => parseParams(detail.value.reqParams))
const formParams = computed(() =>
  detail.value.reqFormat === '表单' ? parseParams(detail.value.reqBody) : [])
const basicFields = computed(() => {
  const d = detail.value
  return [
    { label: '应用ID', value: d.appId },
    { label: '应用名', value: d.appName },
    { label: '服务名', value: d.serviceName },
    { label: '接口名', value: d.api },
    { label: 'traceId', value: d.traceId },
    { label: '协议', value: d.protocol },
    { label: '请求格式', value: d.reqFormat },
    { label: '返回格式', value: d.resFormat },
    { label: '请求IP', value: d.reqIp },
    { label: '请求端口', value: d.reqPort },
    { label: '回复IP', value: d.resIp },
    { label: '回复端口', value: d.resPort },
    { label: '状态码', value: d.statusCode },
    { label: '请求时间', value: fmtTime(d.reqTime) },
    { label: '返回时间', value: fmtTime(d.resTime) },
    { label: '耗时', value: d.cost + ' ms' },
    { label: '请求字节数', value: fmtBytes(d.reqBytes) },
    { label: '返回字节数', value: fmtBytes(d.resBytes) },
    { label: '网关错误类型', value: d.errType === '无' ? '—' : d.errType }
  ]
})

// 窄屏（≤768px）把固定操作列收窄为「详情」，避免长文案在固定列里被裁
const narrow = ref(false)
const mq = window.matchMedia('(max-width: 768px)')
narrow.value = mq.matches
const onMqChange = (e) => { narrow.value = e.matches }
mq.addEventListener('change', onMqChange)
onUnmounted(() => mq.removeEventListener('change', onMqChange))

onMounted(async () => {
  await loadFilters()
  // 支持从服务拓扑的慢链路列表跳转过来：?traceId=xxx 自动检索并打开详情瀑布图
  const fromQuery = route.query.traceId
  if (fromQuery) {
    query.traceId = String(fromQuery)
  }
  await load()
  if (fromQuery && rows.value.length) {
    openDetail(rows.value[0])
  }
})
</script>
