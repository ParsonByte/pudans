<h1 align="center">Pudans</h1>

<p align="center">
  <strong>Every identity. Every model. One platform.</strong>
</p>

<p align="center">
  Users, roles, workspaces and permissions on one side. Providers, routing, gateways and conversations on the other.<br>
  Pudans gives operators one console for both.
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue.svg"></a>
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-orange.svg">
  <img alt="Spring Boot 4.2" src="https://img.shields.io/badge/Spring_Boot-4.2-6DB33F.svg">
  <img alt="PostgreSQL R2DBC" src="https://img.shields.io/badge/PostgreSQL-R2DBC-4169E1.svg">
</p>

<p align="center">
  <a href="README.md"><strong>English</strong></a> · <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <a href="#quick-start">Quick start</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#ai-console">AI Console</a> ·
  <a href="#agent-runtime">Agent runtime</a> ·
  <a href="CONTRIBUTING.md">Contributing</a> ·
  <a href="#contributors">Contributors</a> ·
  <a href="LICENSE">License</a>
</p>

## What is Pudans?

Pudans is an enterprise identity and permission management platform with a built-in AI Console and AI gateway.

It combines a reactive Spring Boot backend, an extension-resource model for users, roles, workspaces, menus and AI resources, and HTTP APIs for operating identity, permissions and AI traffic from one place.

## Why Pudans

- **Identity, not just login.** Users, groups, roles, permissions, workspaces, menus, audit records and risk rules share one extension-resource model.
- **AI operations in the same console.** Register providers and accounts, discover models, route traffic across them and watch live requests without leaving the admin surface.
- **Real conversations.** Streaming chat with reasoning, markdown and multimodal attachments, plus memory and retrieval backed by live APIs.
- **Agent-native runtime.** The LangChain4j harness powers chat, tools, subagents and protocols with streaming, permissions, plan mode, compaction, durable state and a thread journal.
- **Open agent protocols.** Expose agents over AG-UI, A2A and Agent Protocol alongside the OpenAI/Anthropic-compatible model gateway.
- **Retrieval your way.** Local pgvector hybrid retrieval with PDF, Word, Tika and image parsers, or Dify, RAGFlow, Haystack and Bailian knowledge bases through external connectors.
- **Reactive from day one.** WebFlux, R2DBC and Redis keep the request path non-blocking.

## AI Console

| Page | What it does |
| --- | --- |
| **Providers** | Provider and account management, protocol presets, brand icons, quotas and live model discovery. |
| **Model market** | Browse and filter configured models by capability, author, input/output modality, context window and pricing. |
| **Routing** | Live user → PUDANS → provider topology with animated flow edges, request history and route decisions. |
| **Route groups** | Model members, weights, tiers and routing modes, mapped to the fast/pro chat modes. |
| **Gateway** | OpenAI/Anthropic-compatible endpoints, PAT-based access, model list, recent calls and display limits. |
| **Knowledge base** | Local and external knowledge bases, document ingestion, retrieval settings and citations. |

## Agent runtime

Pudans runs its AI features on a LangChain4j agent harness instead of a separate chat loop.

- **Harness loop.** `ReActAgent` and `HarnessAgent` run streaming chat, tool calls, plan and todo state, context compaction, tool-result eviction and structured output on LangChain4j chat and streaming models.
- **Tools and agents.** `@Tool` reflection, tool groups and search, async tools, subagents, teams, skills, workspace and sandbox abstractions register into one toolkit.
- **Human control.** Permission rules and ask/deny/bypass modes provide suspend-and-confirm HITL, cancellation and resumable runs.
- **Durable context.** Agent state is stored in PostgreSQL JSONB with optimistic concurrency; thread journals record run, model, tool and checkpoint events for recovery and audit.

### Protocol endpoints

All routes live under `/apis/api.pudans.tolim.cn/v1alpha1`.

| Protocol | Endpoint | Purpose |
| --- | --- | --- |
| AG-UI | `POST /aiagui/-/run` | SSE event stream for clients |
| A2A | `GET /aia2a/.well-known/agent.json`, `POST /aia2a` | Agent card and JSON-RPC task exchange |
| Agent Protocol | `/aiagentprotocol/-/runs`, `/aiagentprotocol/-/threads/{threadId}/state` | Run lifecycle and thread state |
| OpenAI / Anthropic / Gemini | `/aigateway/-/...` | Model-compatible gateway endpoints |

## Knowledge and retrieval

- Local knowledge bases use pgvector hybrid retrieval (keyword and vector search, RRF and optional rerank) with PDF, Word, Tika and image parsers.
- External knowledge bases can point at Dify, RAGFlow, Haystack or Bailian through `KnowledgeBase.spec.external`; credentials stay in `AiProviderAccount` references.
- Agents can retrieve on demand through the `retrieve_knowledge` tool when `AiSetting.spec.retrieval.agenticEnabled` is on, or receive pre-retrieved context in chat.

## Architecture

