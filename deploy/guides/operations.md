# 运维手册

## 日常检查

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

```bash
cd deploy/assets/gateway
docker compose --env-file ../.env ps
```

每个服务的 compose 目录都可以执行同样的 `ps`、`logs` 和 `restart`。重点检查：

- 七个业务容器状态是否为 `Up`。
- 管理端口健康检查是否返回 `"status":"UP"`。
- Nacos 控制台是否仍有七个服务实例。
- Prometheus 的 `pudans-*` target 是否全部 `UP`。
- Jaeger 是否持续收到新的 trace。
- PostgreSQL 连接数、磁盘空间和备份是否正常。

## 升级

升级前：

1. 备份 PostgreSQL 的六个业务库、repository 数据卷、Nacos 配置和 `.env`。
2. 确认新版本只包含向前兼容的 Liquibase changelog。
3. 在测试环境执行一次新镜像启动和健康检查。

构建或拉取新镜像。构建在 CI 或 Docker builder 中完成；不要在本地后端正在运行时重建 bootJar。

```bash
docker build -f deploy/assets/Dockerfile --build-arg SERVICE=iam -t registry.example.com/pudans/iam:0.1.1 .
```

更新 `.env`：

```text
PUDANS_REGISTRY=registry.example.com/pudans
PUDANS_TAG=0.1.1
```

逐个服务重建。先升级无状态服务，再升级 IAM，最后网关；每个服务执行：

```bash
cd deploy/assets/iam
docker compose --env-file ../.env up -d --force-recreate
```

`up -d` 会创建新容器并停止旧容器，存在短暂中断。需要零中断时，使用多副本或外部编排平台，而不是
在单个 compose 中滚动。

升级后检查：

- 服务启动日志中没有 Liquibase 校验失败。
- 管理端口健康检查为 `UP`。
- Nacos 实例数为预期值。
- 网关登录、会话校验和至少一个业务 API 可用。
- Prometheus 5xx、p95 和错误日志没有明显上升。

## 回滚

把 `PUDANS_TAG` 改回上一版本，再重建对应服务：

```bash
cd deploy/assets/iam
docker compose --env-file ../.env up -d --force-recreate
```

回滚只回滚应用镜像，不回滚数据库。Liquibase 已经执行的 changelog 不会自动撤销；如果新版本包含了
不兼容的表结构变更，必须先恢复升级前的数据库备份，再启动旧镜像。不要通过修改已发布的 changelog
来“回滚”，那会破坏数据库的校验和。

## 备份与恢复

### PostgreSQL

在 1Panel 的 PostgreSQL 容器中执行，容器名以实际为准：

```bash
docker exec -t postgresql pg_dump -U pudans -Fc -d pudans_iam > pudans_iam.dump
```

六个数据库分别备份：`pudans_iam`、`pudans_repository`、`pudans_notification`、`pudans_mq`、
`pudans_job`、`pudans_search`。恢复前先停止写入，使用 `pg_restore` 恢复到空库或重建库。

1Panel 自带的数据库备份功能优先使用；无论用哪种方式，都要定期做恢复演练。

### repository 数据

本地存储路径是容器内 `/var/lib/pudans/repository`，对应 `repository-data` 卷：

```bash
docker run --rm -v pudans-repository_repository-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/repository-data.tar.gz -C /data .
```

加密密钥在 `repository-keys` 卷中，必须和文件数据一起备份。`SPISSATUS_KEY_PROVIDER=memory` 的
密钥不会持久化，生产环境不要使用该模式。

### Nacos 与配置

Nacos 配置通过控制台导出，或保留 `platform/nacos/config` 的版本化副本。`.env` 中的密钥由部署平台
或 1Panel 保管，不要放进 Git。

## 扩容

单服务 compose 默认使用固定 `container_name`，适合单实例。需要多副本时：

- 复制服务目录并修改 `name`、`container_name` 和管理端口映射。
- 多个实例注册同一个 Nacos 服务名，网关通过 Spring Cloud LoadBalancer 分发。
- 会话统一放在 Redis，不能依赖单实例内存。
- 作业执行器需要确认同一任务不会被多个实例重复执行；按 job 的调度配置调整。
- 日志和指标按实例增加，Prometheus target 列表要同步更新。

高可用还要求 PostgreSQL、Redis、Nacos 和 RocketMQ 自身具备冗余；只扩容业务服务不能消除中间件
单点。

## 日志与磁盘

Docker 日志驱动已经限制为 20 MB × 5。业务 ECS 日志写在 `<service>-logs` 卷中，当前没有自动清理
策略；需要定期检查卷占用，或在 1Panel 中对日志卷做清理/归档。

```bash
docker system df
docker volume ls
```

不要直接删除正在使用的卷。清理前先确认备份和保留周期。

## 安全检查

- 所有示例口令、`PUDANS_INTERNAL_TOKEN`、`PUDANS_JWT_SECRET`、SPISSATUS 密钥口令都已替换。
- 只有网关 8080 暴露；9080/9081/9091-9095、5432、6379、8848、9876、8858、9090、3300、16686、
  4318 只在防火墙允许的内网地址上。
- 1Panel、Nacos、Grafana、Prometheus、Jaeger 的管理口令不使用默认值。
- HTTPS 在 1Panel OpenResty 或外部负载均衡终止，网关只信任已知代理地址。
- `PUDANS_COOKIE_SECURE=true`，CORS 和 `PUDANS_HOME_ORIGIN` 只包含实际前端域名。
- `.env` 权限限制为部署账号可读，不进入镜像、Git 或日志。
- 定期更新基础镜像、JDK、PostgreSQL、Redis、Nacos 和 1Panel 应用版本。

## 故障处理顺序

1. 看 `docker compose ps` 和管理端口健康检查。
2. 看容器日志中的第一个异常，而不是最后一条错误。
3. 确认 Nacos 配置、数据库、Redis、RocketMQ 是否可达。
4. 检查网关到 IAM 的会话校验、Nacos 实例列表和 Prometheus target。
5. 需要回滚时先确认数据库备份，再切换镜像标签。
6. 详细问题见 [FAQ](../faq/index.md)。