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

## Backend AI dependencies

AgentScope Java is the only allowed AI runtime and embedding library. It must stay behind the `core/agent/runtime`, `core/agent/sandbox` and `core/ai/embedding` boundaries.

- AgentScope Java is the required agent runtime. Use `io.agentscope:agentscope-core`, `io.agentscope:agentscope-harness`, `io.agentscope:agentscope-extensions-model-openai`, `io.agentscope:agentscope-extensions-model-anthropic`, `io.agentscope:agentscope-extensions-model-dashscope`, `io.agentscope:agentscope-extensions-rag-simple`, `io.agentscope:agentscope-extensions-reme`, `io.agentscope:agentscope-extensions-sandbox-agentrun` and `io.agentscope:agentscope-extensions-sandbox-e2b` at `2.0.5`. The version targets Java 17 and runs on Java 25 with Spring Boot `4.2.0-RC2`.
- AgentScope must enter the `appkernel` runtime classpath through the project dependency declarations. Its OkHttp 5 requests must be resolved to the single project OkHttp `4.12.0` line so `com.upyun:java-sdk:4.2.3` can keep using `okhttp3.internal.Util`; the compatibility test must prove AgentScope, OpenAI, Anthropic, DashScope, RAG, ReMe, AgentRun, E2B, Harness and UpYun load together.
- Exclude `org.xerial:sqlite-jdbc`, `com.squareup.okhttp3:okhttp-jvm` and the RAG extension's unused JDBC, document-reader and vector-client dependencies globally in `appkernel`; AgentScope dependencies stay plain `api`/`implementation(libs.*)` declarations without repeated per-dependency blocks.
- Force every `com.squareup.okhttp3:okhttp` request to the project `4.12.0` line; do not exclude `okhttp` from AgentScope because the global rule and the single direct dependency resolve to the same `4.12.0` artifact.
- Do not add `agentscope-extensions-postgresql` or any JDBC state store; use project R2DBC repositories or Redis leases.
- Do not add `agentscope-extensions-cos`; object storage uses the project `com.qcloud:cos_api:5.6.283` client and `CosStorageDriver`.
- The Messages protocol uses `agentscope-extensions-model-anthropic`; the Anthropic SDK base URL is derived from the provider Messages URL so DeepSeek, MiniMax and DashScope compatible endpoints keep their `/v1/messages` path.
- The chat-completions runtime uses `agentscope-extensions-model-dashscope` for DashScope channels; the extension's `DashScopeChatModel` owns native text and multimodal routing, thinking/search flags, tool calls, structured-output fallback and DashScope base-URL resolution.
- The DashScope extension transitively supplies `com.alibaba:dashscope-sdk-java`; the platform BOM pins that transitive SDK to `2.23.7` for the native media adapter. The adapter stays behind `core/creation/provider`, every SDK call runs on `Schedulers.boundedElastic()`, and its OkHttp `4.12.0` request line resolves to the project OkHttp version.
- Model discovery reads only provider-returned model identifiers, display names and model type. Context/output limits, modalities, capabilities and parameter metadata are not imported or inferred; no external model catalog is bundled or merged.
- The DashScope catalog is Qwen-only: channel discovery keeps `qwen*`, `qwq*`, `qvq*`, `text-embedding-*` and `multimodal-embedding-*`; third-party and non-Qwen families are filtered out.
- Embedding uses the AgentScope RAG extension's `EmbeddingModel` with `DashScopeTextEmbedding` for DashScope channels and `OpenAITextEmbedding` for OpenAI-compatible channels. The 2.0.5 implementations call their SDK synchronously, so `MemoryEmbeddings` must subscribe on `Schedulers.boundedElastic()`.
- The RAG extension is used for embedding only. Its `PgVectorStore`, Qdrant, Milvus, Elasticsearch, document readers and extra HTTP clients are excluded and must not be used; vector persistence stays in project R2DBC and pgvector.
- ReMe uses `agentscope-extensions-reme` through the application-level memory boundary. It is enabled only by `pudans.memory.reme.enabled`, uses a hashed owner/workspace namespace and cannot fail a completed Agent run when recall or write-back is unavailable.
- E2B uses `agentscope-extensions-sandbox-e2b` behind `SandboxGateway`. It is enabled only by `pudans.sandbox.provider=e2b` plus complete E2B configuration; AgentRun remains the default provider.
- Do not add LangChain4j or `langchain4j-pgvector`.
- Skills, Protocol and Redis extensions are not baseline dependencies. Add one only with a wired business path and a transport that follows the R2DBC-only, single-HTTP-client and reactive rules; `agentscope-extensions-postgresql` is JDBC and remains prohibited.
- Keep the Spring Boot-managed Jackson 2 line (`2.21.8`); do not introduce a second Jackson 2 version or a second application JSON library.
- No AI dependency may add a second primary HTTP client or a blocking database path. AgentScope internal OkHttp is not the project HTTP client.

## Verification

- Run `./gradlew :appkernel:compileJava --console=plain` and `./gradlew :appkernel:test --console=plain`.
- Run `./gradlew :api-docs:validateOpenApiDocs --console=plain`.
- Run `./gradlew build` to verify the backend build; `docker compose build` verifies the container build.
