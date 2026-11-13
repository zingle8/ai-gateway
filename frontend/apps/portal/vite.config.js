import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import federation from '@originjs/vite-plugin-federation'

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

/** 微前端模块地址表（mf-manifest.json 为唯一事实来源） */
const MF_MANIFEST = JSON.parse(readFileSync(r('../../mf-manifest.json'), 'utf-8'))

/**
 * 双模式：
 *  - 默认（单体模式）：源码别名直连模块目录，一个 dev server / 一次构建出全量产物，
 *    即 v0.1/v0.2 一直以来的形态，行为不变；
 *  - VITE_MF=true（联邦模式）：portal 作为 federation host，按模块地址表动态加载
 *    8 个 remote（./module 暴露），模块独立 dev server（28091–28098）或独立构建产物，
 *    单模块故障时宿主占位兜底不白屏（见 src/views/ModuleError.vue）。
 */
const mfMode = process.env.VITE_MF === 'true'

/** 联邦模式 remote 地址：dev 用模块 dev server；构建用 VITE_MF_BASE 前缀（默认 /mf） */
function mfRemotes(mode) {
  const base = (process.env.VITE_MF_BASE || '/mf').replace(/\/$/, '')
  const remotes = {}
  for (const [key, m] of Object.entries(MF_MANIFEST.modules)) {
    remotes[m.name] = mode === 'production'
      ? `${base}/${key}/${MF_MANIFEST.remotePath}`
      : `http://localhost:${m.devPort}/${MF_MANIFEST.remotePath}`
  }
  return remotes
}

const MF_SHARED = {
  vue: { singleton: true, requiredVersion: false },
  'vue-router': { singleton: true, requiredVersion: false },
  pinia: { singleton: true, requiredVersion: false },
  'element-plus': { singleton: true, requiredVersion: false },
  '@element-plus/icons-vue': { singleton: true, requiredVersion: false },
  // 本地包必须显式给 version：插件会去读 <包>/package.json，alias 指向源码目录时读不到
  '@zimo/shared': { singleton: true, requiredVersion: false, version: '1.0.0' }
  // '@zimo/ui' 故意不共享，理由见 tools/federation.js
}

export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    ...(mfMode
      ? [
          federation({
            name: 'mf_portal',
            remotes: mfRemotes(mode),
            shared: MF_SHARED
          })
        ]
      : [])
  ],
  resolve: {
    alias: {
      '@': r('./src'),
      '@zimo/shared': r('../../packages/shared/src'),
      '@zimo/ui': r('../../packages/ui/src'),
      // remote 加载器：联邦模式用真实实现（federation 插件改写 mf_* 导入），
      // 单体模式用空存根，保证 router/index.js 无需感知模式差异、构建不碰 remote 模块名
      'mf-remote-loaders': mfMode
        ? r('./src/mf/remote-loaders.js')
        : r('./src/mf/remote-loaders-stub.js'),
      // 单体模式下模块以源码别名直连；联邦模式下模块只通过 remote 加载，
      // 不能留别名，否则宿主仍会把模块打进自己的产物
      ...(!mfMode
        ? {
            '@zimo/module-overview': r('../../modules/overview'),
            '@zimo/module-stats': r('../../modules/stats'),
            '@zimo/module-discovery': r('../../modules/discovery'),
            '@zimo/module-trace': r('../../modules/trace'),
            '@zimo/module-system': r('../../modules/system'),
            '@zimo/module-onboard': r('../../modules/onboard'),
            '@zimo/module-auth': r('../../modules/auth'),
            '@zimo/module-perm': r('../../modules/perm')
          }
        : {})
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
    // 模块装配用了顶层 await（双模式统一写法），目标环境需 ES2022+；
    // federation 产物要求 esnext（动态 import 不可降级）
    target: mfMode ? 'esnext' : 'es2022',
    rollupOptions: mfMode
      ? { output: { manualChunks: undefined } }
      : {
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
}))
