# 可观测性

可观测性中间件通过 1Panel 应用商店安装：Prometheus、Grafana、Jaeger。业务服务只负责暴露
管理端口、发送 OTLP 追踪和写 ECS JSON 日志，不负责运行这些组件。

## 组件与端口

| 组件 | 1Panel 默认端口 | 作用 |
| --- | --- | --- |
| Prometheus | 9090 | 采集管理端口指标、评估告警规则 |
| Grafana | 3300 | 指标看板与告警联系点 |
| Jaeger | 16686 / 4318 | 追踪查询 UI / OTLP HTTP 接收 |

这些端口只应在内网访问，不要通过 1Panel 反向代理暴露到公网。

## Prometheus

1Panel 安装 Prometheus 后，导入仓库中的
[platform/observability/prometheus/prometheus.yml](../../platform/observability/prometheus/prometheus.yml)
与 [rules.yml](../../platform/observability/prometheus/rules.yml)。

如果 Prometheus 容器和业务容器都在 `1panel-network`，把 `pudans-host` 替换为各服务的容器名：

```text
pudans-gateway:9080
pudans-iam:9081
pudans-repository:9091
pudans-notification:9092
pudans-mq:9093
pudans-job:9094
pudans-search:9095
```

网关与 IAM 的 `metrics_path` 带前缀，其他服务使用 `/actuator/prometheus`，不要混用。compose 已经
把 `PUDANS_MANAGEMENT_ADDRESS` 设为 `0.0.0.0`，所以容器网络可以访问管理端口；宿主机默认只绑定
`127.0.0.1`。

如果 Prometheus 运行在 1Panel 主机而不是业务容器网络中，把 `.env` 的
`PUDANS_MANAGEMENT_BIND` 改成 1Panel 主机内网地址，重新创建服务容器，并在防火墙上只允许
Prometheus 主机访问 9080/9081/9091-9095。此时 Prometheus target 使用 `1panel-host:9081` 这类
地址。

验证 target：

```bash
curl -fsS http://127.0.0.1:9090/api/v1/targets
```

Prometheus 页面中七个 `pudans-*` job 应为 `UP`；`up{job=~"pudans-.*"} == 0` 会触发
`PudansServiceDown`。

## Grafana

1Panel 安装 Grafana 后，导入
[datasources.yml](../../platform/observability/grafana/provisioning/datasources/datasources.yml)：
Prometheus 地址改为 `http://prometheus:9090`，Jaeger 地址改为 `http://jaeger:16686`（以 1Panel 实际
容器名为准）。再导入
[pudans-overview.json](../../platform/observability/grafana/dashboards/pudans-overview.json)。

看板包含服务可用性、请求量、5xx、p95 延迟、JVM 内存等面板。数据源 UID 是 `prometheus` 和
`jaeger`，如果 1Panel 安装时生成了不同 UID，在 Grafana 中修改数据源 UID 或在看板 JSON 中同步。

## Jaeger

在 `.env` 中设置：

```text
PUDANS_OTLP_TRACING_ENDPOINT=http://jaeger:4318/v1/traces
PUDANS_TRACING_EXPORT_ENABLED=true
PUDANS_TRACING_SAMPLING=0.1
```

采样率默认 0.1。生产环境可以根据流量调整；全量采样会显著增加 Jaeger 存储和网络开销。没有安装
Jaeger 时把 `PUDANS_TRACING_EXPORT_ENABLED` 设为 `false`，否则 exporter 会持续尝试连接并产生错误
日志。

网关会把 W3C `traceparent` 和 `X-Request-Id` 传给下游；服务日志中的 `traceId`/`spanId` 可以
直接跳到 Jaeger 查询。请求 ID 还会进入网关 MDC，跨 Reactor 线程切换后仍然保留。

验证：

```bash
curl -fsS http://127.0.0.1:16686/api/services
```

访问一次网关 API 后，Jaeger 的 service 列表应出现 `pudans-gateway`、`pudans-iam` 等名称。

## 日志

日志是 ECS JSON。compose 将 `PUDANS_LOG_FILE` 固定为 `/var/log/pudans/pudans-<service>.json`，
并挂载到 `<service>-logs` 卷；同时 Docker 的 `json-file` 驱动保留最近 5 个 20 MB 的容器日志。

查看容器标准输出：

```bash
cd deploy/assets/iam
docker compose --env-file ../.env logs -f --tail=200
```

查看文件日志：

```bash
docker run --rm -v pudans-iam_iam-logs:/logs alpine tail -n 200 /logs/pudans-iam.json
```

卷名前缀取决于 compose 项目名；用 `docker volume ls | grep pudans` 确认实际名称。

当前部署栈没有集中式日志存储。需要跨服务检索时，使用 1Panel 的
容器日志查看器、Docker 日志或主机上的日志文件；如果后续引入日志后端，再单独增加采集组件。

## 告警

`rules.yml` 只负责在 Prometheus 中评估规则。告警要真正发出，还需要在 Grafana 中配置：

1. 在 Grafana Alerting 中创建 Contact point（邮件、Webhook、钉钉或其他通知渠道）。
2. 创建 Notification policy，把 `severity=critical` 和 `severity=warning` 路由到该联系点。
3. 导入或重建 `rules.yml` 中三条规则对应的 Grafana 告警规则，数据源选择 Prometheus。

当前没有部署 Alertmanager；如果改用 Alertmanager，需要单独安装并让 Prometheus 指向它。没有
Contact point 时，Prometheus 规则只会显示 `firing`，不会产生外部通知。

## 安全

- Prometheus、Grafana、Jaeger 和业务管理端口只开放在内网。
- Grafana 默认管理员口令必须修改；不要让 Grafana 匿名访问。
- 不要把 `/actuator/prometheus` 或 `/actuator/metrics` 通过网关或公网反向代理暴露。
- 管理端点没有认证，防护依赖网络隔离和防火墙。
- tracing endpoint 和日志文件不包含请求体或凭据；不要把敏感字段写入自定义日志。