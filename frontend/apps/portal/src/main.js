import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

// 菜单图标：各模块 menu 里写的是组件名字符串（如 'Odometer'），需全局注册才能被
// <component :is="icon" /> 解析。按需注册，新增菜单图标时在这里补一行。
import {
  Odometer, DataAnalysis, Grid, Tickets, Setting, User, Avatar,
  Connection, Promotion, MagicStick, Share, Upload,
  Lock, Key, Link, Timer, SetUp, Tools
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import '@zimo/shared/styles/index.css'
import '@zimo/shared/styles/components.css'

const icons = {
  Odometer,       // 网关总览：仪表盘
  DataAnalysis,   // 数据统计：分析图表
  Grid,           // 服务发现：服务实例矩阵
  Tickets,        // 日志追踪：日志条目
  Setting,        // 系统管理：设置
  User,           // 用户管理
  Avatar,         // 角色管理：角色组
  Connection,     // 接入中心：连接
  Promotion,      // 系统接入：接入 / 发送
  MagicStick,     // 自动接入：一键自动化
  Share,          // 路由配置：转发分支
  Upload,         // 上线发布：发布上线
  Lock,           // 认证与安全：安全
  Key,            // 登录方式：凭证
  Link,           // 单点登录：关联
  Timer,          // 令牌与会话：时效
  SetUp,          // 安全策略：策略
  Tools           // 系统设置：工具
}

const app = createApp(App)
Object.entries(icons).forEach(([name, icon]) => app.component(name, icon))

app.use(createPinia())
  .use(router)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app')
