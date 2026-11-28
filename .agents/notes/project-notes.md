# Project notes

Last reviewed: 2026-11-28

## Service map

| Service | Port | Primary responsibility |
| --- | --- | --- |
| `pudans-gateway` | 8080 | Public edge, routing, session enforcement, edge rate limiting, internal-token injection |
| `pudans-iam` | 8081 | Login, MFA, sessions, SSO tickets, identity sources, authorization |
| `pudans-repository` | 8091 | File metadata and object-storage providers |
| `pudans-notification` | 8092 | Email, SMS, Feishu and DingTalk delivery, templates, retries, dead letters |
| `pudans-mq` | 8093 | Message center, deliveries and dead letters; optional RocketMQ announcement transport |
| `pudans-job` | 8094 | Job definitions, executions and executor APIs |
| `pudans-search` | 8095 | Search indexes, documents and queries |

All public APIs use `/apis/pudans.tolim.cn/v1alpha1/`. Internal calls carry
`X-Pudans-Internal-Token`.

## Versioning

Service and shared versions are defined only in the root `VERSION` file. Gradle reads the file at
configuration time: each application and its owned contract/kernel modules use the service key;
`SHARED.VERSION` covers shared libraries and api-docs. Do not hardcode a service version in a build
file. Regenerate OpenAPI after changing a version.

## Nacos configuration

Each application imports `optional:nacos:<spring.application.name>.yaml?group=DEFAULT_GROUP&refreshEnabled=true`.
The seed files are in `platform/nacos/config`; publish them with
`platform/nacos/config/publish.sh` before starting a local service. The script publishes YAML and
JSON; Sentinel rule dataIds are JSON. All applications also import the shared
`pudans-observability.yaml` dataId for metrics, tracing and logging defaults.

Tests disable Nacos discovery and config. The dataIds are the application names:
`pudans-gateway.yaml`, `pudans-iam.yaml`, `pudans-repository.yaml`,
`pudans-notification.yaml`, `pudans-mq.yaml`, `pudans-job.yaml` and `pudans-search.yaml`.

`@ConfigurationProperties` values and the gateway static route bean refresh from Nacos. Datasource,
JPA, Liquibase, Redis, SMTP, gateway CORS/trusted-proxies and startup-captured JWT values require a
restart after a change.

## Sentinel

The gateway uses Sentinel 1.8.9 from the Spring Cloud Alibaba BOM. `spring.cloud.sentinel.scg.order`
is -2147483647 so authentication runs first; the WebFlux URL filter is disabled so only gateway
resources are counted. Rule dataIds: `pudans-gateway-sentinel-gw-api-group.json` and
`pudans-gateway-sentinel-gw-flow.json`.

`pudans-notification` protects the send endpoints by URL resource with
`pudans-notification-sentinel-flow.json` and `pudans-notification-sentinel-degrade.json`; its block
handler returns HTTP 429 with the communal `ApiResponse` shape. Sentinel client API ports: gateway
8719, notification 8720. The dashboard compose is `platform/sentinel`, pinned by digest.

Rule JSON hot-reloads from Nacos; datasource, transport and gateway filter settings require a
restart. Nacos must be running for rule loading.

## RocketMQ

The message center owns message and delivery state in PostgreSQL; RocketMQ is an optional
announcement transport. The default is `pudans.messaging.broker=in-memory`; set it to `rocketmq`
and configure `pudans.messaging.rocketmq.*`. The provider is
`pudans-communal/pudans-communal-mq-rocketmq`; `pudans-mq` depends on it.

Announcement topic `pudans-message-announcement`, consumer group `pudans-mq-announcement`,
configurable through `pudans.mq.announcement-*`. The envelope id is the message key; the business
topic travels in `pudans_topic` because RocketMQ tags reject dots and colons. The subscriber
dispatches by id and the database claim keeps duplicates safe; the scheduler remains the safety
net.

Local infrastructure is `platform/rocketmq` (`apache/rocketmq:5.3.1`, `brokerIP1=127.0.0.1`,
auto-create enabled). Production must pre-create the topic and consumer group and disable
auto-create. ACL is optional: set `pudans.messaging.rocketmq.access-key`/`secret-key` or the
`PUDANS_ROCKETMQ_ACCESS_KEY`/`PUDANS_ROCKETMQ_SECRET_KEY` environment variables and enable ACL on
the broker. Send is synchronous with a 3s default timeout; the scheduler still reconciles.

## Observability

