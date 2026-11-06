<template>
  <div class="page">
    <!-- 拓扑图 -->
    <div class="gw-card">
      <div class="gw-card-hd">
        <span>服务调用拓扑</span>
        <div class="topo-tools">
          <el-select v-model="filterApp" placeholder="全部应用" clearable filterable
                     size="small" style="width: 170px;" @change="onAppChange">
            <el-option v-for="a in appOptions" :key="a.appId"
                       :label="a.appName ? `${a.appName}（${a.appId}）` : a.appId" :value="a.appId" />
          </el-select>
          <el-select v-model="filterService" placeholder="全部服务" clearable filterable
                     size="small" style="width: 190px;" @change="load">
            <el-option v-for="s in serviceOptions" :key="s" :label="s" :value="s" />
          </el-select>
          <TimeRangePicker v-model="range" size="sm" @change="load" />
          <el-button size="small" :icon="Refresh" circle @click="load" />
        </div>
      </div>
      <div class="gw-card-bd">
        <div v-if="!edges.length && !loading" class="gw-sm gw-muted topo-empty">
          该时间窗内没有服务间调用记录（CLIENT span 的 peer_service 聚合），等业务跑一会儿再来看。
        </div>
        <div v-else class="topo-wrap" v-loading="loading" @mousemove="onMove">
          <svg class="topo-svg" :viewBox="`0 0 ${layout.width} ${layout.height}`" :style="{ height: svgHeight }">
            <!-- 边：贝塞尔曲线，线宽=调用量，颜色=错误率 -->
            <g v-for="e in layout.edges" :key="e.key">
              <!-- 透明宽描边做命中区：真实线宽只有 2~6px，直接用线做 hover 太难点中 -->
              <path class="topo-edge-hit" :d="e.path"
                @mouseenter="hoverEdge = e; hoverNode = null" @mouseleave="hoverEdge = null" />
              <path class="topo-edge" :d="e.path" :class="e.level" :stroke-width="e.strokeWidth"
                :marker-end="`url(#arrow-${e.level})`" />
              <text class="topo-edge-label" :x="e.labelX" :y="e.labelY" text-anchor="middle">
                {{ e.calls }} 次{{ e.errors > 0 ? ' · ' + e.errors + ' 败' : '' }}
              </text>
            </g>
            <!-- 节点 -->
            <g v-for="n in layout.nodes" :key="n.name" class="topo-node"
              :transform="`translate(${n.x}, ${n.y})`" @mouseenter="hoverNode = n" @mouseleave="hoverNode = null">
              <rect :width="NODE_W" :height="NODE_H" rx="8" />
              <text class="topo-node-name" :x="NODE_W / 2" y="19">{{ n.short }}</text>
              <text class="topo-node-sub" :x="NODE_W / 2" y="34">
                入 {{ n.inCalls }} · 出 {{ n.outCalls }}
              </text>
            </g>
            <defs>
              <marker id="arrow-ok" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                <path d="M0,0 L10,5 L0,10 z" fill="var(--gw-success)" />
              </marker>
              <marker id="arrow-warn" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                <path d="M0,0 L10,5 L0,10 z" fill="var(--gw-warning)" />
              </marker>
              <marker id="arrow-err" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                <path d="M0,0 L10,5 L0,10 z" fill="var(--gw-danger)" />
              </marker>
            </defs>
          </svg>
          <!-- 悬浮提示：节点 / 边共用一个浮层 -->
          <div v-if="tip" class="topo-tip" :style="{ left: tip.x + 'px', top: tip.y + 'px' }">
            <div class="topo-tip-title">{{ tip.title }}</div>
            <div v-for="(row, i) in tip.rows" :key="i" class="topo-tip-row">
              <span>{{ row[0] }}</span><b>{{ row[1] }}</b>
            </div>
          </div>
          <!-- 图例 -->
          <div class="topo-legend gw-sm gw-muted">
            <span><i class="lg-line ok" />正常</span>
            <span><i class="lg-line warn" />错误率 &lt; 5%</span>
            <span><i class="lg-line err" />错误率 ≥ 5%</span>
            <span>线宽 = 调用量，箭头指向被调方</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 慢链路 TopN -->
    <div class="gw-card">
      <div class="gw-card-hd">
        <span>慢链路 Top {{ slowLimit }}</span>
        <span class="gw-sm gw-muted">{{ rangeDesc }} · 按链路总耗时（首 span 起点→最晚 span 终点）排名，点击行看瀑布图</span>
      </div>
      <div class="gw-card-bd">
        <el-table :data="slowRows" size="small" v-loading="slowLoading" @row-click="openTrace"
          :row-style="{ cursor: 'pointer' }">
          <el-table-column type="index" label="#" width="46" />
          <el-table-column prop="startTime" label="开始时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column prop="rootService" label="入口服务" min-width="150" show-overflow-tooltip />
          <el-table-column prop="rootApi" label="入口接口" min-width="190" show-overflow-tooltip />
          <el-table-column label="总耗时" width="100">
            <template #default="{ row }">
              <b :class="row.totalCost >= 1000 ? 'slow-bad' : ''">{{ fmtCost(row.totalCost) }}</b>
            </template>
          </el-table-column>
          <el-table-column prop="spanCount" label="span 数" width="76" />
          <el-table-column label="错误 span" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.errorCount > 0" type="danger" size="small">{{ row.errorCount }}</el-tag>
              <span v-else class="gw-muted">0</span>
            </template>
          </el-table-column>
          <el-table-column prop="traceId" label="traceId" min-width="150" show-overflow-tooltip />
        </el-table>
        <div v-if="!slowRows.length && !slowLoading" class="gw-sm gw-muted" style="padding: 12px 0">
          该时间窗内没有链路数据。
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { TimeRangePicker } from '@zimo/ui'
import { DEFAULT_TIME_RANGE, timeRangeLabel } from '@zimo/shared'
import { fetchTopology, fetchSlowTraces, fetchTraceFilters } from '../api'

