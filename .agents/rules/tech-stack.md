# Tech stack rule

## Scope

- Applies to every backend module, Gradle build file, dependency declaration and application configuration file.
- Applies to the `web/` frontend project: its source code, package files, build configuration and dependencies.
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
- Agent orchestration uses LangChain4j AI Services with the gateway-backed `GatewayChatModel` and `GatewayStreamingChatModel` bridges; do not add custom agent loops.
- MCP servers are accessed through the LangChain4j MCP client (`langchain4j-mcp`); do not hand-write JSON-RPC, SSE or STDIO transports.
- MiniMax Native remains unsupported by the LangChain4j model path; do not reintroduce a WebClient chat adapter. MiniMax Messages, Chat and Responses use the Anthropic/OpenAI modules.
- Provider resources expose `spec.vendor`. The transport protocol is internal and derived from the vendor; do not expose protocol variants in the UI, presets, extension schema or public responses. Presets expose one entry per vendor and a base URL.
- Built-in system prompts live under `core/ai/prompt`; services reference them instead of inline literals.
- Providers, accounts, models and route groups remain extension resources. Model instances are built from extension resources per request or cached by provider, account and model.
- Gateway request and response protocols for `/apis/api.pudans.tolim.cn/v1alpha1/aigateway/...` remain internal codecs; LangChain4j is not a server-side protocol implementation. Root `/v1/...` compatibility is prohibited unless this rule is changed.
- RAG uses LangChain4j `EmbeddingModel`, `EmbeddingStore` and `ContentRetriever` abstractions. The `EmbeddingStore` is implemented on R2DBC; do not introduce JDBC, JPA or a LangChain4j JDBC embedding store.
- Memory and knowledge-base work uses the existing `StorageService` and `AuditService`. Do not add a second object-storage, audit or permission implementation.
- Document parsing with Apache Tika/PDFBox is allowed only when the dependency is declared in the version catalog and executed on `Schedulers.boundedElastic()`; it must never run on the request path.

## Frontend

