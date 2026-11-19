# Pudans

Pudans is an enterprise identity and permission management platform with a built-in AI Console and AI gateway.

It combines a reactive Spring Boot backend, an extension-resource model for users, roles, workspaces, menus and AI resources, and a React console for operating identity, permissions and AI traffic from one place.

## Highlights

- **Identity and access**: users, groups, roles, permissions, workspaces, menus, audit records and risk rules.
- **Extension resources**: roles, permissions, workspaces, menus, AI providers, accounts, models, route groups and settings are declared as YAML resources and reconciled by the platform.
- **AI Console**: provider and account management, live model discovery, a model market, a live routing topology, route groups for chat modes and an OpenAI/Anthropic-compatible gateway.
- **Chat workspace**: streaming conversations, thinking output, markdown rendering and multimodal attachments backed by real APIs.
- **Reactive by default**: WebFlux, Spring Security Reactive, Spring Data R2DBC, Redis sessions and cache, and virtual threads.

## Tech stack

| Area | Stack |
| --- | --- |
| Backend | Java 25, Spring Boot 4.2, WebFlux, Spring Security Reactive, Spring Data R2DBC |
| Data | PostgreSQL, Redis |
| Build | Gradle multi-module, version catalog |
| Frontend | React 19, TypeScript 5.9, Vite 8, TanStack Router/Query, Tailwind CSS v4, shadcn/ui |
| API docs | OpenAPI, validated by the `api-docs` module |

## Repository layout

| Path | Purpose |
| --- | --- |
| `appkernel/` | Backend core: identity, permissions, extension mechanism, AI kernel and HTTP endpoints |
| `appkernel/src/main/resources/extensions/` | YAML source of truth for roles, permissions, workspaces, menus and AI resources |
| `api-docs/` | OpenAPI aggregation and validation |
| `platform/appkernel/` | Backend dependency constraints (BOM) |
| `gradle/libs.versions.toml` | Backend dependency version catalog |
| `web/` | React console, bundled into the backend jar |

## Prerequisites

- JDK 25
- Node.js 20.19 or newer with npm
- PostgreSQL and Redis reachable from the backend
- Docker, optional but handy for the two services

The Gradle build installs the frontend dependencies and builds `web/` before packaging the backend jar.

## Quick start

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

### 2. Run the backend

```bash
./gradlew :appkernel:bootRun
```

Open http://localhost:8090. The backend serves the built React app, creates or updates the PostgreSQL schema on startup and reconciles the extension resources.

### 3. Run the frontend in dev mode (optional)

```bash
cd web
npm install
npm run dev
```

Vite proxies `/apis`, `/login` and `/logout` to `http://localhost:8090`, so start the backend first.

## Configuration

The backend reads standard Spring Boot configuration and the following environment variables:

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

Use `--spring.profiles.active=dev` for the local development profile (`application-dev.yaml`), which enables basic authentication and debug logging.

Secrets belong in environment variables or local configuration; never commit them.

## Build and test

```bash
./gradlew test                    # backend tests, including the bundled web build
./gradlew build                   # backend and web build
./gradlew :api-docs:validateOpenApiDocs
```

```bash
cd web
npm run lint
npm run format:check
npm run typecheck
npm run build
```

`make verify` runs the backend compile/test/OpenAPI gate plus `git diff --check`.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. The authoritative engineering rules live in [AGENTS.md](AGENTS.md) and [`.agents/rules`](.agents/rules); the agent skills are under [`.agents/skills`](.agents/skills).

## Security

Do not report security vulnerabilities in public issues. Contact the repository owner privately with reproduction steps and affected versions. Never commit credentials, tokens, private keys or production data.

## License

Copyright (C) 2026 ParsonByte.

Pudans is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

Pudans is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU General Public License](LICENSE) for more details.
