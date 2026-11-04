/**
 * 全平台统一的时间统计维度。
 *
 * 十个档位横跨「秒级实时排查 → 月度趋势」，同时约定两件事：
 *   1. seconds：时间窗跨度（后端按 since = now - seconds 过滤）
 *   2. bucket：趋势图桶宽（秒），区间内固定切 60 个点，保证图表密度一致
 *
 * 24小时 与 1天 跨度相同但语义不同：前者是滚动 24 小时，后者是自然日（今日 00:00 起），
 * 后端据此区分，避免两个按钮出来一模一样的结果。
 */
export const TIME_RANGES = [
  { value: '1m', label: '1分钟', seconds: 60, bucket: 1, desc: '1 秒 / 点 · 近 1 分钟' },
  { value: '5m', label: '5分钟', seconds: 300, bucket: 5, desc: '5 秒 / 点 · 近 5 分钟' },
  { value: '10m', label: '10分钟', seconds: 600, bucket: 10, desc: '10 秒 / 点 · 近 10 分钟' },
  { value: '30m', label: '30分钟', seconds: 1800, bucket: 30, desc: '30 秒 / 点 · 近 30 分钟' },
  { value: '1h', label: '1小时', seconds: 3600, bucket: 60, desc: '1 分钟 / 点 · 近 1 小时' },
  { value: '2h', label: '2小时', seconds: 7200, bucket: 120, desc: '2 分钟 / 点 · 近 2 小时' },
  { value: '24h', label: '24小时', seconds: 86400, bucket: 1440, desc: '24 分钟 / 点 · 近 24 小时' },
  { value: '1d', label: '1天', seconds: 86400, bucket: 1440, desc: '24 分钟 / 点 · 今日 00:00 起' },
  { value: '7d', label: '7天', seconds: 604800, bucket: 10080, desc: '2.8 小时 / 点 · 近 7 天' },
  { value: '30d', label: '30天', seconds: 2592000, bucket: 43200, desc: '12 小时 / 点 · 近 30 天' }
]

/** 默认区间：24 小时（与原各页面默认值一致） */
export const DEFAULT_TIME_RANGE = '24h'

/** 兜底取 24h，非法值不抛错 */
export function timeRangeOf(value) {
  return TIME_RANGES.find((r) => r.value === value) || TIME_RANGES[6]
}

/** 区间文案，如「近 24 小时」 */
export function timeRangeLabel(value) {
  return '近 ' + timeRangeOf(value).label
}
