# Tech stack rule

## Required stack

| Area | Choice |
| --- | --- |
| Language | Java 25, Gradle toolchain |
| Build | Gradle Wrapper 9.9; dependency versions in `gradle/libs.versions.toml`; service versions in the root `VERSION` |
| Framework | Spring Boot 4.2.0, Spring Cloud 2026.0.0, Spring Cloud Alibaba 2025.1.0.0 |
| Web | Spring MVC servlet applications behind Spring Cloud Gateway WebFlux |
| Persistence | PostgreSQL through Spring Data JPA; `ddl-auto=validate`; Liquibase changelogs |
| Cache and sessions | Redis |
| Service calls | OpenFeign, Spring Cloud LoadBalancer, `lb://` service URIs |
| Discovery and config | Nacos discovery and Nacos Config |
| API docs | springdoc 3.2.1, OpenAPI 3.0 generation, doc profile |
| Cryptography | `cn.tolim.spissatus4j` only |
| Tests | JUnit 5, Spring Boot Test, Testcontainers for IAM PostgreSQL/Redis |

The root build applies the three BOMs and declares Java 25 for every project. Declare new
dependency versions in the catalog and reference them through `libs.*`.

## Versioning

- The root `VERSION` file is the only source of service and shared versions. Keys include
  `GATEWAY.VERSION`, `IAM.VERSION`, `REPOSITORY.VERSION`, `NOTIFICATION.VERSION`, `MQ.VERSION`,
  `JOB.VERSION`, `SEARCH.VERSION` and `SHARED.VERSION`.
- Gradle reads the file at configuration time. Each application and its owned contract and kernel
  modules use the service key; `SHARED.VERSION` covers shared libraries and the api-docs module.
- Do not hardcode an application version in a build file. Change the value in `VERSION` and let the
  root mapping apply it.
- Regenerate and validate the OpenAPI documents after changing a version.

## Configuration

- Nacos Config is the source of truth for business and runtime configuration.
- `application.yml` keeps bootstrap and infrastructure fallback only.
- Seed dataIds live in `platform/nacos/config/<application-name>.yaml`; publish them with
  `platform/nacos/config/publish.sh`.
- Use `@ConfigurationProperties` for new keys. Avoid new `@Value` keys; document every new key in the
  seed file.
- Keep secrets, tokens and passwords in environment variables; never commit them.
- Dynamic refresh is explicit: add `@RefreshScope` only where a bean must rebuild; state which keys
  require a service restart.

## Contracts and APIs

- Public API prefix: `/apis/pudans.tolim.cn/v1alpha1/`.
- `pudans-contract-*` modules hold Feign clients, paths and DTOs only.
- Internal service calls use the shared `X-Pudans-Internal-Token` header.
- Service discovery uses Nacos; gateway routes use `lb://<service-name>`.
- OpenAPI metadata uses Chinese tags and summaries. Generate and validate the documents; do not
  hand-edit the JSON.

## Prohibited

- MySQL, Oracle, SQL Server and any database other than PostgreSQL.
- Seata, distributed transaction frameworks and transaction coordinators.
- eiam support or any external eiam dependency; eiam is a local reference source only.
- A second crypto implementation, direct ad-hoc JCA use or a competing crypto library.
- SQL changelogs outside `sql`; schema changes without a versioned Liquibase file.
- Manual edits to `api-docs/openapi/v3_0` or hand-written OpenAPI JSON.
- Static, environment-specific runtime configuration committed only in `application.yml`.
- Duplicate provider implementations for the same capability, such as two file-storage adapters or
  two notification senders with the same channel.
- Build output, local configuration or secrets in the repository.

## Verification

```bash
./gradlew test
./gradlew :api-docs:validateOpenApiDocs
```

When controllers or annotations change, run `./gradlew :api-docs:generateOpenApiDocs` first. When
Nacos seed configuration changes, publish it before starting the service.