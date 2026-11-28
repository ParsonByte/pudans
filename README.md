<p align="center">
  <img src="assets/pudans.svg" alt="pudans" width="128">
</p>

<h1 align="center">pudans</h1>

<p align="center">
  <strong>Enterprise identity and access management, delivered as a microservice platform.</strong>
</p>

<p align="center">
  A Java 25 / Spring Boot 4.2 platform for authentication, authorization, file storage,
  notifications, messaging, jobs and search.<br>
  One gateway, explicit service contracts, PostgreSQL persistence and Nacos-backed configuration.
</p>

<p align="center">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-orange.svg">
  <img alt="Spring Boot 4.2" src="https://img.shields.io/badge/Spring_Boot-4.2-6DB33F.svg">
  <img alt="Spring Cloud 2026" src="https://img.shields.io/badge/Spring_Cloud-2026-6DB33F.svg">
  <img alt="Gradle 9.9" src="https://img.shields.io/badge/Gradle-9.9-02303A.svg">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-16-336791.svg">
  <img alt="Nacos 3.1" src="https://img.shields.io/badge/Nacos-3.1-1890FF.svg">
</p>

<p align="center">
  <a href="README.md"><strong>English</strong></a> · <a href="README.zh.md">简体中文</a>
</p>

<p align="center">
  <a href="#overview">Overview</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#services">Services</a> ·
  <a href="#repository-layout">Repository layout</a> ·
  <a href="#getting-started">Getting started</a> ·
  <a href="#configuration">Configuration</a> ·
  <a href="#api-documentation">API documentation</a> ·
  <a href="#versioning">Versioning</a>
</p>

## Overview

pudans is an enterprise identity and access management platform built as a set of small,
independently deployable Spring Boot services. The gateway is the only public edge; services
communicate through explicit Feign contracts and a shared internal token.

- **Identity first.** Login, MFA, global sessions, SSO tickets, WebAuthn, TOTP, CAM-style
  authorization, identity sources and synchronization live in IAM.
- **Contract-first services.** `pudans-contract` contains only Feign clients, paths and DTOs;
  implementations stay in their owning service.
- **Configuration is operational data.** Nacos Config is the source of truth for runtime
  settings; `platform/nacos/config` contains the seed dataIds.
- **PostgreSQL everywhere.** Each service owns its database and applies Liquibase changes from
  the single root `sql` tree.
- **OpenAPI is generated.** The `api-docs` module starts the services with a doc profile and
  writes the OpenAPI 3.0 documents to `api-docs/openapi/v3_0`.

## Architecture

```text
Browser / API clients
                    │
                    ▼
            pudans-gateway :8080
     /apis/pudans.tolim.cn/v1alpha1/**
                    │
     ┌──────────────┼──────────────┬──────────────┬──────────────┐
     ▼              ▼              ▼              ▼              ▼
 pudans-iam    pudans-repository  pudans-notification  pudans-mq  pudans-job
   :8081           :8091              :8092            :8093      :8094
     │
     └──────────────┬──────────────┐
                    ▼              ▼
             pudans-search      Nacos
               :8095      discovery + config
```

Services register with Nacos. The gateway builds routes from Nacos metadata (`gateway.path`) and
routes `lb://<service>` URIs; there is no static service route list to maintain.

## Services

| Service | Port | Responsibility |
| --- | --- | --- |
| `pudans-gateway` | 8080 | Public edge, route selection, session enforcement, edge rate limiting and internal-token injection |
| `pudans-iam` | 8081 | Identity, login, MFA, sessions, SSO tickets, identity sources, authorization |
| `pudans-repository` | 8091 | File metadata and object-storage providers (local, OSS, COS, MinIO, Qiniu, S3) |
| `pudans-notification` | 8092 | Email, SMS, Feishu and DingTalk delivery, templates, retries and dead letters |
| `pudans-mq` | 8093 | Message center, deliveries, retries and dead letters; optional RocketMQ announcement transport |
| `pudans-job` | 8094 | Job definitions, executions and scheduler-facing executor APIs |
| `pudans-search` | 8095 | Search indexes, documents and PostgreSQL-backed queries |

The gateway exposes every service under the shared API prefix `/apis/pudans.tolim.cn/v1alpha1/`.

## Repository layout

| Path | Purpose |
| --- | --- |
| `pudans-gateway`, `pudans-iam`, `pudans-repository`, `pudans-notification`, `pudans-mq`, `pudans-job`, `pudans-search` | Runnable Spring Boot applications |
| `pudans-contract` | Service-to-service contracts: paths, DTOs, Feign clients and constants only |
| `pudans-communal` | Shared libraries: core, web, security, mybatis, redis, mq (in-memory and RocketMQ), log and feign |
| `pudans-kernel-modules` | Domain kernels: IAM, audit, repository, notification, mq, job and search |
| `platform` | Nacos compose file, local infrastructure, and importable observability assets under `platform/observability` |
| `sql` | Every Liquibase changelog; see [sql/README.md](sql/README.md) |
| `api-docs` | OpenAPI generation, aggregation and validation; generated JSON lives in `api-docs/openapi/v3_0` |
| `deploy` | Deployment guides, FAQ and per-service Docker Compose assets under `deploy/assets` |
| `.agents` | Agent rules, skills and project notes |
| `gradle/libs.versions.toml` | Single source of dependency versions |
| `VERSION` | Service and shared library versions; the only service-version source |

