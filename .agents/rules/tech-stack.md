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

LangChain4j is the only allowed AI runtime, model and embedding library. It must stay behind the `core/agent/runtime`, `core/agent/sandbox`, `core/agent/memory` and `core/ai` boundaries.

- LangChain4j is the required AI runtime. Use `dev.langchain4j:langchain4j`, `langchain4j-open-ai`, `langchain4j-anthropic` and `langchain4j-mcp` at the versions managed by `dev.langchain4j:langchain4j-bom` `1.23.0` (`1.23.0-beta33` for the MCP module). The platform BOM imports the LangChain4j BOM and constrains the beta module.
- Do not add another AI SDK, agent framework or embedding library. Chat protocols, the agent loop, embeddings, memory extraction and tool execution use LangChain4j types.
- The Chat Completions protocol uses `OpenAiStreamingChatModel`, the Messages protocol uses `AnthropicStreamingChatModel`, the Responses protocol uses `OpenAiResponsesStreamingChatModel` and provider profiles customize the LangChain4j builders. Protocol packages expose only `StreamingChatModel`; SDK request and response types stay inside the protocol implementation.
- LangChain4j model transports use the JDK HTTP client internally. That client belongs to the model adapters and is not the project HTTP client; project code keeps using the single WebClient gateway (`AiProviderHttp`) and OkHttp only for the vendor sandbox transports.
- Embedding uses `OpenAiEmbeddingModel` for OpenAI-compatible channels. `MemoryEmbeddings` subscribes embedding calls on `Schedulers.boundedElastic()` because the SDK is synchronous.
- Model discovery imports metadata returned by the provider's real model-list API, such as identifiers, display names, context/output limits, modalities, effort levels and API capabilities. Do not invent models, release dates, prices or capabilities, and do not bundle or merge an external model catalog. When the provider's model-list API omits media model families that its official documentation publishes, the provider adapter may declare only those documented model identifiers, types and modalities, marked with `source=provider-documented`; it must not add prices, release dates or undocumented capabilities.
- Before implementing an AI capability, search LangChain4j core and LangChain4j Community for an existing interface or implementation. Reuse `ChatModel`, `StreamingChatModel`, `EmbeddingModel`, `ImageModel`, `ScoringModel`, `TextToSpeechModel`, `AudioTranscriptionModel`, `WebSearchEngine` and their existing implementations when they fit; write only the provider adapter and record why a new abstraction was necessary if none fits.
- Do not add `langchain4j-pgvector`, Qdrant, Milvus or Elasticsearch storage adapters; vector persistence stays in project R2DBC and pgvector.
- ReMe is reached through the application-level memory boundary using the project JSON WebClient gateway. It is enabled only by `pudans.memory.reme.enabled`, uses a hashed owner/workspace namespace and cannot fail a completed Agent run when recall or write-back is unavailable.
- Sandbox vendors stay behind `SandboxGateway`. AgentRun uses the project data-plane client plus `langchain4j-mcp` streamable HTTP for `process_exec_cmd`; E2B uses the project platform client and the envd Connect JSON process stream. Both are enabled only by `pudans.sandbox.enabled` plus complete provider configuration; AgentRun remains the default provider and E2B supports TAR workspace persistence only.
- Keep the Spring Boot-managed Jackson 2 line (`2.21.8`); do not introduce a second Jackson 2 version or a second application JSON library.
- No AI dependency may add a second primary HTTP client or a blocking database path. LangChain4j internal transports are not the project HTTP client.

## Verification

- Run `./gradlew :appkernel:compileJava --console=plain` and `./gradlew :appkernel:test --console=plain`.
- Run `./gradlew :api-docs:validateOpenApiDocs --console=plain`.
- Run `./gradlew build` to verify the backend build; `docker compose build` verifies the container build.
