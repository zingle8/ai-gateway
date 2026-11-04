<template>
  <header class="topbar">
    <div class="crumb">
      <span class="page-title">{{ pageTitle }}</span>
      <span class="page-subtitle">{{ pageSubtitle }}</span>
    </div>
    <div class="topbar-right">
      <el-tag size="small" type="success" effect="plain">Nacos 已连接</el-tag>
      <el-dropdown trigger="click" @command="onCommand">
        <div class="user-chip">
          <el-avatar :size="24" class="avatar">{{ initial }}</el-avatar>
          <span class="user-name">{{ nickname }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人信息</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore, logout as logoutApi } from '@zimo/shared'
import { ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const pageTitle = computed(() => route.meta.title || '')
const pageSubtitle = computed(() => route.meta.subtitle || '')
const initial = computed(() => (store.nickname || 'U').slice(0, 1))
const nickname = computed(() => store.nickname)

const onCommand = async (cmd) => {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    try {
      await logoutApi()
    } catch (e) {
      /* 忽略后端异常，前端仍需清理会话 */
    }
    store.forceLogout()
    router.replace('/login')
    ElMessage.success('已退出登录')
  }
}
</script>

<style scoped>
.topbar {
  height: var(--gw-topbar-h);
  flex-shrink: 0;
  background: var(--gw-card);
  border-bottom: 1px solid var(--gw-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.crumb {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.page-title { font-size: 15px; font-weight: 600; }
.page-subtitle {
  font-size: 12px;
  color: var(--gw-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 6px;
  cursor: pointer;
}

.user-chip:hover { background: var(--sidebar-hover); }
.avatar { background: var(--gw-primary); font-size: 12px; }
.user-name { font-size: 12.5px; }
</style>
