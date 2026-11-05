<template>
  <div class="pane-tabs">
    <!-- 页内区块切换：pill 风格，与顶部菜单/路由级 Tab 明确区分开 -->
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
.pane-tabs-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.pane-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid var(--gw-border);
  border-radius: 6px;
  background: var(--gw-card);
  color: var(--gw-text-secondary);
  font-size: 12.5px;
  line-height: 18px;
  cursor: pointer;
  transition: all .15s;
  font-family: inherit;
}

.pane-tab:hover {
  border-color: #d5d7dc;
  color: var(--gw-text);
}

.pane-tab.active {
  background: var(--gw-primary);
  border-color: var(--gw-primary);
  color: #fff;
  font-weight: 600;
}

.pane-tab-badge {
  min-width: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--gw-border-light);
  color: var(--gw-text-secondary);
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  font-weight: 400;
}

.pane-tab.active .pane-tab-badge {
  background: rgba(255, 255, 255, .22);
  color: #fff;
}
</style>
