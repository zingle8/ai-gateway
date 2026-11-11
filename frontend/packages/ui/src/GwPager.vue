<template>
  <div class="gw-pager">
    <select class="gw-pager-size" :value="size" @change="onSize">
      <option v-for="s in pageSizes" :key="s" :value="s">{{ s }} 个/页</option>
    </select>
    <button class="gw-pager-btn" type="button" :disabled="current <= 1" @click="go(current - 1)">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
      上一页
    </button>
    <span class="gw-pager-pos">第 {{ current }} / {{ pages }} 页</span>
    <button class="gw-pager-btn" type="button" :disabled="current >= pages" @click="go(current + 1)">
      下一页
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  total: { type: Number, default: 0 },
  current: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] }
})
const emit = defineEmits(['page-change', 'size-change'])

const pages = computed(() => Math.max(1, Math.ceil((props.total || 0) / (props.size || 10))))

function go(p) {
  if (p < 1 || p > pages.value || p === props.current) return
  emit('page-change', p)
}
function onSize(e) {
  const s = Number(e.target.value)
  if (s && s !== props.size) emit('size-change', s)
}
</script>

<style>
.gw-pager { display: inline-flex; align-items: center; gap: 8px; }
.gw-pager-size {
  height: 30px; padding: 0 8px; border: 1px solid var(--gw-border); border-radius: 6px;
  background: var(--gw-card); color: var(--gw-text-secondary); font-size: 12px; cursor: pointer; outline: none;
}
.gw-pager-size:hover { border-color: var(--gw-text-tertiary); }
.gw-pager-btn {
  display: inline-flex; align-items: center; gap: 2px; height: 30px; padding: 0 10px;
  border: 1px solid var(--gw-border); border-radius: 6px; background: var(--gw-card);
  color: var(--gw-text-secondary); font-size: 12px; cursor: pointer; transition: border-color .15s, color .15s;
}
.gw-pager-btn svg { width: 14px; height: 14px; }
.gw-pager-btn:hover:not(:disabled) { border-color: var(--gw-text-tertiary); color: var(--gw-text); }
.gw-pager-btn:disabled { opacity: .45; cursor: not-allowed; }
.gw-pager-pos { font-size: 12px; color: var(--gw-text-tertiary); white-space: nowrap; }
</style>
