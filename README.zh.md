<p align="center">
  <img src="assets/pudans.svg" alt="pudans" width="128">
</p>

<h1 align="center">pudans</h1>

<p align="center">
  <strong>企业级身份与访问管理，以微服务平台交付。</strong>
</p>

<p align="center">
  基于 Java 25 / Spring Boot 4.2 的微服务平台，覆盖认证、授权、文件存储、通知、消息、任务与搜索。<br>
  统一网关、显式服务契约、PostgreSQL 持久化，以及由 Nacos 驱动的配置。
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
  <a href="README.md">English</a> · <a href="README.zh.md"><strong>简体中文</strong></a>
</p>

<p align="center">
  <a href="#概览">概览</a> ·
  <a href="#架构">架构</a> ·
  <a href="#服务">服务</a> ·
  <a href="#仓库结构">仓库结构</a> ·
  <a href="#快速开始">快速开始</a> ·
  <a href="#配置">配置</a> ·
  <a href="#api-文档">API 文档</a> ·
  <a href="#版本管理">版本管理</a>
</p>

## 概览

pudans 是一个企业级身份与访问管理平台，由一组可独立部署的 Spring Boot 服务组成。网关是唯一
公网入口；服务之间通过显式的 Feign 契约和共享内部令牌通信。

- **身份优先。** 登录、MFA、全局会话、SSO 票据、WebAuthn、TOTP、CAM 风格授权、身份源与同步
  都由 IAM 承载。
- **契约先行。** `pudans-contract` 只放 Feign 客户端、路径与 DTO；实现留在所属服务。
- **配置即运行数据。** Nacos Config 是运行时配置的权威来源；`platform/nacos/config` 保存种子
  dataId。
- **统一 PostgreSQL。** 每个服务拥有独立数据库，Liquibase 变更统一来自根目录 `sql`。
- **OpenAPI 自动生成。** `api-docs` 模块以 doc profile 启动服务，生成 OpenAPI 3.0 文档到
  `api-docs/openapi/v3_0`。

## 架构

```text
浏览器 / API 客户端
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
               :8095      服务发现 + 配置
```

服务注册到 Nacos。网关根据 Nacos 元数据（`gateway.path`）生成路由，并以 `lb://<service>`
转发；不需要维护静态服务路由列表。

## 服务

| 服务 | 端口 | 职责 |
| --- | --- | --- |
| `pudans-gateway` | 8080 | 公网入口、路由选择、会话校验、边缘限流与内部令牌注入 |
| `pudans-iam` | 8081 | 身份、登录、MFA、会话、SSO 票据、身份源与授权 |
| `pudans-repository` | 8091 | 文件元数据与对象存储（local、OSS、COS、MinIO、Qiniu、S3） |
| `pudans-notification` | 8092 | 邮件、短信、飞书与钉钉投递、模板、重试与死信 |
| `pudans-mq` | 8093 | 消息中心、投递、重试与死信；可选 RocketMQ 宣布传输 |
| `pudans-job` | 8094 | 任务定义、任务执行与调度器执行器 API |
| `pudans-search` | 8095 | 搜索索引、文档与 PostgreSQL 查询 |

所有服务通过统一前缀 `/apis/pudans.tolim.cn/v1alpha1/` 暴露 API。

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `pudans-gateway`、`pudans-iam`、`pudans-repository`、`pudans-notification`、`pudans-mq`、`pudans-job`、`pudans-search` | 可运行的 Spring Boot 应用 |
| `pudans-contract` | 服务间契约：路径、DTO、Feign 客户端与常量，仅接口 |
| `pudans-communal` | 公共库：core、web、security、mybatis、redis、mq（内存与 RocketMQ）、log、feign |
| `pudans-kernel-modules` | 领域内核：IAM、审计、仓储、通知、MQ、任务与搜索 |
| `platform` | Nacos compose 文件、本地基础设施，以及 `platform/observability` 下可导入的可观测性资产 |
| `sql` | 全部 Liquibase 变更；见 [sql/README.md](sql/README.md) |
| `api-docs` | OpenAPI 生成、聚合与校验；生成文件位于 `api-docs/openapi/v3_0` |
| `deploy` | 部署指南、FAQ 与 `deploy/assets` 下的分服务 Docker Compose 资源 |
| `.agents` | Agent 规则、技能与项目笔记 |
| `gradle/libs.versions.toml` | 依赖版本的唯一来源 |
| `VERSION` | 服务与公共库版本；服务版本的唯一来源 |

## 技术栈

