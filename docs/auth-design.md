# AI 网关平台 · 登录鉴权方案

## 一、方案选型结论

**采用 JWT（无状态 Bearer Token）**，配合 `Authorization: Bearer <token>` 请求头传递，
服务端通过拦截器统一校验，前端由路由守卫统一拦截未登录访问。

## 二、为什么不是 Session，也不是不透明 Token

| 方案 | 在网关平台场景下的表现 | 结论 |
| --- | --- | --- |
| **Cookie + Session（Tomcat HttpSession）** | 控制台一次登录后要访问 4 个后端微服务（28081~28084）。Session 存在单个 JVM 内存里，跨服务必须再做一层集中式 Session（Spring Session + Redis）或 session 复制；同时网关控制台常有 curl / SDK / 定时任务的调用诉求，Cookie 模型不友好 | 不采用 |
| **不透明 Token（随机串 + Redis 校验）** | 可控性最好（随时吊销），但每次请求都要查一次 Redis，网关这种高频控制台多活部署下多一次网络往返；且需要引入 Redis 强依赖 | 作为演进方案 |
| **JWT（无状态签名令牌）** | 服务端无状态，任一服务本地验签即可，天然适配多微服务与水平扩容；与网关对外给上游系统的鉴权模型（API Key / JWT / OAuth2）一致；不依赖任何中间件 | **采用** |

补充：JWT 的最大短板是「签发后无法主动失效」，
因此本方案额外引入 **令牌版本（token_version）** 机制来兜底（见下）。

## 三、令牌设计

### 载荷（Claims）

| Claim | 说明 |
| --- | --- |
| `sub` | 登录账号 |
| `userId` | 用户主键 |
| `tv` | 令牌版本，取自 `tb_sys_user.token_version` |
| `iat` / `exp` | 签发时间 / 过期时间 |

### 有效期与存储

- 有效期：默认 8 小时（`gateway.jwt.expire-hours`，可按环境覆盖）
- 密钥：默认内置，生产环境通过环境变量 `GATEWAY_JWT_SECRET` 注入，长度 ≥ 32 字节
- 前端存储：`localStorage`（勾选「记住我」）/ `sessionStorage`（不记住，关闭标签页即失效）
- 传递方式：`Authorization: Bearer <token>`，不使用 Cookie，规避 CSRF

### 失效策略

```
登录   → 取 tb_sys_user.token_version 写入 claim tv
校验   → 签名 & 过期 → 账号存在/未禁用 → claim.tv == 库中 token_version
退出   → tb_sys_user.token_version + 1 → 该账号此前签发的所有令牌立即失效
改密/禁用 → 同样自增 token_version，即可强制下线
```

单实例下登录失败计数用内存 `ConcurrentHashMap`；
多实例部署时替换为 Redis 的 `INCR + EXPIRE` 即可跨实例生效。

## 四、鉴权时序

```
浏览器                         jv-gateway-user (28081)
  │  POST /api/auth/login  ───────►  校验账号 + BCrypt 比对密码
  │  ◄─────  { code:200, data:{ token, id, username, nickname, role } }
  │  Pinia 持久化 token（local/session）
  │
  │  GET /api/users  Authorization: Bearer <token>
  │  ─────►  AuthInterceptor
  │            ├─ 验签 & 过期
  │            ├─ 查账号，校验是否被禁用
  │            └─ 比对 token_version
  │  ◄─────  200 数据 / 401 登录失效
  │
  │  POST /api/auth/logout（需携带有效令牌）
  │  ─────►  token_version + 1
  │  前端清理会话 → 跳转 /login
```

## 五、代码落点

### 后端

| 文件 | 职责 |
| --- | --- |
| `jv-gateway-common/.../util/JwtUtil.java` | 签发（含 `tv` 声明）、解析、取版本号 |
| `jv-gateway-user/.../interceptor/AuthInterceptor.java` | 签名/过期 → 账号状态 → 令牌版本 三段校验 |
| `jv-gateway-user/.../config/WebMvcConfig.java` | CORS + 拦截器白名单（仅放行 `/auth/login`） |
| `jv-gateway-user/.../controller/AuthController.java` | `/auth/login`、`/auth/logout`、`/auth/info` |
| `jv-gateway-user/.../service/impl/UserServiceImpl.java` | 密码校验、失败锁定、退出自增版本 |
| `jv-gateway-user/.../config/PasswordEncoderConfig`（common） | BCrypt 加密器 |
| `jv-gateway-user/src/main/resources/application.yml` | `gateway.jwt.*` 配置 |

### 前端

| 文件 | 职责 |
| --- | --- |
| `packages/shared/src/request.js` | 请求注入 token；401/403 统一清会话并回调 |
| `packages/shared/src/store/user.js` | 会话持久化（local/session）、`expired` 过期判定 |
| `packages/shared/src/api/auth.js` | 登录 / 退出 / 当前用户接口 |
| `apps/portal/src/router/index.js` | 注册失效回调、路由守卫拦截未登录 |
| `apps/portal/src/views/login/index.vue` | 登录页（记住我、回车登录、加载态） |

## 六、安全加固清单

已实现：

- 密码 BCrypt 加盐存储，明文不落库、不出接口（`password` 统一置空）
- 统一 401 返回，前端清理会话并跳转登录页（携带 `redirect` 自动回跳）
- 路由守卫拦截未登录 / 令牌过期访问
- 连续输错 5 次锁定账号，防止暴力破解
- 登录 IP、最后登录时间记录
- 账号禁用后令牌立即失效

生产环境建议再补：

1. **Redis 令牌黑名单 / token_version 缓存**：消除每次请求查库的开销，并支持单点登出
2. **Refresh Token**：长会话场景下用短时效 access token + 刷新令牌，降低令牌泄露风险
3. **全站 HTTPS** + ` Secure` 传输，避免令牌在明文链路中被截获
4. **密钥集中管理**：接入 Nacos / KMS 托管 `GATEWAY_JWT_SECRET`，禁止硬编码
5. **图形验证码**：登录失败 N 次后强制验证码，配合锁定策略
6. **操作审计**：登录/退出/关键配置变更落审计表
