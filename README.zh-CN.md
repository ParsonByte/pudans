<h1 align="center">Pudans</h1>

<p align="center">
  <strong>每一个身份，每一个模型，一个平台。</strong>
</p>

<p align="center">
  一边是用户、角色、工作区与权限，另一边是供应商、路由、网关与对话。<br>
  Pudans 让管理员在同一个控制台里管理两者。
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
  <a href="#ai-控制台">AI 控制台</a> ·
  <a href="#agent-运行时">Agent 运行时</a> ·
  <a href="CONTRIBUTING.md">参与贡献</a> ·
  <a href="#贡献者">贡献者</a> ·
  <a href="LICENSE">许可证</a>
</p>

## Pudans 是什么

Pudans 是一个企业身份与权限管理平台，内置 AI Console 与 AI 网关。

它将响应式 Spring Boot 后端、以扩展资源（Extension Resource）描述的用户 / 角色 / 工作区 / 菜单 / AI 资源模型，以及统一的身份、权限与 AI 流量 HTTP 接口结合在一起。

## 为什么选择 Pudans

- **不只是登录。** 用户、用户组、角色、权限、工作区、菜单、审计记录与风险规则共享同一套扩展资源模型。
- **统一管理 AI。** 注册供应商与账号、发现模型、在多个账号之间路由，并实时观察请求，无需额外组件。
- **真实对话。** 支持思考过程、Markdown 与多模态附件的流式对话，记忆与检索全部基于真实 API。
- **Agent 原生运行时。** LangChain4j Harness 统一承载对话、工具、子代理与协议，支持流式输出、权限、计划模式、上下文压缩、持久状态与线程日志。
- **开放的 Agent 协议。** 在 OpenAI / Anthropic 兼容网关之外，同时提供 AG-UI、A2A 与 Agent Protocol。
- **灵活的检索方式。** 本地 pgvector 混合检索支持 PDF、Word、Tika 与图片解析，也可通过外部连接器接入 Dify、RAGFlow、Haystack 与百炼知识库。
- **响应式优先。** WebFlux、R2DBC 与 Redis 保证请求链路非阻塞。

## AI 控制台

| 页面 | 功能 |
| --- | --- |
| **供应商** | 管理供应商与账号、协议预设、品牌图标、额度，以及实时模型发现。 |
| **模型广场** | 按能力、作者、输入 / 输出模态、上下文窗口与价格浏览和筛选已配置的模型。 |
| **路由** | 实时用户 → PUDANS → 供应商拓扑，带流动边、请求历史与路由决策解释。 |
| **路由组** | 配置模型成员、权重、档位与路由方式，并映射到快速 / 进阶对话模式。 |
| **网关** | OpenAI / Anthropic 兼容端点、PAT 访问、模型列表、最近调用与显示限制。 |
| **知识库** | 本地与外部知识库、文档入库、检索参数与引用展示。 |

## Agent 运行时

Pudans 的 AI 能力运行在 LangChain4j Agent Harness 上，而不是独立的对话循环。

- **Harness 循环。** `ReActAgent` 与 `HarnessAgent` 基于 LangChain4j 的 ChatModel / StreamingChatModel 执行流式对话、工具调用、计划与待办、上下文压缩、工具结果卸载与结构化输出。
- **工具与代理。** `@Tool` 反射、工具组与检索、异步工具、子代理、Team、技能、Workspace 与沙箱抽象统一注册到 Toolkit。
- **人工控制。** 权限规则与 ask / deny / bypass 模式支持暂停确认（HITL）、取消与可恢复运行。
- **持久上下文。** Agent 状态以 PostgreSQL JSONB 保存并使用乐观并发控制；线程日志记录运行、模型、工具与检查点事件，用于恢复与审计。

### 协议端点

所有路由位于 `/apis/api.pudans.tolim.cn/v1alpha1` 下。

| 协议 | 端点 | 用途 |
| --- | --- | --- |
| AG-UI | `POST /aiagui/-/run` | 面向客户端的 SSE 事件流 |
| A2A | `GET /aia2a/.well-known/agent.json`、`POST /aia2a` | Agent Card 与 JSON-RPC 任务交互 |
| Agent Protocol | `/aiagentprotocol/-/runs`、`/aiagentprotocol/-/threads/{threadId}/state` | 运行生命周期与线程状态 |
| OpenAI / Anthropic / Gemini | `/aigateway/-/...` | 模型兼容网关端点 |

## 知识与检索

- 本地知识库使用 pgvector 混合检索（关键词与向量检索、RRF 与可选重排），支持 PDF、Word、Tika 与图片解析。
- 外部知识库可通过 `KnowledgeBase.spec.external` 接入 Dify、RAGFlow、Haystack 或百炼；凭据通过 `AiProviderAccount` 引用保存。
- 开启 `AiSetting.spec.retrieval.agenticEnabled` 后，Agent 可通过 `retrieve_knowledge` 工具按需检索；否则在对话中直接获得预检索上下文。

## 架构

```text
浏览器 / Agent
       │
       ▼
┌──────────────────────────────────────────┐
│ Pudans appkernel (Spring Boot / WebFlux) │
│ 身份 · RBAC · 工作区 · 审计              │
│ 扩展资源 · LangChain4j Harness           │
│ 供应商 → 路由 → 网关 → Agent             │
│ AG-UI · A2A · Agent Protocol · RAG       │
└──────────────────────────────────────────┘
       │                          │
       ▼                          ▼
   PostgreSQL                    Redis
```

后端负责身份、权限决策、扩展资源协调、AI 路由、Agent Harness、协议与对话，并通过 HTTP 对外提供兼容接口。

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `appkernel/` | 后端核心：身份、权限、扩展机制、AI 内核与 HTTP 接口 |
| `appkernel/src/main/java/cn/tolim/pudans/core/ai/harness/` | LangChain4j Agent Harness：工具、权限、线程日志、技能与子代理 |
| `appkernel/src/main/java/cn/tolim/pudans/core/ai/protocol/` | AG-UI、A2A 与 Agent Protocol 端点及共享协议运行时 |
| `appkernel/src/main/java/cn/tolim/pudans/core/ai/rag/` | 混合检索、文档解析与外部 RAG 连接器 |
| `appkernel/src/main/resources/extensions/` | 角色、权限、工作区、菜单与 AI 资源的 YAML 配置源 |
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
| `PUDANS_AI_ENABLED` | `true` | 是否启用 AI 内核 |
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
