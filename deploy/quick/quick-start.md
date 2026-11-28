# 快速开始

目标是在一台已经运行 1Panel 的 Linux 主机上，用 1Panel 安装中间件，用仓库内的 compose 启动七个
业务服务，最后通过网关访问 API。下面命令默认在仓库根目录执行。

## 前置条件

- 1Panel 已安装，Docker 与 Docker Compose v2 可用。
- 主机至少有 4 核 8 GB 内存；七个 Java 服务同时运行时建议 8 核 16 GB。
- 一个可被浏览器和服务容器访问的域名或 IP。
- 仓库代码已放到部署主机，包含 `deploy/assets`、`platform/nacos/config`、`VERSION`。
- 如果使用外部镜像仓库，先在部署主机执行 `docker login`。

## 1. 在 1Panel 安装中间件

在 1Panel 的“应用商店”中安装以下应用，并记录容器名与端口：

| 应用 | 建议版本 | 用途 | 默认容器名示例 |
| --- | --- | --- | --- |
| PostgreSQL | 16+ | 六个业务数据库 | `postgresql` |
| Redis | 7+ | IAM 会话与缓存 | `redis` |
| Nacos | 3.1+ | 服务发现与配置 | `nacos` |
| RocketMQ | 5.3+ | 可选，消息中心宣布传输 | `rocketmq-namesrv`、`rocketmq-broker` |
| Sentinel Dashboard | 1.8+ | 可选，流控规则看板 | `sentinel` |
| Prometheus | 3.x | 指标采集 | `prometheus` |
| Grafana | 13.x | 指标看板 | `grafana` |
| Jaeger | 1.7x | 追踪查询 | `jaeger` |

中间件不通过本仓库的 compose 启动。1Panel 安装完成后，确认容器都在同一个 Docker 网络，默认是
`1panel-network`：

```bash
docker network ls
```

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
```

如果实际网络名不是 `1panel-network`，后面在 `.env` 中设置 `PUDANS_DOCKER_NETWORK`。

## 2. 创建数据库

在 1Panel 的 PostgreSQL 终端中执行。把示例口令替换为随机强口令：

```sql
CREATE ROLE pudans LOGIN PASSWORD 'replace-with-a-strong-password';
CREATE DATABASE pudans_iam OWNER pudans;
CREATE DATABASE pudans_repository OWNER pudans;
CREATE DATABASE pudans_notification OWNER pudans;
CREATE DATABASE pudans_mq OWNER pudans;
CREATE DATABASE pudans_job OWNER pudans;
CREATE DATABASE pudans_search OWNER pudans;
```

网关不连接数据库。六个服务的 Liquibase changelog 会在首次启动时自动建表。

## 3. 发布 Nacos 配置

从仓库根目录发布 `platform/nacos/config` 下的种子配置：

```bash
NACOS_ADDR=http://127.0.0.1:8848 platform/nacos/config/publish.sh
```

把 `127.0.0.1` 换成 1Panel 主机地址或 Nacos 容器名可达地址。脚本发布到 `DEFAULT_GROUP` 和
`public` 命名空间；服务端默认也从这里读取。

如果 Nacos 启用了鉴权，发布脚本不会携带登录凭据。可以在内网暂时关闭鉴权，或通过 Nacos 控制台
逐个导入同目录下的 YAML/JSON 文件。配置发布完成后再启动服务。

## 4. 准备环境变量

```bash
cd deploy/assets
cp .env.example .env
```

编辑 `.env`，至少设置以下值：

| 变量 | 说明 |
| --- | --- |
| `PUDANS_NACOS_ADDR` | Nacos 地址，容器网络内通常是 `nacos:8848` |
| `PUDANS_DB_HOST` / `PUDANS_DB_PORT` | PostgreSQL 容器名或地址、端口 |
| `PUDANS_DB_USER` / `PUDANS_DB_PASSWORD` | 第 2 步创建的账号与口令 |
| `PUDANS_REDIS_HOST` | Redis 地址 |
| `SPRING_DATA_REDIS_PASSWORD` | 仅当 Redis 有口令时加入，不要留空值 |
| `PUDANS_INTERNAL_TOKEN` | 服务间内部 token，所有服务必须一致 |
| `PUDANS_JWT_SECRET` | 网关与 IAM 共用的 JWT 签名密钥 |
| `PUDANS_HOME_ORIGIN` / `PUDANS_CORS_ALLOWED_ORIGINS` | 前端站点地址 |
| `PUDANS_COOKIE_SECURE` | HTTPS 部署设为 `true` |
| `PUDANS_COOKIE_DOMAIN` | 设为应用共同父域以共享登录态，例如 `.example.com` |

生成随机密钥：

```bash
openssl rand -hex 32
```

`.env` 含敏感信息，不要提交到仓库。镜像默认使用 `pudans/<service>:local`；生产环境把
`PUDANS_REGISTRY` 和 `PUDANS_TAG` 改成实际镜像仓库和发布标签。

## 5. 构建镜像

仓库根目录执行。共享 Dockerfile 会在 Gradle 构建阶段生成对应服务的 bootJar：

```bash
for service in gateway iam repository notification mq job search; do
  docker build -f deploy/assets/Dockerfile --build-arg SERVICE="$service" -t "pudans/$service:local" .
done
```

首次构建需要下载 Gradle 和 Maven 依赖，耗时较长。构建完成后可以推送到镜像仓库，再把
`.env` 中的 `PUDANS_REGISTRY`、`PUDANS_TAG` 指向该镜像。

## 6. 启动服务

按依赖顺序启动；数据库迁移在服务启动时完成：

```bash
for service in repository notification mq job search iam gateway; do
  (cd "deploy/assets/$service" && docker compose --env-file ../.env up -d)
done
```

`--env-file ../.env` 同时用于 compose 变量替换和容器环境。每个服务目录中的 `env_file` 也指向
同一份 `.env`。

## 7. 验证

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

```bash
curl -fsS http://127.0.0.1:9080/apis/pudans.tolim.cn/v1alpha1/actuator/health
```

```bash
curl -fsS http://127.0.0.1:9081/apis/pudans.tolim.cn/v1alpha1/iam/actuator/health
```

```bash
curl -i http://127.0.0.1:8080/apis/pudans.tolim.cn/v1alpha1/iam/auth/login/config
```

前两个请求返回 `"status":"UP"`，第三个请求由网关转发到 IAM 并返回登录配置。再检查 Nacos 控制台
的服务列表，应看到 `pudans-gateway`、`pudans-iam`、`pudans-repository`、
`pudans-notification`、`pudans-mq`、`pudans-job`、`pudans-search` 七个服务。

## 下一步

- 中间件细节：[guides/middleware.md](../guides/middleware.md)
- 镜像、compose 与启动顺序：[guides/services.md](../guides/services.md)
- Prometheus、Grafana、Jaeger：[guides/observability.md](../guides/observability.md)
- 常见故障：[faq/index.md](../faq/index.md)