| 层 | 技术 |
| --- | --- |
| 运行时 | Java 25、Spring Boot 4.2.0 |
| 云与治理 | Spring Cloud 2026.0.0、Spring Cloud Alibaba 2025.1.0.0（Nacos） |
| 数据 | PostgreSQL 16+、Redis 7+、Spring Data JPA、Liquibase |
| 服务调用 | OpenFeign、Spring Cloud LoadBalancer、Nacos 服务发现 |
| 网关与 API | Spring Cloud Gateway WebFlux、Spring MVC、springdoc 3.2.1 |
| 边缘保护 | Sentinel 1.8.9（Spring Cloud Alibaba） |
| 消息传输 | RocketMQ 5.3.1（可选宣布传输；PostgreSQL 消息中心仍是权威来源） |
| 可观测性 | 自建 Grafana + Prometheus + Jaeger（通过 1Panel） |
| 密码学 | `cn.tolim.spissatus4j` |
| 测试 | JUnit 5、Spring Boot Test、Testcontainers |

## 版本管理

服务版本只在根目录 [VERSION](VERSION) 中定义：

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

Gradle 在配置阶段读取该文件，并把版本应用到服务及其所属的 contract/kernel 模块。
`SHARED.VERSION` 用于公共库与 api-docs 模块。不要在 build 文件中设置服务版本。
版本变更后需要重新生成 OpenAPI 文档。

## 环境要求

- JDK 25，并使用 Gradle toolchain
- Gradle Wrapper 9.9（仓库自带）
- PostgreSQL 16+（每个服务一个数据库）
- Redis 7+，用于 IAM 会话与缓存
- Nacos 3.1+，用于服务发现与配置
- Docker，用于 IAM Testcontainers 测试

## 快速开始

启动 Nacos：

```bash
cd platform/nacos && docker compose up -d
```

Sentinel Dashboard 与 RocketMQ broker 是可选本地依赖：

```bash
cd platform/sentinel && docker compose up -d
```

```bash
cd platform/rocketmq && docker compose up -d
```

使用本地 PostgreSQL 与 Redis；`platform/pgsql` 与 `platform/redis` 是本地基础设施定义的占位目录。
启动服务前先发布种子配置：

```bash
platform/nacos/config/publish.sh
```

构建项目，先启动 IAM，再启动网关：

```bash
./gradlew build
./gradlew :pudans-iam:bootRun
```

```bash
./gradlew :pudans-gateway:bootRun
```