- The frontend lives in `web/` and is packaged into the backend jar through `web/build.gradle`. The root Gradle build installs npm dependencies, builds `web/`, copies the output into `appkernel` static resources and serves it from the backend on port 8090. Node and npm are required for Gradle builds. Use npm with `web/package.json` and the committed `web/package-lock.json`; run `npm run dev` for local development and do not add another package manager or lockfile.
- Frontend configuration is defined by `web/build.gradle`, `web/vite.config.ts`, `web/tsconfig*.json`, `web/eslint.config.js`, `web/.prettierrc.json`, `web/knip.json` and `web/components.json`; keep it consistent with this rule.
- Use React 19 and TypeScript 5.9 with Vite 8 and the configured plugins: `@vitejs/plugin-react`, `@tanstack/router-plugin` and `@tailwindcss/vite`.
- Routing uses TanStack Router file-based routes in `web/src/routes`. A route file exports `Route`; `web/src/routeTree.gen.ts` is generated by the router plugin and must never be edited by hand. Do not add another router.
- Server state uses TanStack Query and requests go through Axios. Forms use React Hook Form with Zod schemas through `@hookform/resolvers`. Client global state uses Zustand; URL state uses `nuqs`. Do not add a second data-fetching, form, validation or state library.
- Tables use TanStack Table, charts use Recharts, dates use date-fns, icons use `lucide-react` (plus `@iconify/react` for icon sets Lucide does not cover), toasts use `sonner`, command menus use `cmdk`. Transitions use `react-transition-group` and `react-animate-height`; viewport detection uses `react-intersection-observer`; keyboard shortcuts use `react-hotkeys-hook`; virtual lists use `react-virtuoso`; fuzzy search uses `fuse.js` with `react-highlight-words`; drag and drop uses `react-dnd` with `react-dnd-html5-backend` and `react-draggable`; color picking uses `@uiw/react-color-sketch` with `@uiw/color-convert`; QR codes use `qrcode.react`; loading progress uses `react-top-loading-bar`; OTP input uses `input-otp`; date picking uses `react-day-picker`.
- Styling uses Tailwind CSS v4 through `@tailwindcss/vite`, `tw-animate-css` for animation utilities, and the shadcn/ui theme tokens in `web/src/styles/_global.css`. Component variants use `class-variance-authority`; class names merge through `cn` (the `cn` package, re-exported by `@/lib/utils`), and `clsx` and `tailwind-merge` are also part of the approved toolchain. Use the `@/` alias for `web/src`; do not add another class-name or styling utility.
- Lint with ESLint (`@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `@tanstack/eslint-plugin-query`), format with Prettier (`@trivago/prettier-plugin-sort-imports`, `prettier-plugin-tailwindcss`), and detect unused files, exports and dependencies with Knip; do not bypass or weaken these checks.
- Transient user feedback uses the global Sonner toaster: mount the shadcn `Toaster` from `web/src/components/ui/sonner.tsx` once in the root route and call `toast` from `sonner` for errors, successes, warnings and notices. Do not render inline alert banners, status strips or disabled button labels for transient messages.
- Login method buttons use concise provider labels (`通行密钥`, `飞书`); do not append action wording such as 「使用…登录」.
- Do not add mock, fake, demo, seeded or stubbed data anywhere in the repository, including development fixtures and preview data. Do not use `@faker-js/faker`.

### shadcn/ui

- shadcn/ui is the only UI component library. Every UI primitive and interactive control must use the shadcn/ui components in `web/src/components/ui` (dialog, alert dialog, dropdown menu, select, tabs, tooltip, popover, checkbox, radio group, switch, scroll area, separator, avatar, label, collapsible, table and the rest); never reach for another library.
- `web/src/components/ui` contains only components introduced through the shadcn CLI. When a required component is missing, run `npx shadcn@latest add <component>` from `web/`; never create, copy or hand-write a component file in that directory.
- Pages, layouts and feature components may be written by hand, but they must compose the CLI-generated shadcn/ui components and must not re-implement UI primitives.
- Do not import Radix primitives directly outside `web/src/components/ui`; use the shadcn/ui wrapper instead. If a wrapper does not exist yet, add it with the CLI first.
- If a shadcn/ui component needs different behaviour, compose it or edit the CLI-generated file; do not create a second component with the same responsibility.
- Keep `web/components.json` and the theme variables in `web/src/styles/_global.css` as the single source of design tokens. Do not replace the theme with another design system or override components with inline-style theming.

### Frontend prohibited

- Do not introduce another component library such as MUI, Ant Design, Chakra UI or Bootstrap, and do not hand-create or hand-copy any file under `web/src/components/ui`; missing components are added with the shadcn CLI.
- Do not add a second router, data-fetching, form, validation, global-state or styling library.
- Do not add a second notification library, and do not render inline alert banners for transient user feedback.
- Do not add pnpm, yarn or bun lockfiles under `web/`.
- Do not edit `web/src/routeTree.gen.ts`, `web/dist/` or other generated output by hand.

## Dependency management

- Declare every new backend dependency version in `gradle/libs.versions.toml`; reference it from a build file through a `libs.*` alias.
- Declare frontend dependencies in `web/package.json` and lock them in `web/package-lock.json` with npm; do not add them to `gradle/libs.versions.toml`.
- Keep TypeScript within the version range supported by `typescript-eslint`; upgrade TypeScript and the lint toolchain together.
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
- For frontend changes run, from `web/`: `npm run lint`, `npm run format:check`, `npm run typecheck` and `npm run build`.
- Run `./gradlew build` to verify the web bundle is packaged into the backend jar and the SPA fallback is active.
- Run `npx knip` from `web/` when dependencies, files or exports change.
- Run `rg -n 'node|pnpm|npm|vite|react|typescript|pf4j|jdbc|jpa|hibernate|mybatis|jooq|flyway|liquibase' appkernel gradle platform --glob '!**/build/**'` and confirm frontend tooling has not leaked into backend modules; the frontend stack is allowed only under `web/`.
