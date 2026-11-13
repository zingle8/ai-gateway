/**
 * 模块独立预览入口（v0.2 第 16 项）。
 *
 * 模块既能被宿主 portal 以 federation remote 加载（./module），
 * 也能单独 build + preview 直接打开——挂载模块第一个页面，方便独立开发调试。
 */
import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@zimo/shared/styles/index.css'
import '@zimo/shared/styles/components.css'
import module from './index.js'

const first = module.routes[0]
const app = createApp({ render: () => h(first.component) })
app.use(createPinia())
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