默认端口见[服务](#服务)。数据库地址、账号、Redis 与 Nacos 地址可通过各服务启动配置中记录的
`PUDANS_*` 环境变量覆盖。

生产部署见 [deploy/index.md](deploy/index.md)：中间件通过 1Panel 应用商店安装，分服务 Docker
Compose 资源位于 `deploy/assets`。

## 配置

业务与运行时配置从 Nacos Config 加载。每个应用导入自己的 dataId：

```yaml
spring:
  config:
    import: optional:nacos:${spring.application.name}.yaml?group=DEFAULT_GROUP&refreshEnabled=true
```

种子文件位于 [platform/nacos/config](platform/nacos/config)：

| 服务 | dataId |
| --- | --- |
| `pudans-gateway` | `pudans-gateway.yaml` |
| `pudans-iam` | `pudans-iam.yaml` |
| `pudans-repository` | `pudans-repository.yaml` |
| `pudans-notification` | `pudans-notification.yaml` |
| `pudans-mq` | `pudans-mq.yaml` |
| `pudans-job` | `pudans-job.yaml` |
| `pudans-search` | `pudans-search.yaml` |

`platform/nacos/config/publish.sh` 会把所有 YAML 与 JSON 文件发布到 `DEFAULT_GROUP`，可重复执行；
可用 `NACOS_ADDR` 或 `NACOS_GROUP` 覆盖默认值。

`@ConfigurationProperties` 配置和网关静态路由 Bean 支持 Nacos 刷新；网关路由 locator 与 IAM
Feign 内部令牌拦截器使用 `@RefreshScope`。datasource、JPA、Liquibase、Redis、SMTP、网关 CORS
与启动时捕获的 JWT 配置变更后需要重启。

如果 dataId 缺失且本地 fallback 不包含业务键，服务可能无法通过启动校验；本地联调应先发布种子
配置。

### 边缘保护与消息传输

Sentinel 规则 dataId 与服务种子放在一起：`pudans-gateway-sentinel-gw-api-group.json`、
`pudans-gateway-sentinel-gw-flow.json`、`pudans-notification-sentinel-flow.json` 与
`pudans-notification-sentinel-degrade.json`。规则 JSON 通过 Nacos 热更新；datasource、transport
与网关过滤器配置需要重启。

消息中心默认使用内存 broker。将 `pudans.messaging.broker` 设为 `rocketmq` 可启用 RocketMQ
provider；宣布 topic 与消费者组由 `pudans.mq.announcement-*` 配置。RocketMQ 只负责传输：
PostgreSQL 仍是消息的权威来源，调度器会从数据库兜底。ACL 凭据通过
`PUDANS_ROCKETMQ_ACCESS_KEY` 与 `PUDANS_ROCKETMQ_SECRET_KEY` 注入。生产环境需预创建宣布 topic
与消费者组，并关闭自动创建。

### 可观测性

自建 Grafana 栈通过 1Panel 运行：Grafana（宿主端口 3300）、Prometheus（9090）与 Jaeger
（16686/4318）。每个服务在独立管理端口上暴露
`health,info,metrics,prometheus`，默认绑定 `PUDANS_MANAGEMENT_ADDRESS`（默认 `127.0.0.1`）：

| 服务 | 管理端口 |
| --- | --- |
| `pudans-gateway` | 9080 |
| `pudans-iam` | 9081 |
| `pudans-repository` | 9091 |
| `pudans-notification` | 9092 |
| `pudans-mq` | 9093 |
| `pudans-job` | 9094 |
| `pudans-search` | 9095 |

原先使用应用端口的健康探针需要改到管理端口。网关保留
`/apis/pudans.tolim.cn/v1alpha1/actuator`，IAM 保留
`/apis/pudans.tolim.cn/v1alpha1/iam/actuator`，其余服务使用 `/actuator`。Prometheus 抓取与
Grafana/Jaeger 数据源示例位于 `platform/observability`，请把 `pudans-host` 与 `1panel-host`
占位符替换为实际局域网地址。将 `PUDANS_OTLP_TRACING_ENDPOINT` 设为 Jaeger 的 OTLP 地址。日志
使用 ECS JSON；将 `PUDANS_LOG_FILE` 设为 JSON 文件路径可保留在主机上，或直接读取容器日志。此栈
不包含集中式日志存储，可使用 1Panel 的容器日志查看器或日志文件。
`pudans-observability.yaml` 设置 `spring.reactor.context-propagation=auto`，WebFlux 日志模块将
`requestId` 注册为线程本地访问器，网关在 Reactor 线程切换后仍能在 MDC 中保留 requestId。下游
服务接收 `X-Request-Id` 与 `traceparent`，并写入自己的 MDC。

当 Prometheus 运行在另一台主机时，把 `PUDANS_MANAGEMENT_ADDRESS` 设为 1Panel 宿主可达的地址，
并用防火墙限制管理端口。管理端点无认证，必须只留在内网。共享可观测性 dataId 为
`pudans-observability.yaml`，通过 `platform/nacos/config/publish.sh` 发布。管理端口、tracing
exporter、结构化日志与 Reactor context-propagation 配置变更后需重启。

## API 文档

OpenAPI 3.0 文档由运行中的服务生成，不手工编写：

```bash
./gradlew :api-docs:generateOpenApiDocs
```

命令会写出 `aggregated.json` 以及 `apis_iam`、`apis_repository`、`apis_notification`、`apis_mq`、
`apis_job`、`apis_search` 的 `.v1alpha1.json` 到 [api-docs/openapi/v3_0](api-docs/openapi/v3_0)。
Operation 标签与摘要为中文。网关没有业务 controller，因此没有独立分组。

不启动服务即可校验生成结果：

```bash
./gradlew :api-docs:validateOpenApiDocs
```

## 数据库变更

所有 Liquibase XML 都位于 [sql](sql)。每个服务有 `changelog-master.xml`；内核变更使用版本化
文件名，并保留原 `logicalFilePath` 以兼容已执行的数据库。所有表和列必须提供中文 `remarks`。
新增或修改变更集前请阅读 [sql/README.md](sql/README.md)。

## 测试

```bash
./gradlew test
```

全量测试使用 JUnit 5；IAM 的 PostgreSQL/Redis 测试使用 Testcontainers。测试会关闭 Nacos 发现与
配置。修改 OpenAPI 注解或 controller 后还要运行：

```bash
./gradlew :api-docs:validateOpenApiDocs
```

## Agent 指南

修改代码、配置、SQL 或文档前，请先阅读 [AGENTS.md](AGENTS.md)。详细规则在
[.agents/rules](.agents/rules)，可执行流程在 [.agents/skills](.agents/skills)，项目事实在
[.agents/notes](.agents/notes)。

## 约定

- 所有服务 API 使用 `/apis/pudans.tolim.cn/v1alpha1/` 前缀；不要新增根 `/api` 别名。
- `pudans-contract` 只放接口、DTO、路径与 Feign 客户端，不放任何实现。
- 服务间调用携带 `pudans.internal.token` 对应的 `X-Pudans-Internal-Token`。
- 只使用 PostgreSQL。变更统一放在根 `sql`，使用 Liquibase；不要把 changelog 放进模块
  `src/main/resources`。
- PostgreSQL 消息中心是消息的真相源；RocketMQ 只是可选传输。
- 所有加密与解密使用 `cn.tolim.spissatus4j`；不要新增自研密码学。
- 不要引入 eiam 支持或 Seata。eiam 仅作为本地参考源码。
- Java 注释使用英文单行 `//`；不要给 SQL、YAML、Gradle、shell 或 Markdown 添加注释。
- Agent 不 push、不创建 PR、不改变远端状态；所有远端操作由用户执行。