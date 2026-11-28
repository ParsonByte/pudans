# pudans 部署文档

本目录只描述生产部署，不参与应用构建。中间件由 1Panel 应用商店安装，pudans 微服务由
`deploy/assets/<service>/docker-compose.yml` 单独编排。网关是唯一对外入口，数据库、Redis、
Nacos、管理端口和可观测性组件都留在内网。

## 文档地图

| 文档类 | 入口 | 内容 |
| --- | --- | --- |
| quick | [quick/quick-start.md](quick/quick-start.md) | 从 1Panel 中间件到七个服务可访问的最短路径 |
| guides | [guides/middleware.md](guides/middleware.md) | 1Panel 中间件、数据库、Nacos、RocketMQ、Sentinel、网络 |
| guides | [guides/services.md](guides/services.md) | 镜像构建、环境变量、分服务 compose、启动顺序、端口 |
| guides | [guides/observability.md](guides/observability.md) | Prometheus、Grafana、Jaeger、日志与告警 |
| guides | [guides/operations.md](guides/operations.md) | 升级、回滚、备份、扩容、安全检查 |
| api | [api/overview.md](api/overview.md) | API 基址、鉴权、响应结构、错误码、限流 |
| api | [api/openapi.md](api/openapi.md) | OpenAPI 文档生成、校验与查看方式 |
| faq | [faq/index.md](faq/index.md) | 部署、注册、数据库、指标、追踪、日志故障排查 |

## 部署拓扑

```text
浏览器 / 调用方
        |
        v
  1Panel OpenResty 或负载均衡
        |
        v
  pudans-gateway :8080
        |
        +--> pudans-iam :8081
        +--> pudans-repository :8091
        +--> pudans-notification :8092
        +--> pudans-mq :8093
        +--> pudans-job :8094
        +--> pudans-search :8095

  1panel-network
        +--> PostgreSQL / Redis / Nacos
        +--> RocketMQ（可选）
        +--> Prometheus / Grafana / Jaeger
```

服务之间通过 Nacos 注册与发现通信；内部调用携带 `X-Pudans-Internal-Token`。Prometheus 通过
各服务的管理端口采集指标，Jaeger 通过 OTLP HTTP 接收追踪，日志以 ECS JSON 写入容器卷或
容器标准输出。

## 服务与端口

| 服务 | 应用端口 | 管理端口 | 健康检查路径 | compose |
| --- | --- | --- | --- | --- |
| gateway | 8080 | 9080 | `/apis/pudans.tolim.cn/v1alpha1/actuator/health` | `assets/gateway` |
| iam | 8081 | 9081 | `/apis/pudans.tolim.cn/v1alpha1/iam/actuator/health` | `assets/iam` |
| repository | 8091 | 9091 | `/actuator/health` | `assets/repository` |
| notification | 8092 | 9092 | `/actuator/health` | `assets/notification` |
| mq | 8093 | 9093 | `/actuator/health` | `assets/mq` |
| job | 8094 | 9094 | `/actuator/health` | `assets/job` |
| search | 8095 | 9095 | `/actuator/health` | `assets/search` |

除网关 8080 外，应用端口不发布到宿主机。管理端口默认只绑定宿主机 `127.0.0.1`，供本机排查；
容器网络内的 Prometheus 使用 `pudans-<service>:<管理端口>` 直接采集。

## 原则

- 中间件不在本仓库中通过 compose 部署；PostgreSQL、Redis、Nacos、RocketMQ、Sentinel、
  Grafana、Prometheus 和 Jaeger 均通过 1Panel 应用商店安装。
- 微服务镜像由 [assets/Dockerfile](assets/Dockerfile) 构建，镜像按服务拆分，compose 按服务放置。
- 密钥、数据库口令、JWT secret、内部 token 只放在 `deploy/assets/.env`，该文件不得提交。
- 数据库结构由各服务启动时的 Liquibase 管理；部署前先备份，升级只做向前迁移。
- 管理端口与中间件端口不得暴露到公网；网关前面的 TLS 由 1Panel OpenResty 或外部负载均衡终止。