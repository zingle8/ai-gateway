<template>
  <div class="page">
    <div class="gw-card">
      <div class="gw-card-hd">{{ route.meta.title }}</div>
      <div class="gw-card-bd placeholder">
        <el-empty description="该模块正在建设中，页面结构与交互将参照现有模块实现" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style scoped>
.placeholder { padding: 40px 0; }
</style>
