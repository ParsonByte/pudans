# 微服务镜像与 Compose

`deploy/assets` 按服务拆分，每个目录只负责一个进程：

```text
deploy/assets
├── Dockerfile
├── .env.example
├── gateway/docker-compose.yml
├── iam/docker-compose.yml
├── repository/docker-compose.yml
├── notification/docker-compose.yml
├── mq/docker-compose.yml
├── job/docker-compose.yml
└── search/docker-compose.yml
```

所有 compose 共享同一个外部 Docker 网络和同一份 `.env`。容器之间通过容器名和 1Panel 网络通信，
不依赖宿主机端口。

## 镜像构建

共享 [Dockerfile](../assets/Dockerfile) 使用两阶段构建：

- `gradle:9.9.0-jdk25` 阶段执行 `:pudans-<service>:clean bootJar`。
- `eclipse-temurin:25-jre` 阶段只保留 bootJar，以非 root 用户 `pudans` 运行。

构建上下文是仓库根目录，`.dockerignore` 排除了 `.git`、`.gradle` 和各模块的 `build` 目录。示例：

```bash
docker build -f deploy/assets/Dockerfile --build-arg SERVICE=iam -t pudans/iam:local .
```

七个服务可以逐个构建：

```bash
for service in gateway iam repository notification mq job search; do
  docker build -f deploy/assets/Dockerfile --build-arg SERVICE="$service" -t "pudans/$service:local" .
done
```

镜像标签不要与根 `VERSION` 脱节。发布时从 `VERSION` 读取服务版本，例如：

```bash
awk -F= '/^IAM.VERSION=/{print $2}' VERSION
```

把得到的版本作为 `PUDANS_TAG`，并推送到镜像仓库：

```bash
docker tag pudans/iam:local registry.example.com/pudans/iam:0.1.0
docker push registry.example.com/pudans/iam:0.1.0
```

compose 默认使用 `pudans/<service>:local`。生产环境在 `.env` 中设置：

```text
PUDANS_REGISTRY=registry.example.com/pudans
PUDANS_TAG=0.1.0
```

`PUDANS_REGISTRY` 不要带结尾斜杠。私网仓库需要先在部署主机执行 `docker login`。

如果部署主机无法访问 Docker Hub，先在 1Panel 的容器镜像加速或私有仓库配置 mirror，再把
Dockerfile 的 `FROM` 改成内网镜像；否则构建会卡在拉取 `gradle` 或 `eclipse-temurin` 基础镜像。

## 环境变量

复制模板：

```bash
cp deploy/assets/.env.example deploy/assets/.env
```

`.env` 已被 `.gitignore` 忽略，只提交 `.env.example`。`.env` 同时供 compose 变量替换和容器环境
使用。每个 compose 都通过 `env_file: ../.env` 注入，并在启动命令中使用 `--env-file ../.env`，
两者必须指向同一份文件：

```bash
cd deploy/assets/iam
docker compose --env-file ../.env up -d
```

核心变量：

| 变量 | 作用 |
| --- | --- |
| `PUDANS_REGISTRY` / `PUDANS_TAG` | 镜像仓库与标签 |
| `PUDANS_DOCKER_NETWORK` | 外部网络名，默认 `1panel-network` |
| `PUDANS_NACOS_ADDR` | Nacos 地址，容器网络内通常 `nacos:8848` |
| `PUDANS_DB_HOST` / `PUDANS_DB_PORT` | PostgreSQL 地址与端口 |
| `PUDANS_DB_USER` / `PUDANS_DB_PASSWORD` | 六个业务库的共同账号 |
| `PUDANS_REDIS_HOST` / `PUDANS_REDIS_PORT` | IAM Redis 地址 |
| `SPRING_DATA_REDIS_PASSWORD` | 仅当 Redis 有口令时加入 `.env`，不要留空值 |
| `PUDANS_INTERNAL_TOKEN` | 服务间 `X-Pudans-Internal-Token`，所有服务一致 |
| `PUDANS_JWT_SECRET` | 网关与 IAM 共用的 JWT 密钥 |
| `PUDANS_HOME_ORIGIN` | IAM 允许的前端 Origin |
| `PUDANS_CORS_ALLOWED_ORIGINS` | 网关 CORS 白名单，逗号分隔 |
| `PUDANS_COOKIE_SECURE` | HTTPS 部署设为 `true` |
| `PUDANS_COOKIE_DOMAIN` | 设为应用共同父域以共享登录态，例如 `.example.com` |
| `PUDANS_MANAGEMENT_BIND` | 管理端口在宿主机上的绑定地址，默认 `127.0.0.1` |
| `PUDANS_OTLP_TRACING_ENDPOINT` | Jaeger OTLP HTTP 地址 |
| `PUDANS_TRACING_EXPORT_ENABLED` | 没有 Jaeger 时设为 `false` |
| `PUDANS_LOG_FILE` | 容器内 ECS JSON 日志路径，由 compose 固定 |
| `PUDANS_JAVA_OPTS` | JVM 参数，默认 `-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC` |

不要提交 `.env`。生产密钥建议用 `openssl rand -hex 32` 生成，并由 1Panel 或部署平台保管。

## Compose 约定

每个服务 compose 遵循同一结构：

