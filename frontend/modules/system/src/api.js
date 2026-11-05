import request from '@zimo/shared/request'

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

/** 网关健康快照：各后端服务状态 + 鉴权缓存实际生效模式 */
export const fetchMenuHealth = () => fetch('/health/menu-services').then((r) => (r.ok ? r.json() : null))
