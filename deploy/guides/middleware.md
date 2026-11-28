# 1Panel 中间件

本项目的中间件不在 `deploy/assets` 中编排。PostgreSQL、Redis、Nacos、RocketMQ、Sentinel、
Prometheus、Grafana 和 Jaeger 统一由 1Panel 应用商店安装；`deploy/assets` 只放业务服务。

## 网络

1Panel 应用商店安装的容器通常接入 `1panel-network`。微服务 compose 使用外部网络
`${PUDANS_DOCKER_NETWORK:-1panel-network}`，因此业务容器和中间件容器可以直接用容器名互访。

```bash
docker network ls
```

```bash
docker network inspect 1panel-network
```

如果 1Panel 使用了不同的网络名，在 `deploy/assets/.env` 中设置：

```text
PUDANS_DOCKER_NETWORK=实际网络名
```

容器名不是稳定的接口。部署前用 `docker ps` 记录实际名称，并据此设置
`PUDANS_NACOS_ADDR`、`PUDANS_DB_HOST`、`PUDANS_REDIS_HOST`、`PUDANS_ROCKETMQ_NAME_SERVER`、
`PUDANS_OTLP_TRACING_ENDPOINT` 和 `PUDANS_SENTINEL_DASHBOARD`。

## PostgreSQL

在 1Panel 应用商店安装 PostgreSQL 16 或更高版本。为六个有数据库的服务创建独立数据库，不要
让多个服务共用一个数据库：

```sql
CREATE ROLE pudans LOGIN PASSWORD 'replace-with-a-strong-password';
CREATE DATABASE pudans_iam OWNER pudans;
CREATE DATABASE pudans_repository OWNER pudans;
CREATE DATABASE pudans_notification OWNER pudans;
CREATE DATABASE pudans_mq OWNER pudans;
CREATE DATABASE pudans_job OWNER pudans;
CREATE DATABASE pudans_search OWNER pudans;
```

网关没有数据库。各服务启动时由 Liquibase 执行 `sql/<service>/changelog-master.xml`，所以数据库
必须预先存在，账号必须拥有建表、索引和修改表的权限。

在 `.env` 中设置：

```text
PUDANS_DB_HOST=postgresql
PUDANS_DB_PORT=5432
PUDANS_DB_USER=pudans
PUDANS_DB_PASSWORD=与 SQL 中一致的口令
```

如果 1Panel 的 PostgreSQL 容器名不是 `postgresql`，把 `PUDANS_DB_HOST` 改成实际容器名或内网地址。
数据库不要发布到公网；只允许 `1panel-network` 和 1Panel 主机访问。

## Redis

IAM 使用 Redis 保存登录会话和缓存。1Panel 的 Redis 如果设置了密码，在 `.env` 中新增标准 Spring
属性（不要在无口令时保留空值）：

```text
SPRING_DATA_REDIS_PASSWORD=实际密码
```

也可以把 `spring.data.redis.password` 直接写进 Nacos 的 `pudans-iam.yaml`。无口令时不要设置该
属性，否则 Redisson 会发送空 `AUTH`，Redis 返回 `ERR AUTH <password> called without any password
configured`，IAM 启动失败。

Redis 不是业务数据库，不需要为每个服务建库。IAM 使用默认数据库 0；如果需要隔离多个环境，在
1Panel 中为不同环境使用不同的 Redis 实例，而不是只换 key 前缀。

## Nacos

在 1Panel 应用商店安装 Nacos 3.1 或更高版本。Nacos 3.x 除 8848 外还需要 9848/9849 的 gRPC
端口在容器网络内可达；1Panel 应用模板通常已经处理，部署后用 `docker inspect` 或控制台确认。

每个服务导入：

```text
optional:nacos:<spring.application.name>.yaml?group=DEFAULT_GROUP&refreshEnabled=true
optional:nacos:pudans-observability.yaml?group=DEFAULT_GROUP&refreshEnabled=false
```

服务端默认使用 `DEFAULT_GROUP`、`public` 命名空间。如果使用自定义命名空间，需要在各服务的
`application.yml` 或启动参数中增加 `spring.cloud.nacos.config.namespace`，并同步修改
`platform/nacos/config/publish.sh` 的发布目标；当前种子配置没有配置 namespace。

发布种子配置：

