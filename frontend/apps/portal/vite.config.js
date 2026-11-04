import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const r = (p) => fileURLToPath(new URL(p, import.meta.url))

/**
 * 各前端业务模块与后端微服务一一对应，
 * dev 阶段通过 /api/<service> 前缀把请求转发到对应的微服务端口。
 */
/**
 * 所有 /api 请求统一走 jv-gateway-proxy（28090）：
 * 一是让平台自己的流量也被网关采集（日志追踪里看得到控制台每次请求），
 * 二是路由集中在 tb_gw_route 管理，后端换端口不用动前端。
 */
const SERVICES = {
  '/api': 'http://127.0.0.1:28090' // jv-gateway-proxy（按 tb_gw_route 转发）
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
      '@zimo/module-onboard': r('../../modules/onboard')
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
    chunkSizeWarningLimit: 1200
  }
})
