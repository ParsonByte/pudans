# API 访问

## 基址

所有业务 API 使用统一前缀：

```text
http://<gateway-host>:8080/apis/pudans.tolim.cn/v1alpha1/
```

生产环境通过 1Panel OpenResty 或外部负载均衡提供 HTTPS，对外地址只暴露网关。七个服务的应用端口
中只有网关 8080 映射到宿主机；其他服务通过 Nacos 被发现，不直接对外。

| 服务 | API 前缀 |
| --- | --- |
| IAM | `/apis/pudans.tolim.cn/v1alpha1/iam` |
| repository | `/apis/pudans.tolim.cn/v1alpha1/repository` |
| notification | `/apis/pudans.tolim.cn/v1alpha1/notification` |
| mq | `/apis/pudans.tolim.cn/v1alpha1/mq` |
| job | `/apis/pudans.tolim.cn/v1alpha1/job` |
| search | `/apis/pudans.tolim.cn/v1alpha1/search` |

网关还代理前端首页、登录页和静态资源；具体路由由 `pudans.gateway.frontend` 和应用配置决定。

## 鉴权

网关接受两种认证方式：

- 浏览器登录会话：IAM 登录成功后写入 `PUDANS_SID` Cookie，网关在受保护路由上调用 IAM 的
  `session/validate` 校验会话。
- Bearer JWT：`Authorization: Bearer <token>`。网关使用与 IAM 共享的 `PUDANS_JWT_SECRET` 校验
  签名、issuer 和 audience。密码登录接口返回 `sid` 并写入 HttpOnly 的 `PUDANS_SID` Cookie，不返回
  JWT；JWT 由使用同一密钥的签发方（IAM 应用协议或外部集成）产生。

以下 IAM 接口是公开的，不需要登录：

```text
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/login
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/logout
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/mfa/challenge
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/mfa/verify
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/mfa/webauthn/options
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/mfa/webauthn/verify
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/password/forgot
POST /apis/pudans.tolim.cn/v1alpha1/iam/auth/password/reset
POST /apis/pudans.tolim.cn/v1alpha1/iam/session/validate
POST /apis/pudans.tolim.cn/v1alpha1/iam/ticket/issue
POST /apis/pudans.tolim.cn/v1alpha1/iam/ticket/exchange
```

公开的 GET 接口包括登录配置、CSRF、验证码和门户登录配置。具体请求体、字段和响应以
[OpenAPI](openapi.md) 为准。

`X-Pudans-Internal-Token` 只用于服务间调用，由网关或内部客户端注入；外部调用方不应持有或发送
该请求头。

## 请求头

| 请求头 | 说明 |
| --- | --- |
| `Authorization` | `Bearer <JWT>`，可选，优先于会话 Cookie |
| `Cookie` | IAM 登录后的 `PUDANS_SID` 会话 |
| `X-Request-Id` | 可选；网关会复用合法值，否则生成 32 位十六进制 ID 并回写响应 |
| `traceparent` | W3C 追踪上下文；网关会生成或透传到下游 |
| `Content-Type` | JSON 接口使用 `application/json` |

网关响应会带上 `X-Request-Id`。排查问题时同时记录 `X-Request-Id`、`traceId` 和服务名，Jaeger
和日志可以按这些字段关联。

## 响应结构

除 IAM 的部分错误外，业务接口使用统一信封：

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

`code` 为 `OK` 表示成功。业务错误通过对应的错误码和 HTTP 状态返回；不要只看 HTTP 200 判断业务
是否成功。

IAM 的认证错误使用：

```json
{
  "error": "not_found",
  "error_description": "Resource not found",
  "status": 404,
  "timestamp": "2026-11-28T12:00:00"
}
```

网关的限流响应由 Sentinel 生成：

```json
{
  "code": "RATE_LIMITED",
  "message": "请求过于频繁",
  "data": null
}
```

HTTP 状态含义：

| 状态 | 含义 |
| --- | --- |
| 200 | 请求成功 |
| 400 | 参数、请求体或校验失败 |
| 401 | 未登录、会话失效或 JWT 无效 |
| 403 | 已登录但无权限 |
| 404 | 路径不存在；管理端口路径写错也会返回 404 |
| 429 | Sentinel 限流或熔断 |
| 500 | 未处理的服务器错误 |

## 示例

获取登录配置：

```bash
curl -i http://127.0.0.1:8080/apis/pudans.tolim.cn/v1alpha1/iam/auth/login/config
```

带上请求 ID 访问受保护接口：

```bash
curl -i \
  -H "X-Request-Id: 0123456789abcdef0123456789abcdef" \
  -H "Cookie: PUDANS_SID=<session-id>" \
  http://127.0.0.1:8080/apis/pudans.tolim.cn/v1alpha1/iam/me
```

使用 JWT：

```bash
curl -i \
  -H "Authorization: Bearer <jwt>" \
  http://127.0.0.1:8080/apis/pudans.tolim.cn/v1alpha1/iam/me
```

## CORS 与 Cookie

浏览器跨域调用时，网关的 `PUDANS_CORS_ALLOWED_ORIGINS` 必须包含前端 Origin，IAM 的
`PUDANS_HOME_ORIGIN` 必须与前端站点一致。HTTPS 部署把 `PUDANS_COOKIE_SECURE` 设为 `true`；
HTTP 本地调试可以设为 `false`，但生产环境不要关闭。所有应用都在同一父域下（例如
`auth.example.com`、`console.example.com`），把 `PUDANS_COOKIE_DOMAIN` 设为 `.example.com` 后，
各子域共享同一个 `PUDANS_SID` 登录态。

## 健康检查

管理端口的健康检查不属于业务 API：

```text
http://<host>:9080/apis/pudans.tolim.cn/v1alpha1/actuator/health
http://<host>:9081/apis/pudans.tolim.cn/v1alpha1/iam/actuator/health
http://<host>:9091/actuator/health
```

管理端口不经过网关，也不对外暴露。不要把健康检查写成业务 API 路径。