<template>
  <div class="trp" :class="'trp-' + size">
    <button
      v-for="r in ranges"
      :key="r.value"
      type="button"
      class="trp-btn"
      :class="{ active: modelValue === r.value }"
      :title="r.desc"
      @click="pick(r.value)"
    >
      {{ r.label }}
    </button>
  </div>
</template>

<script setup>
import { TIME_RANGES } from '@zimo/shared'

/**
 * 统一时间区间选择器：网关总览 / 数据统计 / 服务发现 / 服务拓扑 共用，
 * 保证四个页面的时间维度完全一致（1分钟 ~ 30天 共 10 档）。
 */
const props = defineProps({
  modelValue: { type: String, default: '24h' },
  ranges: { type: Array, default: () => TIME_RANGES },
  size: { type: String, default: 'default' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const pick = (value) => {
  if (value === props.modelValue) return
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<style scoped>
.trp {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: 10px;
  background: var(--gw-bg-subtle, #f1f3f5);
  flex-wrap: wrap;
}

.trp-btn {
  border: none;
  background: transparent;
  color: var(--gw-text-secondary);
  border-radius: 8px;
  padding: 5px 11px;
  font-size: 13px;
  line-height: 1.2;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color .15s, color .15s, box-shadow .15s;
}

.trp-btn:hover {
  color: var(--gw-text-primary);
}

.trp-btn.active {
  background: var(--gw-bg-card, #fff);
  color: var(--gw-text-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, .12);
}

.trp-sm .trp-btn {
  padding: 3px 9px;
  font-size: 12px;
}
</style>
