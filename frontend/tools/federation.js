import { fileURLToPath } from 'node:url'
import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import federation from '@originjs/vite-plugin-federation'

/**
 * 微前端（v0.2 第 16 项）模块统一 Vite 配置。
 *
 * 地址表 frontend/mf-manifest.json 是唯一事实来源：每个模块从这里取
 * federation 名称与独立 dev server 端口；portal 也从这里拼 remote 地址。
 * 所有模块只暴露 './module'（模块根目录 index.js 的 { menu, routes }），
 * vue / vue-router / pinia / element-plus / @zimo/shared / @zimo/ui 与宿主
 * 单例共享（singleton），避免多份 Vue 实例与状态分裂。
 */

const r = (p) => fileURLToPath(new URL(p, import.meta.url))
const manifest = JSON.parse(readFileSync(r('../mf-manifest.json'), 'utf-8'))

export const MF_SHARED = {
  vue: { singleton: true, requiredVersion: false },
  'vue-router': { singleton: true, requiredVersion: false },
  pinia: { singleton: true, requiredVersion: false },
  'element-plus': { singleton: true, requiredVersion: false },
  '@element-plus/icons-vue': { singleton: true, requiredVersion: false },
  // 本地包必须显式给 version：插件会去读 <包>/package.json，alias 指向源码目录时读不到
  '@zimo/shared': { singleton: true, requiredVersion: false, version: '1.0.0' }
  // '@zimo/ui' 故意不共享：它是纯展示组件（无状态），共享反而会把 Shell 的样式
  // 抽到独立 css 且宿主 index.html 不引用（插件已知问题），导致侧边栏/布局丢样式
}

/** 模块 federation 名（如 mf_stats）与 './module' 暴露 */
export function defineModuleConfig({ moduleName, extraAlias = {} }) {
  const entry = manifest.modules[moduleName]
  if (!entry) {
    throw new Error(`mf-manifest.json 中没有模块 ${moduleName}`)
  }
  return defineConfig({
    plugins: [
      vue(),
      federation({
        name: entry.name,
        filename: 'remoteEntry.js',
        exposes: { './module': './index.js' },
        shared: MF_SHARED
      })
    ],
    resolve: {
      alias: {
        // 注意：r() 以本文件（frontend/tools/）为基准，packages 在上一级
        '@zimo/shared': r('../packages/shared/src'),
        '@zimo/ui': r('../packages/ui/src'),
        ...extraAlias
      }
    },
    server: {
      port: entry.devPort,
      cors: true,
      fs: { allow: [r('../..')] }
    },
    // 联调走 build + preview（见 scripts/dev-mf.sh），preview 必须开 CORS，
    // 否则宿主跨端口 import remoteEntry 会被浏览器拦截
    preview: {
      port: entry.devPort,
      strictPort: true,
      cors: true
    },
    // federation 产物要求：不压缩 + 不按入口切 CSS，remoteEntry 引用的 chunk 才能对齐
    build: {
      target: 'esnext',
      minify: false,
      cssCodeSplit: false,
      outDir: 'dist'
    }
  })
}
