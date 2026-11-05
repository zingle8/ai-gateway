/**
 * 通用格式化工具（各业务模块共用）
 */

/** 字节数 → B / KB / MB 可读文本 */
export function fmtBytes(n) {
  const v = Number(n) || 0
  if (v < 1024) return v + ' B'
  const kb = v / 1024
  return kb < 1024
    ? v.toLocaleString() + ' B（' + kb.toFixed(2) + ' KB）'
    : v.toLocaleString() + ' B（' + (kb / 1024).toFixed(2) + ' MB）'
}

/** LocalDateTime / ISO 字符串 → 'YYYY-MM-DD HH:mm:ss' */
export function fmtTime(t) {
  return t ? String(t).replace('T', ' ') : '-'
}

/** 状态码 → Element Plus 标签类型 */
export function statusTagType(code) {
  if (code === 200) return 'success'
  if (code >= 500) return 'danger'
  return 'warning'
}

/** 健康状态 → 状态点样式类 */
export function dotClass(status) {
  if (status === '健康') return 'success'
  if (status === '亚健康') return 'warning'
  if (status === '异常' || status === '离线') return 'danger'
  return ''
}

/**
 * Header 原文按 ': ' 拆成 key & value；
 * 无冒号的起始行（如 'GET /api/v1/order/list HTTP/1.1'）单独成行。
 */
export function parseHeaders(text) {
  if (!text) return []
  return String(text)
    .split('\n')
    .filter((line) => line.trim())
    .map((line) => {
      const i = line.indexOf(': ')
      return i === -1 ? { key: line, value: null } : { key: line.slice(0, i), value: line.slice(i + 2) }
    })
}
