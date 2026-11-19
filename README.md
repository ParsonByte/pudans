# Pudans

Pudans 是一个企业身份与权限管理平台，内置 AI Console 与 AI 网关。

它将响应式 Spring Boot 后端、以扩展资源（Extension Resource）描述的用户 / 角色 / 工作区 / 菜单 / AI 资源模型，以及用于统一操作身份、权限与 AI 流量的 React 控制台结合在一起。

## 核心能力

- **身份与权限**：用户、用户组、角色、权限、工作区、菜单、审计记录与风险规则。
- **扩展资源**：角色、权限、工作区、菜单、AI 供应商、账号、模型、路由组与设置均以 YAML 资源声明，并由平台协调（reconcile）。
- **AI Console**：供应商与账号管理、实时模型发现、模型广场、实时路由拓扑、面向对话模式的路由组，以及 OpenAI / Anthropic 兼容网关。
- **对话工作区**：流式对话、思考过程、Markdown 渲染与多模态附件，全部基于真实 API。
- **响应式优先**：WebFlux、Spring Security Reactive、Spring Data R2DBC、Redis 会话与缓存、虚拟线程。

## 技术栈

| 领域 | 技术 |
| --- | --- |
| 后端 | Java 25、Spring Boot 4.2、WebFlux、Spring Security Reactive、Spring Data R2DBC |
| 数据 | PostgreSQL、Redis |
| 构建 | Gradle 多模块、版本目录（version catalog） |
| 前端 | React 19、TypeScript 5.9、Vite 8、TanStack Router / Query、Tailwind CSS v4、shadcn/ui |
| 接口文档 | OpenAPI，由 `api-docs` 模块校验 |

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `appkernel/` | 后端核心：身份、权限、扩展机制、AI 内核与 HTTP 接口 |
| `appkernel/src/main/resources/extensions/` | 角色、权限、工作区、菜单与 AI 资源的 YAML 配置源 |
| `api-docs/` | OpenAPI 聚合与校验 |
| `platform/appkernel/` | 后端依赖约束（BOM） |
| `gradle/libs.versions.toml` | 后端依赖版本目录 |
| `web/` | React 控制台，构建后打包进后端 jar |

## 环境要求

- JDK 25
- Node.js 20.19 或更高版本，以及 npm
- 可访问的 PostgreSQL 与 Redis
- Docker（可选，便于启动依赖服务）

Gradle 构建会安装前端依赖、构建 `web/`，再打包后端 jar。

## 快速开始

### 1. 启动 PostgreSQL 与 Redis

```bash
docker run -d --name pudans-pgsql \
  -e POSTGRES_DB=pudans \
  -e POSTGRES_USER=pudans \
  -e POSTGRES_PASSWORD=pudans \
  -p 5432:5432 postgres:latest
```

```bash
docker run -d --name pudans-redis -p 6379:6379 redis:latest
```

默认配置与 `appkernel/src/main/resources/application.yaml` 一致。如果依赖服务使用其他地址，请通过下表中的环境变量覆盖。

### 2. 启动后端

```bash
./gradlew :appkernel:bootRun
```

访问 http://localhost:8090。后端会提供构建后的 React 页面，在启动时创建或更新 PostgreSQL 表结构，并协调扩展资源。

### 3. 启动前端开发服务器（可选）

```bash
cd web
npm install
npm run dev
```

Vite 会把 `/apis`、`/login` 和 `/logout` 代理到 `http://localhost:8090`，请先启动后端。

## 配置

后端使用标准 Spring Boot 配置，并支持以下环境变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `PUDANS_DB_URL` | `r2dbc:postgresql://localhost:5432/pudans` | PostgreSQL R2DBC 地址 |
| `PUDANS_DB_USERNAME` | `pudans` | 数据库用户 |
| `PUDANS_DB_PASSWORD` | `pudans` | 数据库密码 |
| `PUDANS_REDIS_HOST` | `localhost` | Redis 地址 |
| `PUDANS_REDIS_PORT` | `6379` | Redis 端口 |
| `PUDANS_REDIS_PASSWORD` | 空 | Redis 密码 |
| `PUDANS_REDIS_DATABASE` | `0` | Redis 数据库 |
| `PUDANS_WORK_DIR` | `~/.pudans` | 日志与运行时工作目录 |
| `PUDANS_AI_ENABLED` | `true` | 是否启用 AI 内核 |
| `FEISHU_ENABLED` | `false` | 是否启用飞书登录与资料同步 |
| `FEISHU_APP_ID` / `FEISHU_APP_SECRET` | 空 | 飞书 OAuth 凭据 |
| `FEISHU_REDIRECT_URI` | `{baseUrl}/apis/api.pudans.tolim.cn/v1alpha1/oauth2/callback/feishu` | 飞书回调地址 |
| `WEBAUTHN_RP_ID` | `localhost` | WebAuthn 依赖方 ID |
| `WEBAUTHN_ORIGINS` | `http://localhost:8090` | 允许的 WebAuthn 来源 |

使用 `--spring.profiles.active=dev` 可启用本地开发配置（`application-dev.yaml`），其中会开启 Basic Auth 与调试日志。

敏感信息应放在环境变量或本地配置中，禁止提交到仓库。

## 构建与测试

```bash
./gradlew test                    # 后端测试，同时构建打包的前端
./gradlew build                   # 后端与前端构建
./gradlew :api-docs:validateOpenApiDocs
```

```bash
cd web
npm run lint
npm run format:check
npm run typecheck
npm run build
```

`make verify` 会执行后端编译 / 测试 / OpenAPI 校验，以及 `git diff --check`。

## 参与贡献

提交 Pull Request 前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。权威工程规范位于 [AGENTS.md](AGENTS.md) 与 [`.agents/rules`](.agents/rules)，Agent 技能位于 [`.agents/skills`](.agents/skills)。

## 安全

请不要在公开 Issue 中报告安全漏洞。请私下联系仓库所有者，并提供复现步骤与受影响版本。禁止提交凭据、令牌、私钥或生产数据。

## 许可证

Copyright (C) 2026 ParsonByte。

Pudans 是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证第 3 版（或你选择的任何更新版本）的条款重新分发和 / 或修改它。

Pudans 分发时希望它有用，但不提供任何担保，甚至不提供适销性或特定用途适用性的默示担保。详情见 [LICENSE](LICENSE) 中的 GNU 通用公共许可证原文；如有歧义，以英文原文为准。