## Stack

| Layer | Technology |
| --- | --- |
| Runtime | Java 25, Spring Boot 4.2.0 |
| Cloud | Spring Cloud 2026.0.0, Spring Cloud Alibaba 2025.1.0.0 (Nacos) |
| Data | PostgreSQL 16+, Redis 7+, Spring Data JPA, Liquibase |
| Service calls | OpenFeign, Spring Cloud LoadBalancer, Nacos discovery |
| Edge and APIs | Spring Cloud Gateway WebFlux, Spring MVC, springdoc 3.2.1 |
| Edge protection | Sentinel 1.8.9 through Spring Cloud Alibaba |
| Messaging | RocketMQ 5.3.1 for the optional announcement transport; PostgreSQL remains the message source of truth |
| Observability | Self-hosted Grafana + Prometheus + Jaeger through 1Panel |
| Cryptography | `cn.tolim.spissatus4j` |
| Tests | JUnit 5, Spring Boot Test, Testcontainers |

## Versioning

Service versions are defined only in the root [VERSION](VERSION) file:

```properties
GATEWAY.VERSION=0.1.0
IAM.VERSION=0.1.0
REPOSITORY.VERSION=0.1.0
NOTIFICATION.VERSION=0.1.0
MQ.VERSION=0.1.0
JOB.VERSION=0.1.0
SEARCH.VERSION=0.1.0
SHARED.VERSION=0.1.0
```

Gradle reads the file at configuration time and applies each version to the service and its owned
contract and kernel modules. `SHARED.VERSION` covers shared libraries and the api-docs module. Do not set a service version in a build file. Regenerate the OpenAPI documents
after changing a version.

## Requirements

- JDK 25 with the Gradle toolchain
- Gradle Wrapper 9.9 (included)
- PostgreSQL 16+ (one database per service)
- Redis 7+ for IAM sessions and caches
- Nacos 3.1+ for service discovery and configuration
- Docker for the IAM Testcontainers tests

## Getting started

Start Nacos:

```bash
cd platform/nacos && docker compose up -d
```

The Sentinel dashboard and RocketMQ broker are optional local dependencies:

```bash
cd platform/sentinel && docker compose up -d
```

```bash
cd platform/rocketmq && docker compose up -d
```

Provide PostgreSQL and Redis through your local setup; `platform/pgsql` and `platform/redis` are
placeholder directories for local infrastructure definitions. Publish the seed configuration before
starting services:

```bash
platform/nacos/config/publish.sh
```

Build the project and run the IAM service, then the gateway:

```bash
./gradlew build
./gradlew :pudans-iam:bootRun
```

```bash
./gradlew :pudans-gateway:bootRun
```

