# Tech stack rule

## Scope

- Applies to every backend module, Gradle build file, dependency declaration and application configuration file.
- Applies to new dependencies and changes to the runtime stack.

## Backend required

- Use Java 25 with the Gradle toolchain declared in `appkernel/build.gradle`.
- Use Spring Boot `4.2.0-RC2` with WebFlux, Spring Security Reactive, Spring Data R2DBC, Spring Session Redis and Spring Cache Redis.
- Use PostgreSQL through the PostgreSQL R2DBC driver; use reactive Redis for sessions, cache, RBAC and risk control.
- Use Gradle with the version catalog at `gradle/libs.versions.toml`; platform constraints live under `platform/appkernel`.
- Use JUnit 5, Mockito and Reactor Test for backend tests.
- Use the project's Jackson setup: Jackson 3 for application JSON and the Jackson 2 compatibility module for legacy Redis session/cache values.
- Use Argon2 as the default password encoder and BCrypt only for verification of existing credentials.
- Keep HTTP APIs on the single `api.pudans.tolim.cn` group and the `/apis/api.pudans.tolim.cn/v1alpha1/` prefix.

## Backend prohibited

- Do not introduce PF4J, plugin runtimes, plugin SPI, the extension API group, `ExtensionDefinition`, `ExtensionPointDefinition`, `Plugin` or `ReverseProxy`.
- Do not introduce JDBC, `JdbcTemplate`, JPA, Hibernate, MyBatis, jOOQ or blocking database access.
- Do not introduce Flyway or Liquibase.
- Do not introduce Spring MVC or direct Servlet API usage; the backend is WebFlux.
- Do not replace Redis sessions or the Redis cache with a local or alternative distributed store.
- Do not make a blocking dependency mandatory for the request path.

## AI kernel

- Use LangChain4j for model invocation, agent orchestration, embedding, reranking, memory generation and RAG. Declare the LangChain4j BOM in `platform/appkernel` and the required modules in the version catalog.
- Keep the WebFlux `WebClient`, R2DBC and Jackson 3 setup for the gateway compatibility endpoints and for provider protocols LangChain4j does not cover. LangChain4j's internal HTTP and JSON libraries are allowed only inside the library; application code must not add a second HTTP client or JSON mapper.
- Model calls use the LangChain4j `ChatModel` and `StreamingChatModel` APIs. Prefer the non-blocking APIs; providers without a non-blocking implementation must run on `Schedulers.boundedElastic()`.
- Model invocation is centralized in `LangChain4jChatService`; embedding model construction is centralized in `LangChain4jEmbeddingModelFactory`; reranking is centralized in `LangChain4jScoringModelFactory` using the LangChain4j `ScoringModel` API. Do not add provider adapter, embedding client or rerank client classes. RAG components are migrated in stages.
- Agent orchestration uses LangChain4j AI Services with the gateway-backed `GatewayChatModel` and `GatewayStreamingChatModel` bridges. The Harness runtime under `core/ai/harness` may implement a single LangChain4j `ChatModel` reasoning/tool loop to provide typed events, permissions, interruption and durable state; do not add any other agent loop or a second harness implementation.
- MCP servers are accessed through the LangChain4j MCP client (`langchain4j-mcp`); do not hand-write JSON-RPC, SSE or STDIO transports.
- MiniMax Native remains unsupported by the LangChain4j model path; do not reintroduce a WebClient chat adapter. MiniMax Messages, Chat and Responses use the Anthropic/OpenAI modules.
- Provider resources expose `spec.vendor`. The transport protocol is internal and derived from the vendor; do not expose protocol variants in the UI, presets, extension schema or public responses. Presets expose one entry per vendor and a base URL.
- Built-in system prompts live under `core/ai/prompt`; services reference them instead of inline literals.
- Providers, accounts, models and route groups remain extension resources. Model instances are built from extension resources per request or cached by provider, account and model.
- Gateway request and response protocols for `/apis/api.pudans.tolim.cn/v1alpha1/aigateway/...` remain internal codecs; LangChain4j is not a server-side protocol implementation. Root `/v1/...` compatibility is prohibited unless this rule is changed.
- RAG uses LangChain4j `EmbeddingModel`, `EmbeddingStore` and `ContentRetriever` abstractions. The `EmbeddingStore` is implemented on R2DBC; do not introduce JDBC, JPA or a LangChain4j JDBC embedding store.
- Memory and knowledge-base work uses the existing `StorageService` and `AuditService`. Do not add a second object-storage, audit or permission implementation.
- Document parsing with Apache Tika/PDFBox is allowed only when the dependency is declared in the version catalog and executed on `Schedulers.boundedElastic()`; it must never run on the request path.

## Dependency management

- Declare every new backend dependency version in `gradle/libs.versions.toml`; reference it from a build file through a `libs.*` alias.
- For backend dependencies, raw coordinates are allowed only when Spring Boot or the platform BOM supplies the version; do not hard-code a version string in a build file.
- State the purpose and overlap with existing dependencies when adding a dependency; for backend dependencies also state reactive compatibility.
- Do not maintain two implementations of the same capability, such as two primary HTTP clients or two primary JSON libraries.

## Backend configuration

- Declare new configuration properties in a `@ConfigurationProperties` class and document the key in `application.yaml`.
- Keep sensitive values in environment variables; do not hard-code credentials, keys, tokens or passwords.
- Keep the existing `pudans.*` property structure; do not add an undocumented `@Value` key.
- Keep the existing profiles: `application.yaml` for defaults and `application-dev.yaml` for local development overrides.

## Backend legacy allowances

- Resilience4j is allowed for Webhook retry and the existing `RequestNotPermitted` mapping; do not extend it into a general retry framework.
- OpenAPI4j is allowed for extension schema validation; do not use it for runtime document generation.
- Springdoc is allowed only for route annotations and build-time OpenAPI authoring; runtime `/v3/api-docs` remains disabled.
- Jackson 2 is allowed only for legacy Redis session/cache compatibility; new application JSON uses Jackson 3.
- BCrypt is allowed only for verifying passwords previously encoded with BCrypt; new passwords use Argon2.
- The `@Value` audit retention properties are existing configuration and remain allowed; new configuration uses `@ConfigurationProperties`.
- Raw dependency coordinates without a version are allowed when the Spring Boot BOM or `platform/appkernel` supplies the version.

## Verification

- Run `./gradlew :appkernel:compileJava --console=plain` and `./gradlew :appkernel:test --console=plain`.
- Run `./gradlew :api-docs:validateOpenApiDocs --console=plain`.
- Run `./gradlew build` to verify the backend build; `docker compose build` verifies the container build.
