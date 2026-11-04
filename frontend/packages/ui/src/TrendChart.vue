<template>
  <div ref="wrap" class="tc" :style="{ height: height + 'px' }">
    <svg
      class="tc-svg"
      :width="width"
      :height="height"
      @mousemove="onMove"
      @mouseleave="onLeave"
    >
      <defs>
        <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--gw-info)" stop-opacity="0.18" />
          <stop offset="100%" stop-color="var(--gw-info)" stop-opacity="0.01" />
        </linearGradient>
      </defs>

      <!-- Y 轴网格与刻度值 -->
      <g class="tc-grid">
        <template v-for="t in yTicks" :key="t.v">
          <line :x1="pad.left" :y1="t.y" :x2="width - pad.right" :y2="t.y" />
          <text :x="pad.left - 6" :y="t.y + 3.5" text-anchor="end">{{ t.label }}</text>
        </template>
      </g>

      <!-- 面积 + 折线（viewBox 用真实像素，线宽不被拉伸变形） -->
      <path v-if="hasData" :d="areaPath" :fill="`url(#${gradId})`" stroke="none" />
      <path v-if="hasData" :d="linePath" class="tc-line" />

      <!-- 悬浮：竖向准线 + 数据点 -->
      <g v-if="hover" class="tc-hover">
        <line :x1="hover.x" :y1="pad.top" :x2="hover.x" :y2="height - pad.bottom" />
        <circle :cx="hover.x" :cy="hover.y" r="3.6" />
      </g>

      <!-- X 轴时间刻度 -->
      <g class="tc-axis">
        <text
          v-for="x in xLabels"
          :key="x.i"
          :x="x.x"
          :y="height - 4"
          :text-anchor="x.anchor"
        >{{ x.label }}</text>
      </g>
    </svg>

    <!-- 空数据提示：全 0 时折线贴底，需要明确告知不是「图挂了」 -->
    <div v-if="!hasData" class="tc-empty">该区间暂无调用数据</div>

    <!-- 悬浮提示：跟随鼠标，靠右时自动翻到左侧 -->
    <div v-if="hover" class="tc-tip" :style="{ left: hover.tipX + 'px', top: hover.tipY + 'px' }">
      <div class="tc-tip-time">{{ hover.time }}</div>
      <div class="tc-tip-val">
        <span class="dot" />{{ seriesName }}
        <b>{{ formatNum(hover.value) }}{{ unit }}</b>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  /** X 轴标签（时间或分桶名），与 values 等长 */
  times: { type: Array, default: () => [] },
  /** 数值序列 */
  values: { type: Array, default: () => [] },
  /** 数值后缀，如 ' 次' */
  unit: { type: String, default: '' },
  /** tooltip 里的系列名 */
  seriesName: { type: String, default: '调用量' },
  height: { type: Number, default: 160 }
})

const wrap = ref(null)
const width = ref(600)
const pad = { top: 10, right: 10, bottom: 20, left: 44 }
const gradId = 'tc-grad-' + Math.random().toString(36).slice(2, 8)

let ro = null

onMounted(() => {
  ro = new ResizeObserver((entries) => {
    const w = Math.round(entries[0].contentRect.width)
    if (w > 0) width.value = w
  })
  if (wrap.value) ro.observe(wrap.value)
  width.value = Math.round(wrap.value?.clientWidth || 600)
})

onBeforeUnmount(() => ro?.disconnect())

const count = computed(() => Math.min(props.values.length, props.times.length))
const hasData = computed(() => props.values.some((v) => Number(v) > 0))

/**
 * Y 轴上界：先按「4 段」估算每段步长，再把步长归到 1/2/5×10^n，
 * 保证 5 个刻度值都是整数或 0.5 的倍数（否则会出现 1.25 / 3.75 这种刻度）。
 */
