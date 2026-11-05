# 服务拆解与按需启动方案

> 适用场景：平台以微服务拆分交付，客户按套餐选购部分模块，交付时按清单组装前后端并启动。
> 结论先行：后端最小可售组合 = `jv-api-gateway-user` + `jv-api-gateway-proxy` + MySQL；
> 前端运行时永远只有一个进程（portal），按客户购买在构建期裁剪菜单模块。

## 一、后端模块拆解

各模块为独立进程，仅共享 MySQL，无进程间强依赖（proxy 的转发只依赖 MySQL 路由表 `tb_gw_route`，鉴权回调 user 的验签接口）。

| 模块 | 端口 | 职责 | 被谁依赖 | 裁剪影响 |
| --- | --- | --- | --- | --- |
| jv-api-gateway-user | 28081 | 登录/JWT、API Key（gwak-）、分组权限、接入中心 | **proxy 鉴权回调它的 `/open/auth/token/verify`** | 不可裁剪（登录与鉴权失效） |
| jv-api-gateway-proxy | 28090 | 请求转发、入口鉴权、转发链路采集、上报通道共享密钥校验 | 所有流量入口 + SDK 上报入口 | 不可裁剪（无网关即无平台） |
| jv-api-gateway-discovery | 28083 | 服务注册/心跳/健康巡检（服务发现页、菜单状态点） | SDK 注册心跳 | 可裁剪：SDK 注册失败自动降级，不影响业务 |
| jv-api-gateway-trace | 28084 | 调用日志/span 落库与查询（日志追踪页） | 采集数据 | 可裁剪：采集上报失败仅告警 |
| jv-api-gateway-overview | 28082 | 总览/统计聚合查询（只读 MySQL） | — | 可裁剪：纯展示，无别人依赖它 |
| jv-api-gateway-config | 28085 | 系统设置/平台配置（刷新间隔、鉴权缓存模式、豁免控制位等） | 系统设置页 | 可裁剪：建议保留（很轻，全局开关都在这） |

## 二、启动清单裁剪方法

启动清单集中在 `scripts/_common.sh` 的 `BACKEND_SERVICES` 数组（顺序即启动顺序），删除未购买模块的行即可：

```bash
# scripts/_common.sh（示例：基础套餐只启动 user + proxy + config）
BACKEND_SERVICES=(
  "jv-api-gateway-user|28081"
  "jv-api-gateway-config|28085"
  "jv-api-gateway-proxy|28090"
)
```

配套行为（无需额外改动）：

- `start.sh` 只构建/启动清单内模块（默认先 `mvn clean package` 全量清理打包，`--skip-build` 跳过）；
- `stop.sh` / `status.sh` / `restart.sh` 共用同一清单，自动只管理清单内服务；
- SDK 对未购买模块的注册/上报会失败但**不影响业务运行**（自动降级，下个周期重试）。

## 三、前端模块裁剪

前端是构建期组装的单进程应用：portal（vite，28080）静态引入 8 个业务模块，菜单与路由由模块数组生成。裁剪 = 从数组中删除未购买的模块：

```js
// frontend/apps/portal/src/router/index.js
export const modules = [overview, stats, discovery, trace, auth, onboard, perm, system]
//  ↑ 删除未购买模块的 import 与数组元素，侧边栏/路由/页面整体消失
```

模块 → 后端服务映射（`MODULE_ROUTES`，同文件）：

| 前端模块 | 路由 | 后端微服务 |
| --- | --- | --- |
| overview / stats | /overview、/stats | jv-api-gateway-overview |
| discovery | /discovery | jv-api-gateway-discovery |
| trace | /trace | jv-api-gateway-trace |
| auth / onboard / perm | /auth/**、/system-onboard、/route-config、/system/role | jv-api-gateway-user |
| system | /system/settings | jv-api-gateway-config |

裁剪模块时，`MODULE_ROUTES` 中对应的映射一并删除（菜单状态点依赖它探测后端服务）。

> 可选增强（当前未实现）：把 modules 数组改为读环境变量（如 `VITE_MODULES=overview,trace`）
> 过滤，交付脚本按客户套餐生成环境文件，实现免改代码组装。需要时排期。

## 四、按套餐组装示例

| 套餐 | 后端 BACKEND_SERVICES | 前端 modules | 客户拿到的功能 |
| --- | --- | --- | --- |
| 基础（网关+认证） | user、proxy、config | auth、perm、onboard、system | 网关转发、JWT/API Key 鉴权、分组权限、系统设置 |
| + 可观测 | + discovery、trace、overview | + overview、stats、discovery、trace | 服务发现、调用日志追踪、总览统计、SDK 自动接入三件套 |
| 全量 | 全部 6 个 | 不裁剪 | 上述全部 + 完整控制台 |

## 五、多 Pod / Docker 部署注意事项

1. **traceId / spanId 无需担心重复**：均为纯随机 UUID（traceId 128 位、spanId 64 位），
   不含机器标识、不依赖中心发号器，碰撞概率 2^-122 量级，与部署实例数无关；
2. **需要关注服务注册**：注册表按 `serviceName` upsert，同服务多实例（不同 ip:port）时
   心跳整体刷新，多实例的可视化/权重/健康剔除见 PLAN 的 P1「服务发现控制台」；
   多 pod 客户交付前建议排期该能力；
3. 日志统一切分目录 `logs/app/`（文件名 = 日期--小时--ai-gateway--模块名--类型--等级.txt）
   是**每台机器本地目录**，多容器部署需挂载宿主卷或接入集中采集。

## 六、交付检查清单

- [ ] `_common.sh` 的 `BACKEND_SERVICES` 按套餐裁剪
- [ ] 前端 `modules` 数组与 `MODULE_ROUTES` 按套餐裁剪，`npm run build` 无报错
- [ ] 未购买模块相关菜单不可见（登录控制台逐页确认）
- [ ] `bash scripts/start.sh` 全量构建 + 清单内服务全部「健康」
- [ ] SDK 接入冒烟：`python3 sdk/python/scripts/smoke_report.py` /
      `node sdk/nodejs/scripts/smoke_report.js`（购买可观测套餐时）
- [ ] `platform.auth.exemption.control`、共享密钥 `GATEWAY_CLIENT_TOKEN` 按客户环境注入
