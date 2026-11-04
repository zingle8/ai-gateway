<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-text">{{ brand }}</span>
      <span class="brand-mark">微服务v0.1</span>
    </div>
    <nav class="nav">
      <template v-for="(item, i) in menus" :key="i">
        <div v-if="item.path" class="nav-item" :class="{ active: isActive(item.path) }" @click="go(item.path)">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </div>
        <div v-else class="nav-group">
          <div class="nav-group-title">{{ item.title }}</div>
          <div
            v-for="child in item.children"
            :key="child.path"
            class="nav-item sub"
            :class="{ active: isActive(child.path) }"
            @click="go(child.path)"
          >
            <el-icon><component :is="child.icon" /></el-icon>
            <span>{{ child.title }}</span>
          </div>
        </div>
      </template>
    </nav>
  </aside>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'

defineProps({
  menus: { type: Array, default: () => [] },
  brand: { type: String, default: 'API网关平台' }
})

const router = useRouter()
const route = useRoute()

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')
const go = (path) => router.push(path)
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
  padding: 10px 10px 4px 16px;
  font-size: 11px;
  color: var(--gw-text-tertiary);
  letter-spacing: .04em;
}
</style>
