import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const r = (p) => fileURLToPath(new URL(p, import.meta.url))

/**
 * 各前端业务模块与后端微服务一一对应，
 * dev 阶段通过 /api/<service> 前缀把请求转发到对应的微服务端口。
 */
/**
 * 所有 /api 请求统一走 jv-api-gateway-proxy（28090）：
 * 一是让平台自己的流量也被网关采集（日志追踪里看得到控制台每次请求），
 * 二是路由集中在 tb_gw_route 管理，后端换端口不用动前端。
 */
const SERVICES = {
  '/api': 'http://127.0.0.1:28090', // jv-api-gateway-proxy（按 tb_gw_route 转发）
  '/health': 'http://127.0.0.1:28090' // 网关自身的菜单健康快照（不走 /api 转发）
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': r('./src'),
      '@zimo/shared': r('../../packages/shared/src'),
      '@zimo/ui': r('../../packages/ui/src'),
      '@zimo/module-overview': r('../../modules/overview'),
      '@zimo/module-stats': r('../../modules/stats'),
      '@zimo/module-discovery': r('../../modules/discovery'),
      '@zimo/module-trace': r('../../modules/trace'),
      '@zimo/module-system': r('../../modules/system'),
      '@zimo/module-onboard': r('../../modules/onboard'),
      '@zimo/module-auth': r('../../modules/auth'),
      '@zimo/module-perm': r('../../modules/perm')
    }
  },
  server: {
    port: 28080,
    open: false,
    fs: { allow: [r('../..')] },
    proxy: Object.fromEntries(
      Object.entries(SERVICES).map(([prefix, target]) => [prefix, { target, changeOrigin: true }])
    )
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200,
    /**
     * 按一级菜单分包：每个业务模块独立 chunk，改一个模块不会让其他模块的
     * 缓存失效，发布时互不干扰（微前端前的务实边界：物理目录 + 产物分包）。
     */
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            const hit = id.match(/modules\/([a-z-]+)\//)
            if (hit) return 'module-' + hit[1]
            return undefined
          }
          if (id.includes('element-plus')) return 'vendor-element'
          if (id.includes('vue-router') || id.includes('/@vue/') || id.includes('/vue/')) return 'vendor-vue'
          return 'vendor'
        }
      }
    }
  }
})