const router = useRouter()

const NODE_W = 152
const NODE_H = 46
const COL_GAP = 96   // 列间距减去节点宽：留给边和标注
const ROW_H = 78

const range = ref(DEFAULT_TIME_RANGE)
const rangeDesc = computed(() => timeRangeLabel(range.value))
const slowLimit = 10
const loading = ref(false)
const slowLoading = ref(false)
const edges = ref([])
const rawNodes = ref([])
const slowRows = ref([])
const hoverNode = ref(null)
const hoverEdge = ref(null)
const mouse = ref({ x: 0, y: 0 })

/* --------------------------------------------------- 应用 / 服务过滤（默认全局） */

const filterApp = ref('')
const filterService = ref('')
const appOptions = ref([])      // [{ appId, appName }]
const allServices = ref([])     // [{ appId, serviceId, serviceName }]

// 服务下拉随应用级联：选了应用只列该应用下实际调用过的服务
const serviceOptions = computed(() =>
  filterApp.value
    ? allServices.value.filter((s) => s.appId === filterApp.value).map((s) => s.serviceId)
    : [...new Set(allServices.value.map((s) => s.serviceId))]
)

const loadFilters = async () => {
  try {
    const d = await fetchTraceFilters()
    appOptions.value = d.apps || []
    allServices.value = d.services || []
  } catch (e) { /* 字典不可用不阻塞拓扑查询 */ }
}

// 切应用后原选中服务若不属于新应用则清空，避免「应用 A + 服务 B」组合查空
const onAppChange = () => {
  if (filterService.value && !serviceOptions.value.includes(filterService.value)) {
    filterService.value = ''
  }
  load()
}

