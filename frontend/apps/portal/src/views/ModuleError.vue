<template>
  <div class="mf-error">
    <div class="mf-error-card">
      <div class="mf-error-icon">⚠️</div>
      <h2>模块「{{ title }}」加载失败</h2>
      <p class="gw-sm gw-muted">
        微前端联邦模式下该模块的 remote 未能加载（独立部署的模块未启动、地址错误或网络异常）。
        单模块故障不影响其他模块，其他菜单仍可正常使用。
      </p>
      <p class="gw-sm gw-mono gw-muted">{{ remote }}</p>
      <div class="mf-error-actions">
        <el-button type="primary" size="small" @click="retry">重新加载</el-button>
        <el-button size="small" @click="$router.push('/overview')">回总览</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 微前端模块加载失败占位页（v0.2 第 16 项）：
 * 联邦模式下任一 remote 加载失败，宿主用本页兜底——菜单可见、路由可进，
 * 但不白屏、不拖垮其他模块。
 */
defineProps({
  title: { type: String, default: '未知模块' },
  remote: { type: String, default: '' }
})

const retry = () => window.location.reload()
</script>

<style scoped>
.mf-error {
  display: flex;
  justify-content: center;
  padding: 48px 16px;
}

.mf-error-card {
  max-width: 480px;
  text-align: center;
  background: var(--gw-card, #fff);
  border: 1px solid var(--gw-border-light, #e5e7eb);
  border-radius: 12px;
  padding: 32px 28px;
}

.mf-error-icon {
  font-size: 32px;
  margin-bottom: 12px;
}

.mf-error-card h2 {
  margin: 0 0 10px;
  font-size: 16px;
}

.mf-error-card p {
  margin: 6px 0;
  line-height: 1.7;
}

.mf-error-actions {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 8px;
}
</style>