```text
Browser / Agents
       │
       ▼
┌──────────────────────────────────────────┐
│ Pudans appkernel (Spring Boot / WebFlux) │
│ identity · RBAC · workspaces · audit     │
│ extension resources · LangChain4j harness│
│ providers → routing → gateway → agents   │
│ AG-UI · A2A · Agent Protocol · RAG       │
└──────────────────────────────────────────┘
       │                          │
       ▼                          ▼
   PostgreSQL                    Redis
```

The backend owns identity, permission decisions, extension reconciliation, AI routing, the agent harness, protocols and conversations, and exposes them over HTTP.

## Repository layout

| Path | Purpose |
| --- | --- |
| `appkernel/` | Backend core: identity, permissions, extension mechanism, AI kernel and HTTP endpoints |
| `appkernel/src/main/java/cn/tolim/pudans/core/ai/harness/` | LangChain4j agent harness: tools, permissions, journal, skills and subagents |
| `appkernel/src/main/java/cn/tolim/pudans/core/ai/protocol/` | AG-UI, A2A and Agent Protocol endpoints on the shared protocol runtime |
| `appkernel/src/main/java/cn/tolim/pudans/core/ai/rag/` | Hybrid retrieval, document parsers and external RAG connectors |
| `appkernel/src/main/resources/extensions/` | YAML source of truth for roles, permissions, workspaces, menus and AI resources |
| `api-docs/` | OpenAPI aggregation and validation |
| `platform/appkernel/` | Backend dependency constraints (BOM) |
| `gradle/libs.versions.toml` | Backend dependency version catalog |

## Quick start

### Requirements

- JDK 25
- PostgreSQL and Redis
- Docker, optional but handy for local services

The Gradle build packages the backend jar.

### 1. Start PostgreSQL and Redis

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

The defaults match `appkernel/src/main/resources/application.yaml`. Override them with the environment variables below when your services use different endpoints.

### 2. Run Pudans

```bash
./gradlew :appkernel:bootRun
```

The backend starts on <http://localhost:8090>, creates or updates the PostgreSQL schema on startup and reconciles the extension resources.

### 3. Container deployment (optional)

```bash
docker compose up --build
```

The `api` service exposes the backend on port `8090`.

## Configuration

The backend reads standard Spring Boot configuration and the following environment variables:

<details>
<summary>Environment variables</summary>

| Variable | Default | Purpose |
| --- | --- | --- |
| `PUDANS_DB_URL` | `r2dbc:postgresql://localhost:5432/pudans` | PostgreSQL R2DBC URL |
| `PUDANS_DB_USERNAME` | `pudans` | Database user |
| `PUDANS_DB_PASSWORD` | `pudans` | Database password |
| `PUDANS_REDIS_HOST` | `localhost` | Redis host |
| `PUDANS_REDIS_PORT` | `6379` | Redis port |
| `PUDANS_REDIS_PASSWORD` | empty | Redis password |
| `PUDANS_REDIS_DATABASE` | `0` | Redis database |
| `PUDANS_WORK_DIR` | `~/.pudans` | Logs and runtime work directory |
| `PUDANS_AI_ENABLED` | `true` | Enable the AI kernel |
| `FEISHU_ENABLED` | `false` | Enable Feishu login and profile sync |
| `FEISHU_APP_ID` / `FEISHU_APP_SECRET` | empty | Feishu OAuth credentials |
| `FEISHU_REDIRECT_URI` | `{baseUrl}/apis/api.pudans.tolim.cn/v1alpha1/oauth2/callback/feishu` | Feishu callback URL |
| `WEBAUTHN_RP_ID` | `localhost` | WebAuthn relying party ID |
| `WEBAUTHN_ORIGINS` | `http://localhost:8090` | Allowed WebAuthn origins |

</details>

Use `--spring.profiles.active=dev` for the local development profile (`application-dev.yaml`), which enables basic authentication and debug logging.

Secrets belong in environment variables or local configuration; never commit them.

## Build and test

```bash
./gradlew test                    # backend tests
./gradlew build                   # backend build
./gradlew :api-docs:validateOpenApiDocs
```

`make verify` runs the backend compile/test/OpenAPI gate plus `git diff --check`.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. The authoritative engineering rules live in [AGENTS.md](AGENTS.md) and [`.agents/rules`](.agents/rules); the agent skills are under [`.agents/skills`](.agents/skills).

## Contributors

Thanks to everyone who has contributed to Pudans.

<p align="center">
  <a href="https://github.com/ParsonByte/pudans/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=ParsonByte/pudans" alt="Pudans contributors">
  </a>
</p>

Maintained by [@ParsonByte](https://github.com/ParsonByte). See the full [contributor graph](https://github.com/ParsonByte/pudans/graphs/contributors).

## Security

Do not report security vulnerabilities in public issues. Contact the repository owner privately with reproduction steps and affected versions. Never commit credentials, tokens, private keys or production data.

## License

Copyright (C) 2026 ParsonByte.

Pudans is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

Pudans is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU General Public License](LICENSE) for more details.