const load = async () => {
  loading.value = true
  slowLoading.value = true
  try {
    const [topo, slow] = await Promise.all([
      fetchTopology({ range: range.value, appId: filterApp.value || undefined, service: filterService.value || undefined }),
      fetchSlowTraces({ range: range.value, limit: slowLimit })
    ])
    edges.value = topo.edges || []
    rawNodes.value = topo.nodes || []
    slowRows.value = slow || []
  } finally {
    loading.value = false
    slowLoading.value = false
  }
}

// ---------------------------------------------------------------- 拓扑布局

/**
 * 分层布局：入口服务（无入边）在左，每沿一条调用边加深一层；
 * 迭代 |V| 轮取最长路径层级（可容忍环，层数封顶节点数）。
 */
const layout = computed(() => {
  const nodes = rawNodes.value
  const es = edges.value
  if (!nodes.length) return { nodes: [], edges: [], width: 600, height: 200 }

  const layer = Object.fromEntries(nodes.map((n) => [n.name, 0]))
  for (let r = 0; r < nodes.length; r++) {
    let changed = false
    for (const e of es) {
      const next = (layer[e.src] ?? 0) + 1
      if ((layer[e.dst] ?? 0) < next && next < nodes.length) {
        layer[e.dst] = next
        changed = true
      }
    }
    if (!changed) break
  }

  const pos = {}
  const byLayer = {}
  for (const n of nodes) {
    const l = layer[n.name] ?? 0
    ;(byLayer[l] = byLayer[l] || []).push(n)
  }
  Object.values(byLayer).forEach((list) => list.sort((a, b) => a.name.localeCompare(b.name)))
  const maxCol = Math.max(...Object.keys(byLayer).map(Number))
  const maxRows = Math.max(...Object.values(byLayer).map((a) => a.length))
  Object.entries(byLayer).forEach(([l, list]) => {
    // 列内垂直居中：节点少的列（如只有入口服务的第 0 列）不会贴顶
    const top = 16 + ((maxRows - list.length) * ROW_H) / 2
    list.forEach((n, i) => {
      pos[n.name] = { x: 20 + l * (NODE_W + COL_GAP), y: top + i * ROW_H }
    })
  })

  // 服务ID普遍带统一前缀（jv-api-gateway-*），直接截断会让节点名全都一样，
  // 这里剥离公共前缀只显示有区分度的部分，完整名留到悬浮提示
  const common = commonPrefix(nodes.map((n) => n.name))
  const maxCalls = Math.max(...es.map((x) => x.calls), 1)
  // 同源的多条边分散起点，避免全部从节点右侧同一点发散后拧成一束
  const bySrc = {}
  es.forEach((e) => (bySrc[e.src] = bySrc[e.src] || []).push(e))
  Object.values(bySrc).forEach((list) => list.sort((a, b) => a.dst.localeCompare(b.dst)))

  const drawn = es.map((e) => {
    const p1 = pos[e.src]
    const p2 = pos[e.dst]
    const rate = e.calls > 0 ? e.errors / e.calls : 0
    const level = rate >= 0.05 ? 'err' : rate > 0.01 ? 'warn' : 'ok'
    // 线宽 2~6px：对数压缩，避免调用量差几个数量级时细线看不见
    const strokeWidth = 2 + 4 * (Math.log10(e.calls + 1) / Math.log10(maxCalls + 1))
    const siblings = bySrc[e.src]
    const idx = siblings.indexOf(e)
    const spread = Math.min(14, (NODE_H - 14) / Math.max(1, siblings.length - 1))
    const offset = (idx - (siblings.length - 1) / 2) * spread

    let path, labelX, labelY
    if (p2.x >= p1.x + NODE_W) {
      // 正向边：三阶贝塞尔水平出、水平入
      const x1 = p1.x + NODE_W
      const y1 = p1.y + NODE_H / 2 + offset
      const x2 = p2.x
      const y2 = p2.y + NODE_H / 2
      const mx = (x1 + x2) / 2
      path = `M ${x1} ${y1} C ${mx} ${y1}, ${mx} ${y2}, ${x2} ${y2}`
      // 同源多条边的标签沿曲线按序错开 t（而不是都放中点），避免叠字
      const { x: lx, y: ly } = bezierAt(0.24 + idx * 0.16, x1, y1, mx, y1, mx, y2, x2, y2)
      labelX = lx
      labelY = ly - 5
    } else {
      // 回环边（环 / 同层互调）：从底部绕行，避免与正向边重叠
      const x1 = p1.x + NODE_W / 2 + offset
      const y1 = p1.y + NODE_H
      const x2 = p2.x + NODE_W / 2
      const y2 = p2.y + NODE_H
      const drop = 44
      path = `M ${x1} ${y1} C ${x1} ${y1 + drop}, ${x2} ${y2 + drop}, ${x2} ${y2}`
      labelX = (x1 + x2) / 2
      labelY = Math.max(y1, y2) + drop - 8
    }
    return { ...e, key: e.src + '→' + e.dst, level, strokeWidth, path, labelX, labelY, rate }
  })

  return {
    nodes: nodes.map((n) => ({
      ...n,
      x: pos[n.name].x,
      y: pos[n.name].y,
      short: shortName(n.name, common)
    })),
    edges: drawn,
    width: 20 + (maxCol + 1) * (NODE_W + COL_GAP),
    height: 16 + maxRows * ROW_H + 40
  }
})

