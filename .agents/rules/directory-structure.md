# Directory structure rule

## Top level

| Path | Responsibility |
| --- | --- |
| `pudans-gateway`, `pudans-iam`, `pudans-repository`, `pudans-notification`, `pudans-mq`, `pudans-job`, `pudans-search` | Runnable Spring Boot applications |
| `pudans-contract` | Service contracts: API paths, DTOs, Feign clients and constants only |
| `pudans-communal` | Shared technical libraries |
| `pudans-kernel-modules` | Domain kernels and their endpoints |
| `platform` | Nacos compose file and local infrastructure placeholders |
| `deploy` | Deployment guides, FAQ and per-service compose assets |
| `sql` | The only Liquibase changelog tree |
| `api-docs` | OpenAPI generation, aggregation and validation |
| `assets` | Repository artwork |
| `gradle` | Wrapper, version catalog and shared build scripts |
| `VERSION` | Service and shared version source; the only place to change versions |
| `.agents` | Agent rules, skills and notes |

Do not add a new top-level product module without updating this rule and `settings.gradle`.

## Applications and packages

- Application modules follow `pudans-<service>`.
- Java packages follow `cn.tolim.pudans.<service>` or the established kernel package for that
  service.
- Spring Boot entry classes are `<Service>Application`; do not reintroduce a `Pudans` prefix on
  application-level classes.
- Place HTTP endpoints in the existing `endpoint`, `controller` or service-specific API package.
  Do not create a new package vocabulary for one feature.
- Keep service-specific implementations in the owning service or kernel. Do not put business
  implementations in `pudans-contract`.

## Contracts

- `pudans-contract` contains only interfaces, DTOs, path constants, Feign clients and shared
  contract constants.
- No Spring beans, no `@Service`, no `@Configuration`, no persistence and no IO in contract modules.
- Contract DTOs and path constants are the source of truth for service-to-service calls. Update the
  contract and the provider together, and keep both serialized field names compatible.
- Internal calls use the shared `X-Pudans-Internal-Token` header and the notification/repository
  service names in `lb://` URIs.

## Kernel modules and auto-configuration

- Reusable library modules that expose auto-configuration register it in
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Do not add an imports file to a module with no `@AutoConfiguration` class.
- Host applications scan their own service package; do not broaden a component scan to another
  service's package to make a bean resolve.
- Keep `pudans-communal` free of business-specific endpoints and business data models.

## SQL

- Every DB changelog lives under `sql/<service>/`.
- Each service has `sql/<service>/changelog-master.xml`; versioned files follow
  `<version>-<service>-changelog.xml`.
- Do not place SQL XML in a module's `src/main/resources`.
- Preserve `logicalFilePath` when moving a kernel changelog; already executed databases must not
  reapply changes.
- Every `createTable`, `column` and `addColumn` needs a Chinese `remarks`.
- Never edit a deployed changelog in place; add a new versioned file.

## Configuration and generated files

- `application.yml` contains bootstrap and infrastructure fallback only. Business and runtime
  configuration belongs in the Nacos seed files under `platform/nacos/config`.
- Generated OpenAPI JSON under `api-docs/openapi/v3_0` is produced by
  `./gradlew :api-docs:generateOpenApiDocs`; never hand-edit it.
- Do not keep a second README for a module. Maintain the root `README.md`, `README.zh.md` and
  `sql/README.md`.

## New module checklist

1. Add the module to `settings.gradle`.
2. Declare dependency versions in `gradle/libs.versions.toml`; add service versions only to the
   root `VERSION` and its Gradle mapping; do not hard-code a version in a build file.
3. Choose the top-level family and package by this rule.
4. Add tests beside the production package.
5. Add the Liquibase changes under `sql/<service>` when persistence changes.
6. Register auto-configuration when the module exposes default beans.
7. Update the root README and this rule only when the public layout changes.