const yMax = computed(() => {
  const max = Math.max(0, ...props.values.map(Number))
  if (max <= 0) return 4
  const raw = max / 4
  const mag = Math.pow(10, Math.floor(Math.log10(raw)))
  const n = raw / mag
  const step = (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * mag
  return step * 4
})

const innerW = computed(() => Math.max(1, width.value - pad.left - pad.right))
const innerH = computed(() => Math.max(1, props.height - pad.top - pad.bottom))
const stepX = computed(() => (count.value > 1 ? innerW.value / (count.value - 1) : 0))

const pointAt = (i) => {
  const v = Number(props.values[i] || 0)
  const x = pad.left + (count.value > 1 ? i * stepX.value : innerW.value / 2)
  const y = pad.top + innerH.value - (v / yMax.value) * innerH.value
  return { x, y, v }
}

const points = computed(() => Array.from({ length: count.value }, (_, i) => pointAt(i)))

const linePath = computed(() => {
  if (!count.value) return ''
  return points.value.map((p, i) => (i === 0 ? 'M' : 'L') + p.x.toFixed(1) + ' ' + p.y.toFixed(1)).join(' ')
})

const areaPath = computed(() => {
  if (!count.value) return ''
  const baseY = (pad.top + innerH.value).toFixed(1)
  const first = points.value[0]
  const last = points.value[points.value.length - 1]
  return `${linePath.value} L${last.x.toFixed(1)} ${baseY} L${first.x.toFixed(1)} ${baseY} Z`
})

/** Y 轴 5 档刻度：0 / 1/4 / 1/2 / 3/4 / 上界 */
const yTicks = computed(() =>
  [0, 0.25, 0.5, 0.75, 1].map((r) => {
    const v = yMax.value * r
    return {
      v: r,
      y: pad.top + innerH.value - r * innerH.value,
      label: formatNum(Math.round(v))
    }
  })
)

/** X 轴最多 7 个标签，首尾必保留，避免时间轴稀疏到看不出范围 */
const xLabels = computed(() => {
  const n = count.value
  if (!n) return []
  const maxLabels = Math.min(7, n)
  const gap = Math.max(1, Math.ceil((n - 1) / Math.max(1, maxLabels - 1)))
  const out = []
  for (let i = 0; i < n; i += gap) {
    out.push({ i, x: pointAt(i).x, anchor: i === 0 ? 'start' : 'middle', label: props.times[i] })
  }
  const lastIdx = n - 1
  if (out.length && out[out.length - 1].i !== lastIdx) {
    out.push({ i: lastIdx, x: pointAt(lastIdx).x, anchor: 'end', label: props.times[lastIdx] })
  }
  return out
})

const hoverIdx = ref(-1)
const hover = computed(() => {
  if (hoverIdx.value < 0 || !count.value) return null
  const p = points.value[hoverIdx.value]
  // tooltip 宽度约 118px，右边界预留，避免贴边被裁
  const tipX = Math.min(p.x + 12, width.value - 122)
  return {
    x: p.x,
    y: p.y,
    value: p.v,
    time: props.times[hoverIdx.value] || '',
    tipX: Math.max(0, tipX),
    tipY: Math.max(0, p.y - 52)
  }
})

const onMove = (e) => {
  if (!count.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const idx = stepX.value > 0 ? Math.round((mx - pad.left) / stepX.value) : 0
  hoverIdx.value = Math.min(count.value - 1, Math.max(0, idx))
}

const onLeave = () => { hoverIdx.value = -1 }

/** 数字压缩：12000 → 1.2w；小数值保留 1 位且去尾零，避免刻度挤在一起 */
function formatNum(v) {
  if (v >= 100000000) return trimZero((v / 100000000).toFixed(1)) + '亿'
  if (v >= 10000) return trimZero((v / 10000).toFixed(1)) + 'w'
  if (v >= 10) return String(Math.round(v))
  return trimZero(v.toFixed(1))
}

const trimZero = (s) => String(Number(s))
</script>

<style scoped>
.tc {
  position: relative;
  width: 100%;
}

.tc-svg {
  display: block;
  overflow: visible;
  cursor: crosshair;
}

.tc-grid line {
  stroke: var(--gw-border-light);
  stroke-width: 1;
}

.tc-grid text {
  font-size: 10px;
  fill: var(--gw-text-tertiary);
}

.tc-line {
  fill: none;
  stroke: var(--gw-info);
  stroke-width: 1.6;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.tc-hover line {
  stroke: var(--gw-text-tertiary);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.tc-hover circle {
  fill: #fff;
  stroke: var(--gw-info);
  stroke-width: 2;
}

.tc-axis text {
  font-size: 10px;
  fill: var(--gw-text-tertiary);
}

.tc-empty {
  position: absolute;
  left: 50%;
  top: 46%;
  transform: translate(-50%, -50%);
  font-size: 12px;
  color: var(--gw-text-tertiary);
  background: rgba(255, 255, 255, .82);
  padding: 2px 8px;
  border-radius: 4px;
  pointer-events: none;
}

.tc-tip {
  position: absolute;
  min-width: 108px;
  padding: 6px 9px;
  background: rgba(26, 26, 26, .92);
  color: #fff;
  border-radius: 6px;
  font-size: 11.5px;
  line-height: 1.5;
  pointer-events: none;
  z-index: 5;
  white-space: nowrap;
}

.tc-tip-time {
  color: rgba(255, 255, 255, .62);
  font-size: 11px;
  margin-bottom: 2px;
}

.tc-tip-val {
  display: flex;
  align-items: center;
  gap: 5px;
}

.tc-tip-val b { font-size: 12.5px; font-weight: 600; }

.tc-tip-val .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gw-info);
  flex-shrink: 0;
}
</style>