/**
 * 服务ID的公共前缀（按 '-' 边界收口）。
 * 取「首段相同」的最大分组求前缀 —— 拓扑里混有 IP:port 形态的节点时，
 * 直接对全部名称求前缀会因首个字符不同而退化为空串。
 */
function commonPrefix(names) {
  if (names.length < 2) return ''
  const groups = {}
  names.forEach((n) => {
    // 按前两段分组（jv-api-gateway / jv-regtech）：只按首段 'jv' 分组会把
    // jv-regtech-* 也算进来，公共前缀被拉短成 'jv-'
    const seg = n.split('-').slice(0, 2).join('-')
    ;(groups[seg] = groups[seg] || []).push(n)
  })
  const biggest = Object.values(groups).sort((a, b) => b.length - a.length)[0]
  if (biggest.length < 2) return ''
  let p = biggest[0]
  for (const n of biggest) {
    let i = 0
    while (i < p.length && i < n.length && p[i] === n[i]) i++
    p = p.slice(0, i)
  }
  const cut = p.lastIndexOf('-')
  return cut > 0 ? p.slice(0, cut + 1) : ''
}

/** 三阶贝塞尔取点：用于把边标签沿曲线错开摆放 */
function bezierAt(t, x0, y0, x1, y1, x2, y2, x3, y3) {
  const mt = 1 - t
  const a = mt * mt * mt
  const b = 3 * mt * mt * t
  const c = 3 * mt * t * t
  const d = t * t * t
  return { x: a * x0 + b * x1 + c * x2 + d * x3, y: a * y0 + b * y1 + c * y2 + d * y3 }
}

function shortName(name, prefix) {
  const s = prefix && name.startsWith(prefix) ? name.slice(prefix.length) : name
  return s.length > 16 ? s.slice(0, 15) + '…' : s
}

const svgHeight = computed(() => Math.min(560, Math.max(220, layout.value.height)) + 'px')

// ---------------------------------------------------------------- 悬浮提示