The default ports are listed in [Services](#services). Database URLs, credentials, Redis and
Nacos addresses can be overridden with the `PUDANS_*` environment variables documented by each
service's bootstrap configuration.

Production deployment is documented in [deploy/index.md](deploy/index.md). Middleware is installed
from the 1Panel app store; per-service Docker Compose files live under `deploy/assets`.

## Configuration

Business and runtime configuration is loaded from Nacos Config. Each application imports its own
dataId:

```yaml
spring:
  config:
    import: optional:nacos:${spring.application.name}.yaml?group=DEFAULT_GROUP&refreshEnabled=true
```

The seed files live in [platform/nacos/config](platform/nacos/config):

| Service | dataId |
| --- | --- |
| `pudans-gateway` | `pudans-gateway.yaml` |
| `pudans-iam` | `pudans-iam.yaml` |
| `pudans-repository` | `pudans-repository.yaml` |
| `pudans-notification` | `pudans-notification.yaml` |
| `pudans-mq` | `pudans-mq.yaml` |
| `pudans-job` | `pudans-job.yaml` |
| `pudans-search` | `pudans-search.yaml` |

`platform/nacos/config/publish.sh` publishes every YAML and JSON file to `DEFAULT_GROUP` and can be
run repeatedly. Set `NACOS_ADDR` or `NACOS_GROUP` to override the defaults.

`@ConfigurationProperties` values and the gateway static route beans refresh from Nacos. The
gateway route locator and the IAM Feign internal-token interceptor use `@RefreshScope`.
Datasource, JPA, Liquibase, Redis, SMTP, gateway CORS and JWT values captured at startup require
a restart after a change.

If a dataId is missing and the local fallback does not contain the business keys, the service may
fail its startup validation; publish the seed configuration before starting a local environment.

### Edge protection and messaging

Sentinel rule dataIds live beside the service seeds: `pudans-gateway-sentinel-gw-api-group.json`,
`pudans-gateway-sentinel-gw-flow.json`, `pudans-notification-sentinel-flow.json` and
`pudans-notification-sentinel-degrade.json`. Rule JSON hot-reloads from Nacos; datasource,
transport and gateway filter settings require a restart.

The message center uses the in-memory broker by default. Set `pudans.messaging.broker=rocketmq`
to enable the RocketMQ provider; the announcement topic and consumer group are configured through
`pudans.mq.announcement-*`. RocketMQ is transport only: PostgreSQL remains the message source of
truth and the dispatch scheduler reconciles lost announcements. ACL credentials are supplied
through `PUDANS_ROCKETMQ_ACCESS_KEY` and `PUDANS_ROCKETMQ_SECRET_KEY`. Pre-create the announcement
topic and consumer group and disable auto-creation in production.

### Observability

The self-hosted Grafana stack runs from 1Panel: Grafana (host port 3300), Prometheus (9090)
and Jaeger (16686/4318). Every service exposes
`health,info,metrics,prometheus` on a dedicated management port bound to
`PUDANS_MANAGEMENT_ADDRESS` (default `127.0.0.1`):

| Service | Management port |
| --- | --- |
| `pudans-gateway` | 9080 |
| `pudans-iam` | 9081 |
| `pudans-repository` | 9091 |
| `pudans-notification` | 9092 |
| `pudans-mq` | 9093 |
| `pudans-job` | 9094 |
| `pudans-search` | 9095 |

Health probes that previously used the application port must move to the management port. The
gateway keeps `/apis/pudans.tolim.cn/v1alpha1/actuator`, IAM keeps
`/apis/pudans.tolim.cn/v1alpha1/iam/actuator`, and the other services use `/actuator`.
Prometheus scrape and Grafana/Jaeger datasource samples are in `platform/observability`. Replace
the `pudans-host` and `1panel-host` placeholders with the LAN addresses. Set
`PUDANS_OTLP_TRACING_ENDPOINT` to the Jaeger OTLP endpoint. Logs are ECS JSON; set
`PUDANS_LOG_FILE` to a JSON file path to keep them on the host, or read them from the container
log stream. This stack has no centralized log store; use the 1Panel container log viewer or the
log files. `pudans-observability.yaml` sets `spring.reactor.context-propagation=auto`, and the
WebFlux log module registers `requestId` as a thread-local accessor, so the gateway keeps it in
the MDC across Reactor thread switches. Downstream services receive `X-Request-Id` and
`traceparent` and write them to their own MDC.

When Prometheus runs on another host, set `PUDANS_MANAGEMENT_ADDRESS` to an address reachable
from the 1Panel host and restrict the management ports with the firewall. Management endpoints
are unauthenticated and must stay on the internal network. The shared observability dataId is
`pudans-observability.yaml`; publish it with `platform/nacos/config/publish.sh`. Management port,
tracing exporter, structured logging and Reactor context-propagation changes require a restart.

## API documentation

The OpenAPI 3.0 documents are generated from the running services, not hand-authored:

```bash
./gradlew :api-docs:generateOpenApiDocs
```

This writes `aggregated.json` and `apis_iam`, `apis_repository`, `apis_notification`, `apis_mq`,
`apis_job` and `apis_search` `.v1alpha1.json` files to
[api-docs/openapi/v3_0](api-docs/openapi/v3_0). Operation tags and summaries are Chinese. The
gateway has no business controllers, so it has no separate group.

Validate the generated documents without starting services:

```bash
./gradlew :api-docs:validateOpenApiDocs
```

## Database changes

All Liquibase XML lives under [sql](sql). Each service has a `changelog-master.xml`; kernel
changelogs use versioned file names and retain their original `logicalFilePath` so already
executed databases stay compatible. Every table and column carries a Chinese `remarks` value.
Read [sql/README.md](sql/README.md) before adding or changing a changelog.

## Testing

```bash
./gradlew test
```

The full suite uses JUnit 5 and Testcontainers for the IAM PostgreSQL/Redis tests. Tests disable
Nacos discovery and config. When OpenAPI annotations or controllers change, also run:

```bash
./gradlew :api-docs:validateOpenApiDocs
```

## Agent guide

Read [AGENTS.md](AGENTS.md) before changing code, configuration, SQL or documentation. The
detailed rules are in [.agents/rules](.agents/rules), executable procedures are in
[.agents/skills](.agents/skills), and project-specific facts are in
[.agents/notes](.agents/notes).

## Conventions

- Every service API uses the prefix `/apis/pudans.tolim.cn/v1alpha1/`; do not add root `/api`
  aliases.
- `pudans-contract` contains interfaces, DTOs, paths and Feign clients only; no implementations.
- Service-to-service calls carry `X-Pudans-Internal-Token` from `pudans.internal.token`.
- PostgreSQL is the only supported database. Use the root `sql` tree and Liquibase; never put
  changelogs in a module's `src/main/resources`.
- The PostgreSQL message center is the source of truth for messages; RocketMQ is an optional
  transport.
- All encryption and decryption uses `cn.tolim.spissatus4j`; do not add ad-hoc cryptography.
- Do not add eiam support or Seata. eiam is a local reference source only.
- Java comments are English, single-line `//`; do not add comments to SQL, YAML, Gradle, shell or
  Markdown files.
- Agents never push, open pull requests or change remote state; the user performs every remote
  operation.