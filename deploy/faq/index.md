# 部署 FAQ

## 服务启动了，但 Nacos 看不到实例

先确认服务容器和 Nacos 在同一网络：

```bash
docker network inspect 1panel-network
```

```bash
docker exec pudans-iam getent hosts nacos
```

检查 `.env` 中的 `PUDANS_NACOS_ADDR` 是否是业务容器可达的地址，例如 `nacos:8848`，而不是
`127.0.0.1:8848`。再看 IAM 日志中是否有 Nacos 注册失败或 gRPC 端口不可达的错误。Nacos 3.x 需要
9848/9849 端口在容器网络内可达。

网关路由使用 `lb://pudans-iam`。如果 IAM 没有注册，网关返回 503 或连接失败，而不是 Nacos 配置
错误。

## Nacos 配置没有生效

确认四件事：

- dataId 是 `pudans-<service>.yaml`，不是服务名之外的名称。
- group 是 `DEFAULT_GROUP`，namespace 是 `public`。
- 发布脚本输出 `Published ...`，且 Nacos 控制台能看到配置内容。
- 服务日志中 `spring.config.import` 没有因为 Nacos 不可达而回退到本地配置。

`optional:nacos:` 的语义是 Nacos 不可用时继续启动，所以配置缺失不一定让服务启动失败。检查
Nacos 控制台和启动日志，不要只看容器状态。

修改数据源、Redis、邮件、管理端口、追踪 exporter、结构化日志或 Reactor context-propagation 后
需要重启对应服务；Sentinel 规则 JSON 和部分 `@ConfigurationProperties` 可以热更新。

## 数据库连接失败或提示 database does not exist

```bash
docker exec pudans-iam getent hosts postgresql
```

确认 `.env` 的 `PUDANS_DB_HOST` 是容器名或内网地址，不是容器内的 `127.0.0.1`。确认六个数据库已经
创建，且 `pudans` 角色有连接和建表权限。网关不需要数据库；不要给网关配置 `PUDANS_DB_URL`。

## Liquibase 校验失败

常见原因是数据库结构被手工修改，或部署了不同版本的 changelog。不要修改已发布的 changelog 来
绕过 checksum；应恢复数据库备份，或新增版本化 changelog 做向前修正。

首次部署请使用空数据库，并确认 `sql/<service>/changelog-master.xml` 在镜像中。Liquibase 失败
时服务不会完成启动，先看日志中的第一条 `ValidationFailedException` 或 SQL 错误。

## Redis 提示 NOAUTH 或连接失败

如果 Redis 有口令，在 `.env` 中新增 `SPRING_DATA_REDIS_PASSWORD`（不要留空值），并确认
`PUDANS_REDIS_HOST` 指向 1Panel 的 Redis 容器。无口令时不要设置该属性，否则 Redisson 会发送空
`AUTH` 并导致 IAM 启动失败。

```bash
docker exec pudans-iam getent hosts redis
```

Redis 有 ACL 用户时，当前配置只支持默认用户加密码；如果需要自定义用户名，需要扩展
`spring.data.redis.username` 配置后再部署。

## 网关返回 401、302 或登录循环

依次检查：

- IAM 是否已注册到 Nacos，网关的 `session/validate` 是否能访问 IAM。
- 浏览器是否带着 `PUDANS_SID` Cookie。
- HTTPS 下 `PUDANS_COOKIE_SECURE` 是否为 `true`；HTTP 调试时为 `false`。
- `PUDANS_HOME_ORIGIN` 和 `PUDANS_CORS_ALLOWED_ORIGINS` 是否包含实际前端域名。
- 1Panel OpenResty 是否传递了 `Host`、`X-Forwarded-*`；网关的 `PUDANS_TRUSTED_PROXIES` 是否包含
  代理地址。

页面请求会被重定向到登录页，API 请求返回 401。先用 `curl -i` 看响应头，再判断是未登录还是
会话校验失败。

## 访问 actuator 返回 404

管理端口和应用端口是分开的：

- 应用端口 8081、8091-8095 不提供 actuator。
- 网关使用 9080，IAM 使用 9081，其他服务使用 9091-9095。
- 网关路径带 `/apis/pudans.tolim.cn/v1alpha1/actuator`，IAM 带
  `/apis/pudans.tolim.cn/v1alpha1/iam/actuator`。

```bash
curl -i http://127.0.0.1:9081/apis/pudans.tolim.cn/v1alpha1/iam/actuator/health
```

如果管理端口只绑定 `127.0.0.1`，从另一台机器访问会失败；需要调整 `PUDANS_MANAGEMENT_BIND` 并
限制防火墙。不要把管理端口改成公网可达。

## Prometheus target 是 DOWN

在 Prometheus 容器里测试目标：

```bash
docker exec prometheus wget -qO- http://pudans-iam:9081/apis/pudans.tolim.cn/v1alpha1/iam/actuator/prometheus
```