const wrapEl = ref(null)
const tip = computed(() => {  const base = { x: mouse.value.x + 14, y: mouse.value.y + 14 }
  if (hoverNode.value) {
    const n = hoverNode.value
    const out = edges.value.filter((e) => e.src === n.name)
    const errs = out.reduce((s, e) => s + e.errors, 0)
    return {
      ...base,
      title: n.name,
      rows: [
        ['作为被调方', n.inCalls + ' 次'],
        ['作为调用方', n.outCalls + ' 次'],
        ['下游调用失败', errs + ' 次'],
        ['出边数量', out.length + ' 条']
      ]
    }
  }
  if (hoverEdge.value) {
    const e = hoverEdge.value
    return {
      ...base,
      title: e.src + ' → ' + e.dst,
      rows: [
        ['调用量', e.calls + ' 次'],
        ['失败', e.errors + ' 次（' + (e.rate * 100).toFixed(1) + '%）'],
        ['平均耗时', e.avgCost + ' ms'],
        ['最大耗时', e.maxCost + ' ms']
      ]
    }
  }
  return null
})

const onMove = (ev) => {
  const wrap = ev.currentTarget
  if (!wrap) return
  const r = wrap.getBoundingClientRect()
  mouse.value = { x: ev.clientX - r.left, y: ev.clientY - r.top }
}

// ---------------------------------------------------------------- 工具

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')
const fmtCost = (ms) => (ms >= 1000 ? (ms / 1000).toFixed(2) + ' s' : Math.round(ms) + ' ms')

/** 慢链路行点击 → 日志追踪页按 traceId 检索并打开详情瀑布图 */
const openTrace = (row) => {
  router.push({ path: '/trace', query: { traceId: row.traceId } })
}

onMounted(() => {
  loadFilters()
  load()
})
</script>

<style scoped>
.topo-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topo-wrap {
  position: relative;
  overflow: auto;
}

.topo-svg {
  width: 100%;
  display: block;
  min-width: 520px;
}

.topo-empty {
  padding: 24px 0;
}

/* 边 */
.topo-edge {
  fill: none;
  opacity: .8;
}
.topo-edge.ok   { stroke: var(--gw-success); }
.topo-edge.warn { stroke: var(--gw-warning); }
.topo-edge.err  { stroke: var(--gw-danger); }

/* 透明命中区：hove 判定用，不参与视觉 */
.topo-edge-hit {
  fill: none;
  stroke: transparent;
  stroke-width: 16px;
  cursor: pointer;
}

.topo-edge-label {
  font-size: 10px;
  fill: var(--gw-text-secondary);
  /* 白描边压在曲线之上：多条边交汇处标签不会被线穿过 */
  paint-order: stroke;
  stroke: var(--gw-bg-card, #fff);
  stroke-width: 3px;
  stroke-linejoin: round;
}

/* 节点 */
.topo-node { cursor: default; }
.topo-node rect {
  fill: var(--gw-bg-card, #fff);
  stroke: var(--gw-border);
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, .06));
}
.topo-node:hover rect { stroke: var(--gw-info); }

.topo-node-name {
  font-size: 12px;
  font-weight: 600;
  fill: var(--gw-text-primary);
  text-anchor: middle;
}

.topo-node-sub {
  font-size: 10px;
  fill: var(--gw-text-tertiary);
  text-anchor: middle;
}

/* 悬浮提示 */
.topo-tip {
  position: absolute;
  z-index: 10;
  pointer-events: none;
  background: rgba(17, 24, 39, .94);
  color: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  min-width: 170px;
  max-width: 320px;
}

.topo-tip-title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 5px;
  word-break: break-all;
}

.topo-tip-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  font-size: 11.5px;
  line-height: 1.7;
}

.topo-tip-row span { opacity: .72; }

/* 图例 */
.topo-legend {
  display: flex;
  gap: 14px;
  align-items: center;
  padding-top: 8px;
  flex-wrap: wrap;
}

.lg-line {
  display: inline-block;
  width: 22px;
  height: 0;
  border-top: 3px solid;
  margin-right: 4px;
  vertical-align: middle;
}
.lg-line.ok   { border-color: var(--gw-success); }
.lg-line.warn { border-color: var(--gw-warning); }
.lg-line.err  { border-color: var(--gw-danger); }

.slow-bad { color: var(--gw-danger); }
</style>
