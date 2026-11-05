<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-text">{{ brand }}</span>
      <span class="brand-mark">微服务v0.1</span>
    </div>
    <nav class="nav">
      <template v-for="(item, i) in menus" :key="i">
        <!-- 一级菜单（无子项） -->
        <div v-if="item.path">
          <el-tooltip
            :disabled="!dotOf(item.path)"
            :content="tipOf(item.path, item.title)"
            placement="right"
            :show-after="100"
          >
            <div
              class="nav-item"
              :class="{ active: isActive(item.path) }"
              :title="health.enabled ? undefined : item.title"
              @click="go(item.path)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span class="nav-title">{{ item.title }}</span>
              <i v-if="dotOf(item.path)" class="svc-dot" :class="'is-' + dotOf(item.path)" />
            </div>
          </el-tooltip>
        </div>

        <!-- 分组 -->
        <div v-else class="nav-group">
          <div class="nav-group-title">
            <span>{{ item.title }}</span>
            <i v-if="groupDot(item) && health.enabled" class="svc-dot" :class="'is-' + groupDot(item)" />
          </div>
          <div v-for="child in item.children" :key="child.path">
            <el-tooltip
              :disabled="!dotOf(child.path)"
              :content="tipOf(child.path, child.title)"
              placement="right"
              :show-after="100"
            >
              <div
                class="nav-item sub"
                :class="{ active: isActive(child.path) }"
                :title="health.enabled ? undefined : child.title"
                @click="go(child.path)"
              >
                <el-icon><component :is="child.icon" /></el-icon>
                <span class="nav-title">{{ child.title }}</span>
                <i v-if="dotOf(child.path)" class="svc-dot" :class="'is-' + dotOf(child.path)" />
              </div>
            </el-tooltip>
          </div>
        </div>
      </template>
    </nav>
  </aside>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'

const props = defineProps({
  menus: { type: Array, default: () => [] },
  brand: { type: String, default: 'API网关平台' },
  /** 菜单 path → 后端服务名 */
  services: { type: Object, default: () => ({}) },
  /** { enabled, statuses: { [service]: { status, latencyMs, detail } } } */
  health: { type: Object, default: () => ({ enabled: false, statuses: {} }) }
})

const router = useRouter()
const route = useRoute()

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')
const go = (path) => router.push(path)

const STATUS_TEXT = { UP: '运行正常', DOWN: '未发现运行 / 检查不到', ERROR: '运行异常' }

/** 最长前缀匹配：菜单 path → 服务名 */
const serviceOf = (path) => {
  if (props.services[path]) return props.services[path]
  const hit = Object.keys(props.services)
    .filter((prefix) => path.startsWith(prefix))
    .sort((a, b) => b.length - a.length)[0]
  return hit ? props.services[hit] : null
}

/** 返回 UP / DOWN / ERROR，未开启或未配置服务时返回空 */
const dotOf = (path) => {
  if (!props.health.enabled) return ''
  const service = serviceOf(path)
  if (!service) return ''
  return props.health.statuses[service]?.status || 'DOWN'
}

const tipOf = (path, title) => {
  const service = serviceOf(path)
  const row = service ? props.health.statuses[service] : null
  const status = row ? STATUS_TEXT[row.status] || row.status : '未探测'
  const latency = row && row.latencyMs >= 0 ? `（${row.latencyMs}ms）` : ''
  return `${title} ｜ 后端服务：${service || '未配置'} ｜ 运行状态：${status}${latency}`
}

/** 分组标识取子项中最差状态：ERROR > DOWN > UP */
const groupDot = (group) => {
  if (!props.health.enabled) return ''
  const dots = (group.children || []).map((c) => dotOf(c.path)).filter(Boolean)
  if (dots.includes('ERROR')) return 'ERROR'
  if (dots.includes('DOWN')) return 'DOWN'
  if (dots.includes('UP')) return 'UP'
  return ''
}
</script>

<style scoped>
.sidebar {
  width: var(--gw-sidebar-w);
  flex-shrink: 0;
  background: var(--gw-card);
  border-right: 1px solid var(--gw-border);
  display: flex;
  flex-direction: column;
}

.brand {
  height: var(--gw-topbar-h);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  border-bottom: 1px solid var(--gw-border-light);
}

.brand-mark {
  /* 标题后缀小徽标：比标题小一号，字号不再往下压，避免中文发糊 */
  height: 17px;
  padding: 0 5px;
  border-radius: 3px;
  background: var(--gw-primary);
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: .02em;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  flex-shrink: 0;
}

.brand-text { font-size: 14px; font-weight: 600; }

.nav { flex: 1; overflow-y: auto; padding: 8px; }

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 6px;
  color: var(--gw-text-secondary);
  cursor: pointer;
  font-size: 13px;
  user-select: none;
}

/* 图标比文字略大一号，13px 文字配 15px 图标在侧边栏里更清晰 */
.nav-item .el-icon { font-size: 15px; flex-shrink: 0; }
.nav-item.sub .el-icon { font-size: 14px; }

.nav-item:hover { background: var(--sidebar-hover); color: var(--gw-text); }
.nav-item:active { background: var(--sidebar-active); }

/* 激活项：浅绿底 + 深绿字，点击态与激活态保持同色，避免点击瞬间闪烁 */
.nav-item.active {
  background: var(--sidebar-active);
  color: var(--sidebar-active-text);
  font-weight: 600;
}

.nav-item.sub { padding-left: 20px; font-size: 12.5px; }
.nav-group { margin-bottom: 6px; }

.nav-group-title {
  /* 分组标题比一级菜单右移一档，同时略靠左于子项，形成 一级 10 / 标题 16 / 子项 20 的层级 */
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 10px 4px 16px;
  font-size: 11px;
  color: var(--gw-text-tertiary);
  letter-spacing: .04em;
}

.nav-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 服务运行状态点：绿=正常 灰=不可达 红=有异常 */
.svc-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-left: 2px;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, .9);
}

.svc-dot.is-UP { background: #16A34A; }
.svc-dot.is-DOWN { background: #C4C8CE; }
.svc-dot.is-ERROR { background: #DC2626; }

/* ===== 响应式：中等宽度收起为图标栏，只留图标（悬停有 title 提示） ===== */
@media (max-width: 1100px) {
  .sidebar { width: 60px; }
  .brand { justify-content: center; padding: 0 6px; gap: 0; }
  .brand-text,
  .brand-mark,
  .nav-group-title { display: none; }
  .nav-item,
  .nav-item.sub {
    justify-content: center;
    padding: 9px 0;
  }
  .nav-item span { display: none; }
  .svc-dot { display: none; }
  .nav-item .el-icon { font-size: 17px; }
  /* 图标模式下一级与子项之间的分组分隔线，弥补分组标题消失后的层级感 */
  .nav-group + .nav-item,
  .nav-item + .nav-group { border-top: 1px solid var(--gw-border-light); margin-top: 4px; padding-top: 8px; }
}
</style>
