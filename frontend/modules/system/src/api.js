import request from '@zimo/shared/request'

/* ==========================================================================
 * 日志管控（v0.2 第 14 项，后端 jv-api-gateway-config /policies 接口）
 * ========================================================================== */

/** 动态日志级别：{ logger: LEVEL } */
export const fetchLogLevels = () => request.get('/policies/log-levels')

/** 全量覆盖动态级别，各服务 10 秒内应用（免重启） */
export const saveLogLevels = (levels) => request.put('/policies/log-levels', levels)

/** 日志文件概览：按模块聚合大小 / 文件数 / ERROR 文件 + 滚动保留策略 */
export const fetchLogFiles = () => request.get('/policies/log-files')

/* ==========================================================================
 * 系统配置（后端 jv-api-gateway-config /configs 接口，经网关 28090）
 * ========================================================================== */

/** 全量配置（需登录） */
export const fetchConfigs = () => request.get('/configs')

/** 新增配置：可指定落 DB / Redis / Nacos，并标注平台/应用/服务 */
export const createConfig = (data) => request.post('/configs', data)

/** 删除配置（同时清理对应存储） */
export const deleteConfig = (key) => request.delete(`/configs/${key}`)

/** 修改单个配置值，30 秒内生效 */
export const updateConfig = (key, value) => request.put(`/configs/${key}`, { value })

/** 公开开关（免登录） */
export const fetchPublicFlags = () => request.get('/configs/public')

/* ==========================================================================
 * 变更审计（v0.2 第 8 项）：配置 / 策略 / 路由的增删改留痕
 * ========================================================================== */

/** 变更记录（bizType: CONFIG / POLICY / ROUTE；bizKey 为空表示不限） */
export function fetchChangeLogs(params) {
  return request.get('/change-logs', { params })
}

/** 网关健康快照：各后端服务状态 + 鉴权缓存实际生效模式 */
export const fetchMenuHealth = () => fetch('/health/menu-services').then((r) => (r.ok ? r.json() : null))
