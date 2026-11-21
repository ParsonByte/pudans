<h1 align="center">Pudans</h1>

<p align="center">
  <strong>每一个身份，一个平台。</strong>
</p>

<p align="center">
  一边是用户、用户组、角色、工作区与权限，另一边是审计记录与风险规则。<br>
  Pudans 让管理员在同一个控制台里管理全部能力。
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue.svg"></a>
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-orange.svg">
  <img alt="Spring Boot 4.2" src="https://img.shields.io/badge/Spring_Boot-4.2-6DB33F.svg">
  <img alt="PostgreSQL R2DBC" src="https://img.shields.io/badge/PostgreSQL-R2DBC-4169E1.svg">
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md"><strong>简体中文</strong></a>
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> ·
  <a href="#架构">架构</a> ·
  <a href="CONTRIBUTING.md">参与贡献</a> ·
  <a href="#贡献者">贡献者</a> ·
  <a href="LICENSE">许可证</a>
</p>

## Pudans 是什么

Pudans 是一个企业身份与权限管理平台。

它将响应式 Spring Boot 后端、以扩展资源（Extension Resource）描述的用户 / 角色 / 工作区 / 菜单 / 权限模型，以及统一的身份与权限 HTTP 接口结合在一起。

## 为什么选择 Pudans

- **不只是登录。** 用户、用户组、角色、权限、工作区、菜单、审计记录与风险规则共享同一套扩展资源模型。
- **响应式优先。** WebFlux、R2DBC 与 Redis 保证请求链路非阻塞。

## 架构

```text
浏览器
   │
   ▼
┌──────────────────────────────────────────┐
│ Pudans appkernel (Spring Boot / WebFlux) │
│ 身份 · RBAC · 工作区 · 审计              │
│ 扩展资源 · 风险规则                      │
└──────────────────────────────────────────┘
       │                          │
       ▼                          ▼
   PostgreSQL                    Redis
```

后端负责身份、权限决策、扩展资源协调、审计与风险规则，并通过 HTTP 对外提供接口。

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `appkernel/` | 后端核心：身份、权限、扩展机制与 HTTP 接口 |
| `appkernel/src/main/resources/extensions/` | 角色、权限、工作区、菜单的 YAML 配置源 |
| `api-docs/` | OpenAPI 聚合与校验 |
| `platform/appkernel/` | 后端依赖约束（BOM） |
| `gradle/libs.versions.toml` | 后端依赖版本目录 |

## 快速开始

### 环境要求

- JDK 25
- 可访问的 PostgreSQL 与 Redis
- Docker（可选，便于启动依赖服务）

Gradle 构建会打包后端 jar。

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

### 2. 启动 Pudans

```bash
./gradlew :appkernel:bootRun
```

后端在 <http://localhost:8090> 启动，创建或更新 PostgreSQL 表结构，并协调扩展资源。

### 3. 容器部署（可选）

```bash
docker compose up --build
```

`api` 服务在 `8090` 端口暴露后端。

## 配置

后端使用标准 Spring Boot 配置，并支持以下环境变量：

<details>
<summary>环境变量</summary>

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
| `FEISHU_ENABLED` | `false` | 是否启用飞书登录与资料同步 |
| `FEISHU_APP_ID` / `FEISHU_APP_SECRET` | 空 | 飞书 OAuth 凭据 |
| `FEISHU_REDIRECT_URI` | `{baseUrl}/apis/api.pudans.tolim.cn/v1alpha1/oauth2/callback/feishu` | 飞书回调地址 |
| `WEBAUTHN_RP_ID` | `localhost` | WebAuthn 依赖方 ID |
| `WEBAUTHN_ORIGINS` | `http://localhost:8090` | 允许的 WebAuthn 来源 |

</details>

使用 `--spring.profiles.active=dev` 可启用本地开发配置（`application-dev.yaml`），其中会开启 Basic Auth 与调试日志。

敏感信息应放在环境变量或本地配置中，禁止提交到仓库。

## 构建与测试

```bash
./gradlew test                    # 后端测试
./gradlew build                   # 后端构建
./gradlew :api-docs:validateOpenApiDocs
```

`make verify` 会执行后端编译 / 测试 / OpenAPI 校验，以及 `git diff --check`。

## 参与贡献

提交 Pull Request 前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。权威工程规范位于 [AGENTS.md](AGENTS.md) 与 [`.agents/rules`](.agents/rules)，Agent 技能位于 [`.agents/skills`](.agents/skills)。

## 贡献者

感谢每一位参与 Pudans 开发的人。

<p align="center">
  <a href="https://github.com/ParsonByte/pudans/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=ParsonByte/pudans" alt="Pudans 贡献者">
  </a>
</p>

由 [@ParsonByte](https://github.com/ParsonByte) 维护。查看完整的[贡献者图谱](https://github.com/ParsonByte/pudans/graphs/contributors)。

## 安全

请不要在公开 Issue 中报告安全漏洞。请私下联系仓库所有者，并提供复现步骤与受影响版本。禁止提交凭据、令牌、私钥或生产数据。

## 许可证

Copyright (C) 2026 ParsonByte。

Pudans 是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证第 3 版（或你选择的任何更新版本）的条款重新分发和 / 或修改它。

Pudans 分发时希望它有用，但不提供任何担保，甚至不提供适销性或特定用途适用性的默示担保。详情见 [LICENSE](LICENSE) 中的 GNU 通用公共许可证原文；如有歧义，以英文原文为准。