确认 Prometheus 和业务服务在同一网络，`PUDANS_MANAGEMENT_ADDRESS` 为 `0.0.0.0`，目标名和
`metrics_path` 正确。网关和 IAM 的 metrics 路径带前缀，其他服务是 `/actuator/prometheus`。

如果 Prometheus 在宿主机运行，target 使用宿主机地址，并让 `PUDANS_MANAGEMENT_BIND` 绑定到该
地址；不能用 `127.0.0.1` 让另一台机器的 Prometheus 访问。

## Jaeger 没有 trace

检查：

```text
PUDANS_OTLP_TRACING_ENDPOINT=http://jaeger:4318/v1/traces
PUDANS_TRACING_EXPORT_ENABLED=true
PUDANS_TRACING_SAMPLING=0.1
```

先访问一次网关 API，再查 Jaeger 的 service 列表。采样率 0.1 意味着只有约十分之一请求会被记录；
排查时可以临时设为 `1.0`，完成后恢复。没有安装 Jaeger 时把 export 关闭，避免 exporter 持续报错。

## 日志文件没有生成

确认容器内路径是 `/var/log/pudans/...`，对应卷已挂载，且镜像以 `pudans` 用户运行、目录可写。
先看标准输出：

```bash
cd deploy/assets/iam
docker compose --env-file ../.env logs --tail=200
```

日志同时写容器标准输出和 `PUDANS_LOG_FILE`。如果只看到标准输出，检查卷挂载和文件权限；如果只看
到文件，检查 Docker 日志驱动配置。

## RocketMQ 连接失败

确认 `PUDANS_MESSAGING_BROKER=rocketmq`，`PUDANS_ROCKETMQ_NAME_SERVER` 指向 NameServer。最关键的
是 broker 的 `brokerIP1` 必须是业务容器可达的地址，不能是 broker 容器内的回环地址；同时确认
10909/10911 在 `1panel-network` 内可达。

生产环境预创建 `pudans-message-announcement` topic 和 `pudans-mq-announcement` 消费者组，并关闭
自动创建。开启 ACL 时同时设置 access key 和 secret key。

## 端口冲突

宿主机 8080 或 9080/9081/9091-9095 被占用时，先查：

```bash
ss -lntp | grep -E '8080|9080|9081|9091|9092|9093|9094|9095'
```

网关对外端口可以通过 `PUDANS_GATEWAY_PORT` 改；管理端口由 compose 固定，修改时要同步服务环境变量
和 Prometheus target。不要为了腾端口把管理端口直接暴露到公网。

## 镜像构建很慢或失败

共享 Dockerfile 在容器内运行 Gradle，首次构建会下载 JDK、Gradle 和 Maven 依赖。确认部署主机能访问
镜像仓库和 Maven Central，磁盘空间充足，并保留 `.dockerignore` 以排除各模块的 `build` 目录。

如果只是更新一个服务，单独构建该服务即可。生产环境建议在 CI 构建并推送镜像，部署主机只拉取。

不要在本地后端正在运行时重新构建 bootJar；本地调试和镜像构建分开进行。

## 构建时拉取基础镜像失败

Dockerfile 需要 `gradle:9.9.0-jdk25` 和 `eclipse-temurin:25-jre`。如果部署主机访问 Docker Hub
超时，在 1Panel 的容器镜像加速或私有仓库配置 mirror，或把这两个 `FROM` 换成内网镜像。先手动
`docker pull` 验证镜像可用，再执行 compose 构建。

## 容器被 OOM 杀掉

先看容器退出码和内核日志：

```bash
docker inspect pudans-iam --format '{{.State.OOMKilled}} {{.State.ExitCode}}'
```

默认 JVM 使用 `MaxRAMPercentage=75.0`。容器内存限制很低时，适当降低该比例或增加内存；不要只改
`-Xmx` 而不考虑容器上限。IAM 和 repository 的内存需求通常高于 job/search。

## 时间显示不一致

容器默认使用基础镜像的时区。需要固定时区时，在对应 compose 中增加 `TZ` 环境变量，例如
`TZ=Asia/Shanghai`，然后重建容器。日志、数据库和追踪的时间字段应统一按 UTC 或统一时区解读，
排查时同时记录 `X-Request-Id` 和 `traceId`。

## 如何升级服务版本

服务版本只在根 `VERSION` 中修改。修改后重新生成 OpenAPI、构建镜像、推送新标签，并更新
`.env` 的 `PUDANS_TAG`。升级和回滚步骤见 [operations.md](../guides/operations.md)。

## 为什么 deploy 里没有 PostgreSQL/Redis 的 compose

中间件统一由 1Panel 应用商店安装，仓库只维护业务服务的 compose。这样数据库、Redis、Nacos 的
升级、备份和端口由 1Panel 管理，业务服务只负责连接。详见
[middleware.md](../guides/middleware.md)。