```bash
NACOS_ADDR=http://127.0.0.1:8848 platform/nacos/config/publish.sh
```

需要发布的 dataId：

| dataId | 内容 |
| --- | --- |
| `pudans-gateway.yaml` | 网关 Sentinel、会话、前端地址 |
| `pudans-iam.yaml` | IAM 数据库、Redis、JWT、会话、内部 token |
| `pudans-repository.yaml` | 文件元数据、存储 provider、加密密钥 |
| `pudans-notification.yaml` | 通知数据库、邮件、Sentinel |
| `pudans-mq.yaml` | 消息中心数据库、RocketMQ 传输 |
| `pudans-job.yaml` | 任务数据库、执行器 |
| `pudans-search.yaml` | 搜索数据库、索引 |
| `pudans-observability.yaml` | 指标、追踪、日志、Reactor 上下文传播 |
| `pudans-*-sentinel-*.json` | Sentinel 流控、熔断、网关规则 |

`publish.sh` 使用 Nacos v1 配置接口，不携带登录凭据。生产环境如果开启 Nacos 鉴权，可以通过
控制台导入文件，或在内网关闭鉴权后再执行脚本。Nacos 不应暴露到公网。

配置变更的生效方式不同：

- Sentinel 规则 JSON 从 Nacos 热更新，不需要重启。
- 网关静态路由和 `@ConfigurationProperties` 可刷新。
- 数据源、JPA、Liquibase、Redis、邮件、管理端口、追踪 exporter、结构化日志和 Reactor
  context-propagation 需要重启服务。

Nacos 不可用时，`optional:nacos:` 会让服务继续启动并回退到 `application.yml` 的基础配置，但会
缺少业务配置。生产环境必须把 Nacos 的可用性纳入监控。

## RocketMQ（可选）

消息中心默认使用 `in-memory` broker，生产环境建议安装 RocketMQ 5.3 或更高版本并切换为
`rocketmq`。在 1Panel 安装后设置：

```text
PUDANS_MESSAGING_BROKER=rocketmq
PUDANS_ROCKETMQ_NAME_SERVER=rocketmq-namesrv:9876
PUDANS_ROCKETMQ_ACCESS_KEY=
PUDANS_ROCKETMQ_SECRET_KEY=
```

使用 ACL 时填写 access key/secret key，并在 broker 开启 ACL。公告 topic 和消费者组默认是
`pudans-message-announcement` 与 `pudans-mq-announcement`。生产环境要预创建 topic 与消费者组，
并关闭 broker 自动创建。

`brokerIP1` 必须是一个业务容器可达的地址，不能是 broker 容器内的 `127.0.0.1`。如果 1Panel 的
RocketMQ 模板把 `brokerIP1` 留成回环地址，客户端会在 NameServer 查询成功后连接失败；改成
1Panel 主机内网地址或稳定的容器网络地址，并确认 10909/10911 在 `1panel-network` 内可达。

## Sentinel（可选）

网关与通知服务可以连接 Sentinel Dashboard。在 1Panel 安装 Sentinel 后设置：

```text
PUDANS_SENTINEL_DASHBOARD=sentinel:8858
```

网关的 Sentinel 客户端 API 端口默认 8719，通知服务默认 8720；这些端口只在容器内部使用，不需要
发布到宿主机。没有安装 Sentinel 时，服务仍可启动，但 Dashboard 不会显示实时规则和簇点。

## 可观测性中间件

Prometheus、Grafana 和 Jaeger 同样通过 1Panel 安装，配置方法见
[observability.md](observability.md)。它们需要访问各服务的管理端口，不能访问应用端口来代替。

## 端口与暴露面

| 端口 | 是否对外 | 说明 |
| --- | --- | --- |
| 8080 | 是 | 网关，唯一业务入口 |
| 9080、9081、9091-9095 | 否 | 管理端口，默认只绑定宿主机 `127.0.0.1` |
| 5432、6379、8848、9848、9849 | 否 | PostgreSQL、Redis、Nacos |
| 9876、10909、10911 | 否 | RocketMQ NameServer 与 Broker（如启用） |
| 8858 | 否 | Sentinel Dashboard（如启用） |
| 9090、3300、16686、4318 | 否 | Prometheus、Grafana、Jaeger |

管理端口和中间件端口不得通过 1Panel 反向代理或公网防火墙暴露。