The self-hosted Grafana stack runs from 1Panel: Grafana (3300), Prometheus (9090) and Jaeger
(16686/4318). The repo stores importable Prometheus and Grafana assets under
`platform/observability`; it does not run the stack.

Every service exposes `health,info,metrics,prometheus` on a dedicated management port:
gateway 9080, IAM 9081, repository 9091, notification 9092, mq 9093, job 9094, search 9095.
The management address defaults to 127.0.0.1; set `PUDANS_MANAGEMENT_ADDRESS` to an address
reachable from the 1Panel host and firewall the ports when Prometheus runs elsewhere. Existing
health probes must move from the application port to the management port. The gateway and IAM
keep their prefixed management base paths.

Shared configuration is the `pudans-observability.yaml` dataId; publish it with the seed script.
Metrics use the Prometheus registry; OTLP metrics and OTLP log export are disabled. Traces go to
Jaeger through OTLP HTTP at `PUDANS_OTLP_TRACING_ENDPOINT`. Logs are ECS JSON written to
`PUDANS_LOG_FILE` when configured, otherwise to the console and container log stream; the stack
has no centralized log store. Gateway logs carry traceId/spanId through Micrometer; the requestId
is registered as a Reactor thread-local accessor by the WebFlux log module, and
`spring.reactor.context-propagation=auto` restores it across operator thread switches.
Downstream services receive `X-Request-Id` and `traceparent` and write them to their own MDC. The
sample configs use `pudans-host` and `1panel-host` placeholders that must be replaced with LAN
addresses.

Management port, tracing exporter, structured logging and Reactor context-propagation changes
require a restart. Management endpoints are unauthenticated and internal-only; do not expose
Grafana/Prometheus/Jaeger publicly.

## Deployment

Production deployment is documented in `deploy/index.md`. Middleware (PostgreSQL, Redis, Nacos,
RocketMQ, Sentinel, Prometheus, Grafana, Jaeger) is installed from the 1Panel app store; do not add
middleware compose files to the repository. Business services use per-service compose files under
`deploy/assets/<service>` and a shared `deploy/assets/Dockerfile`; secrets stay in the untracked
`deploy/assets/.env`.

## OpenAPI

Generate with:

```bash
./gradlew :api-docs:generateOpenApiDocs
```

The generator starts the six business applications with the `doc` profile on ports 18091-18096,
fetches `/v3/api-docs/<group>`, writes `api-docs/openapi/v3_0/apis_<service>.v1alpha1.json`, and
merges `aggregated.json`. The gateway has no business controller and no separate group.

Validate with:

```bash
./gradlew :api-docs:validateOpenApiDocs
```

The operation tags and summaries are Chinese. Regenerate after changing controllers or OpenAPI
annotations; never hand-edit the JSON.

## Frontend

`pudans-web` is an npm workspace. `pudans-web/packages/api-client` is generated from
`api-docs/openapi/v3_0/aggregated.json` with OpenAPI Generator (`typescript-axios`); regenerate it
with `npm run api-client:gen` from `pudans-web`. Chinese OpenAPI tags map to English API class
names through `pudans-web/packages/api-client/openapi-tags.json`, so add new tags there before
regenerating.

## SQL

Every changelog is under `sql/<service>/`; each service has `changelog-master.xml`. Kernel DDL
files use versioned names and keep their original `logicalFilePath` for database compatibility.
All tables and columns carry Chinese `remarks`. See `sql/README.md`.

## IAM notifications

IAM composes mail and SMS content and delegates delivery to `pudans-notification` through
`NotificationClient`. The notification service must have an enabled email or SMS provider; IAM's
old provider settings no longer drive delivery. Historical `mail_send_record` and
`sms_send_record` tables remain but are no longer written.

## Tests

```bash
./gradlew test
```

The suite uses JUnit 5; IAM Testcontainers tests require Docker. The current suite has 1022 tests
with no failures, errors or skips in a Docker-capable environment. Nacos is disabled in tests.

## Operational cautions

- Do not rebuild `bootJar` while a local backend is running; use a copied jar for local runs.
- Do not stop or replace the user's port 3000 development server.
- Never push or open a pull request; the user performs all remote operations.
- Production notification delivery depends on providers configured in the notification service.
- Sentinel rule JSON hot-reloads from Nacos; datasource, transport and gateway filter changes need a
  restart.
- RocketMQ local auto-creation is for development only; production must pre-create the announcement
  topic and consumer group and enable ACL where required.
- Management ports expose metrics and must stay on the internal network; do not route them through
  the gateway or expose Grafana/Prometheus/Jaeger publicly.