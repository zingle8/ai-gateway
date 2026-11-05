<template>
  <div class="auth-shell">
    <!-- Tab 条固定在内容区上方，不随页面滚动 -->
    <div class="auth-tabs">
      <el-tabs :model-value="activeName" @tab-change="onTabChange">
        <el-tab-pane v-for="t in TABS" :key="t.name" :name="t.name">
          <template #label>
            <span class="tab-label">
              <el-icon><component :is="t.icon" /></el-icon>
              {{ t.label }}
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <span class="gw-sm gw-muted tab-hint">{{ current.subtitle }}</span>
    </div>

    <!-- keep-alive：切 Tab 不重新拉数据，表单填写内容也不会丢 -->
    <div ref="bodyRef" class="auth-body">
      <keep-alive>
        <component :is="current.component" />
      </keep-alive>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import UserIndexView from './user/Index.vue'
import LoginMethodsView from './LoginMethods.vue'
import SsoView from './Sso.vue'
import SessionsView from './Sessions.vue'
import SecurityPolicyView from './SecurityPolicy.vue'

/**
 * 认证与安全 = 一个入口 + 五个 Tab。
 *
 * 菜单只保留「认证与安全」一项（原先五个子项全部铺在侧边栏，层级过深），
 * 每个 Tab 仍是独立路由，刷新 / 分享链接都能直接落到对应 Tab。
 */
const TABS = [
  {
    name: 'user',
    path: '/auth/user',
    label: '用户管理',
    icon: 'User',
    component: UserIndexView,
    subtitle: '控制台账号与 C 端账号统一管理（按类型切换）'
  },
  {
    name: 'login-methods',
    path: '/auth/login-methods',
    label: '登录方式',
    icon: 'Key',
    component: LoginMethodsView,
    subtitle: '密码 / 验证码 / 扫码 / SSO 各登录因子的开关与通道配置'
  },
  {
    name: 'sso',
    path: '/auth/sso',
    label: '单点登录',
    icon: 'Link',
    component: SsoView,
    subtitle: '外部 IdP 接入配置、账号绑定关系与 SSO 登录审计'
  },
  {
    name: 'sessions',
    path: '/auth/sessions',
    label: '令牌与会话',
    icon: 'Timer',
    component: SessionsView,
    subtitle: '在线会话、refresh 家族与令牌吊销记录'
  },
  {
    name: 'policy',
    path: '/auth/policy',
    label: '安全策略',
    icon: 'SetUp',
    component: SecurityPolicyView,
    subtitle: '登录保护、验证码限频、密码策略与双因子要求'
  }
]

const route = useRoute()
const router = useRouter()

/** 当前 Tab：按路径精确匹配，未知路径回落到用户管理 */
const current = computed(() => TABS.find((t) => t.path === route.path) || TABS[0])
const activeName = computed(() => current.value.name)

const onTabChange = (name) => {
  const target = TABS.find((t) => t.name === name)
  if (target && target.path !== route.path) router.push(target.path)
}

const bodyRef = ref(null)
watch(
  () => route.path,
  () => {
    // 内容区滚动条在每个子页面的 .page 容器上，切 Tab 后回到顶部
    nextTick(() => {
      const el = bodyRef.value && bodyRef.value.querySelector('.page')
      if (el) el.scrollTop = 0
    })
  }
)
</script>

<style scoped>
/* 三段式：Tab 条固定 / 内容区独立滚动（子页面 .page 自带 overflow-y） */
.auth-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.auth-tabs {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 16px;
  background: var(--gw-card);
  border-bottom: 1px solid var(--gw-border);
}

.auth-tabs :deep(.el-tabs__header) { margin: 0; }
.auth-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.auth-tabs :deep(.el-tabs__content) { display: none; }
.auth-tabs :deep(.el-tabs__item) { height: 46px; line-height: 46px; }
.auth-tabs :deep(.el-tabs__active-bar) { background: var(--gw-primary); }

.auth-tabs :deep(.el-tabs__item.is-active) {
  color: var(--gw-text);
  font-weight: 600;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.tab-label .el-icon { font-size: 14px; }

/* 当前 Tab 的说明文字：窄屏隐藏，避免把 Tab 挤到换行 */
.tab-hint {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 1100px) {
  .tab-hint { display: none; }
}

.auth-body {
  flex: 1;
  min-height: 0;
  display: flex;
}
</style>
