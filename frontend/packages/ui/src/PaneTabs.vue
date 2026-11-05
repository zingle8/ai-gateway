<template>
  <div class="pane-tabs">
    <!-- 页内区块切换：连体分段控件，浅绿底 + 激活白块，与侧边栏激活态同一色系 -->
    <div class="pane-tabs-nav" role="tablist">
      <button
        v-for="t in tabs"
        :key="t.name"
        type="button"
        role="tab"
        class="pane-tab"
        :class="{ active: t.name === modelValue }"
        :aria-selected="t.name === modelValue"
        @click="$emit('update:modelValue', t.name)"
      >
        <span>{{ t.label }}</span>
        <span v-if="t.badge != null" class="pane-tab-badge">{{ t.badge }}</span>
      </button>
    </div>
    <div class="pane-tabs-body">
      <slot :name="modelValue" />
    </div>
  </div>
</template>

<script setup>
/**
 * 页内区块 Tab。
 *
 * 用于一个页面里有多个并列内容块（如「登录因子 / 验证码通道」），
 * 内容块较多时按 Tab 切换，避免整页纵向堆叠。
 *
 * 用法：
 *   <PaneTabs v-model="tab" :tabs="[{ name: 'a', label: '标题' }]">
 *     <template #a>...</template>
 *   </PaneTabs>
 *
 * 不做路由绑定（与侧边栏子菜单的路由级 Tab 不同），切换仅换内容。
 */
defineProps({
  modelValue: { type: String, required: true },
  /** [{ name, label, badge? }]，badge 用于显示条数等轻量计数 */
  tabs: { type: Array, default: () => [] }
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
/* 连体分段控件：整体浅绿底，按钮之间无间隙，激活项浮起为白块 */
.pane-tabs-nav {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  overflow-x: auto;
  padding: 2px;
  margin-bottom: 10px;
  background: var(--sidebar-hover);
  border: 1px solid #E3F3EA;
  border-radius: 8px;
}

.pane-tabs-nav::-webkit-scrollbar { height: 0; }

.pane-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 5px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #4B6B58;
  font-size: 12.5px;
  line-height: 18px;
  cursor: pointer;
  transition: background .15s, color .15s, box-shadow .15s;
  font-family: inherit;
}

.pane-tab:hover {
  color: var(--sidebar-active-text);
  background: var(--sidebar-active);
}

.pane-tab.active {
  background: var(--gw-card);
  color: var(--sidebar-active-text);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(22, 163, 74, .12);
}

.pane-tab-badge {
  min-width: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--sidebar-active);
  color: var(--sidebar-active-text);
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  font-weight: 400;
}

.pane-tab.active .pane-tab-badge {
  background: var(--sidebar-active);
  color: var(--sidebar-active-text);
}
</style>
