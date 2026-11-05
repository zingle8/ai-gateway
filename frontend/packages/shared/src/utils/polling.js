/**
 * 控制台数据自动轮询。
 *
 * 频率来自系统设置 `platform.ui.refresh.interval`（秒，公开配置 /configs/public），
 * 修改后最迟 30 秒生效（公开配置每 30 秒重读一次，读取失败沿用上次值）。
 *
 * 网关总览 / 数据统计 / 服务发现共用，页面卸载自动停止。
 */
import { onScopeDispose } from 'vue'
import request from '../request'

const KEY = 'platform.ui.refresh.interval'
const DEFAULT_SECONDS = 5
const MIN_SECONDS = 2
const MAX_SECONDS = 300
const CONFIG_TTL_MS = 30_000

let cache = { value: DEFAULT_SECONDS, fetchedAt: 0 }

/** 当前轮询间隔（秒），带 30 秒配置缓存 */
export async function getRefreshInterval() {
  if (Date.now() - cache.fetchedAt > CONFIG_TTL_MS) {
    cache.fetchedAt = Date.now()
    try {
      const flags = await request.get('/configs/public')
      const v = Number(flags?.[KEY])
      if (Number.isFinite(v)) {
        cache.value = Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, Math.round(v)))
      }
    } catch {
      // 配置服务不可用时沿用上次值，不影响页面轮询
    }
  }
  return cache.value
}

/**
 * 页面数据自动刷新。
 *
 * @param {() => Promise<void>} load 页面自己的数据加载函数（异常需自行吞掉）
 * @param {{ immediate?: boolean }} options immediate=false 时只启动定时器、不立即加载
 */
export function useAutoRefresh(load, { immediate = true } = {}) {
  let timer = null
  let stopped = false

  const loop = async () => {
    if (stopped) return
    if (immediate || timer) {
      try {
        await load()
      } catch {
        // 加载失败不中断轮询
      }
    }
    if (stopped) return
    const seconds = await getRefreshInterval()
    timer = setTimeout(loop, seconds * 1000)
  }

  loop()
  onScopeDispose(() => {
    stopped = true
    if (timer) clearTimeout(timer)
  })
}