- `image` 使用 `PUDANS_REGISTRY` 与 `PUDANS_TAG`，`build` 作为本地构建回退。
- `env_file` 指向 `../.env`。
- `networks.pudans` 是 `external: true`，名称来自 `PUDANS_DOCKER_NETWORK`。
- `PUDANS_MANAGEMENT_ADDRESS=0.0.0.0`，让同一网络中的 Prometheus 可以采集。
- 管理端口发布到 `${PUDANS_MANAGEMENT_BIND:-127.0.0.1}`，默认只允许宿主机访问。
- 日志驱动是 `json-file`，单文件 20 MB，保留 5 个文件；业务日志另写容器卷。
- 每个服务使用独立容器名 `pudans-<service>`，便于 `docker logs` 和故障定位。

网关额外发布 `8080`：

```text
${PUDANS_GATEWAY_BIND:-0.0.0.0}:${PUDANS_GATEWAY_PORT:-8080}:8080
```

除网关外，应用端口不映射到宿主机。不要在 compose 中为 IAM、repository、notification、mq、job、
search 增加应用端口映射；调用方应通过网关访问。

## 启动顺序

先确认 Nacos、PostgreSQL、Redis 和可选中间件已经运行，再按以下顺序启动业务服务：

```bash
for service in repository notification mq job search iam gateway; do
  (cd "deploy/assets/$service" && docker compose --env-file ../.env up -d)
done
```

repository、notification、mq、job、search 彼此没有启动依赖；iam 依赖 PostgreSQL 与 Redis；gateway
依赖 Nacos 发现和 IAM 的会话校验接口。网关最后启动可以减少启动阶段的 503。

如果镜像已在仓库中，`up -d` 会直接拉取或使用本地镜像；需要重新构建时加 `--build`。也可以只重建
单个服务：

```bash
cd deploy/assets/iam
docker compose --env-file ../.env up -d --build
```

## 健康检查

| 服务 | 管理端口 | 路径 |
| --- | --- | --- |
| gateway | 9080 | `/apis/pudans.tolim.cn/v1alpha1/actuator/health` |
| iam | 9081 | `/apis/pudans.tolim.cn/v1alpha1/iam/actuator/health` |
| repository | 9091 | `/actuator/health` |
| notification | 9092 | `/actuator/health` |
| mq | 9093 | `/actuator/health` |
| job | 9094 | `/actuator/health` |
| search | 9095 | `/actuator/health` |

本机验证：

```bash
curl -fsS http://127.0.0.1:9081/apis/pudans.tolim.cn/v1alpha1/iam/actuator/health
```

管理端口不是业务端口。不要用 `curl http://容器IP:8081/actuator/health` 判断服务状态；IAM 和网关
在应用端口上不提供 actuator，其他服务的应用端口也不暴露 actuator。

容器网络内的 Prometheus 直接访问：

```text
pudans-gateway:9080
pudans-iam:9081
pudans-repository:9091
pudans-notification:9092
pudans-mq:9093
pudans-job:9094
pudans-search:9095
```

如果 Prometheus 运行在 1Panel 主机而不是同一 Docker 网络，把 `PUDANS_MANAGEMENT_BIND` 改成主机
内网地址并限制防火墙；不要设置为 `0.0.0.0` 后直接暴露到公网。

## 数据与卷

| 卷 | 服务 | 内容 |
| --- | --- | --- |
| `<service>-logs` | 全部 | `/var/log/pudans` 下的 ECS JSON |
| `repository-data` | repository | 本地对象存储目录 `/var/lib/pudans/repository` |
| `repository-keys` | repository | spissatus4j 文件密钥目录 `/var/lib/pudans/keys` |

repository 使用本地存储时，`repository-data` 必须备份。生产环境如果切换到阿里云 OSS、腾讯 COS、
MinIO、七牛或 S3，按 `platform/nacos/config/pudans-repository.yaml` 中的 provider 配置注入凭据，并
把 `PUDANS_REPOSITORY_DEFAULT_PROVIDER` 改成对应 provider。

repository 默认 `SPISSATUS_KEY_PROVIDER=memory`，重启后密钥会丢失，已加密的文件元数据可能无法
解密。生产环境应改为文件或 keystore provider，并把密钥目录放在持久卷中；密钥口令通过
`SPISSATUS_KEY_MASTER_PASSWORD` / `SPISSATUS_KEYSTORE_PASSWORD` 注入。

## 资源与扩容

compose 给 JVM 设置了 `MaxRAMPercentage=75.0`。如果要限制容器内存，在 compose 中加入
`mem_limit` 或平台对应的资源限制，并同步调整 `PUDANS_JAVA_OPTS`。不要同时把 JVM 堆设成固定大值
和很低的容器内存上限。

当前每个 compose 固定一个 `container_name`，适合单实例部署。需要多副本时，复制服务目录并修改
项目名、容器名和管理端口映射，或改用编排平台；多个实例应注册到同一个 Nacos 服务名，网关通过
Spring Cloud LoadBalancer 轮询。

## 更新与安全检查

更新前先读 [operations.md](operations.md)。生产上线前至少检查：

- 只有网关 8080 对外，9080/9081/9091-9095 只在内网。
- PostgreSQL、Redis、Nacos、RocketMQ 没有公网端口。
- `.env` 不在版本库中，`PUDANS_INTERNAL_TOKEN`、`PUDANS_JWT_SECRET` 和数据库口令都不是示例值；
  Redis 有口令时只添加 `SPRING_DATA_REDIS_PASSWORD`，不要添加空值。
- HTTPS 由 1Panel OpenResty 或外部负载均衡终止，`PUDANS_COOKIE_SECURE=true`。
- 仓库的 `X-Pudans-Internal-Token` 只允许网关和内部服务调用；外部调用方不能拿到该 token。
- 已配置数据库备份、repository 数据卷备份和 Nacos 配置导出。