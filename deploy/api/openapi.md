# OpenAPI 文档

## 生成物

OpenAPI 3.0 文档位于 `api-docs/openapi/v3_0`：

| 文件 | 内容 |
| --- | --- |
| [`apis_iam.v1alpha1.json`](../../api-docs/openapi/v3_0/apis_iam.v1alpha1.json) | IAM |
| [`apis_repository.v1alpha1.json`](../../api-docs/openapi/v3_0/apis_repository.v1alpha1.json) | repository |
| [`apis_notification.v1alpha1.json`](../../api-docs/openapi/v3_0/apis_notification.v1alpha1.json) | notification |
| [`apis_mq.v1alpha1.json`](../../api-docs/openapi/v3_0/apis_mq.v1alpha1.json) | mq |
| [`apis_job.v1alpha1.json`](../../api-docs/openapi/v3_0/apis_job.v1alpha1.json) | job |
| [`apis_search.v1alpha1.json`](../../api-docs/openapi/v3_0/apis_search.v1alpha1.json) | search |
| [`aggregated.json`](../../api-docs/openapi/v3_0/aggregated.json) | 六份服务文档的聚合 |

网关没有业务 controller，因此没有独立网关文档；网关路由和认证错误不在这些 JSON 中。

文档由代码注解生成，不能手工修改。服务版本来自根 `VERSION`，版本变化后要重新生成。

## 重新生成

```bash
./gradlew :api-docs:generateOpenApiDocs
```

生成器会以 `doc` profile 启动六个业务应用，从 18091-18096 端口读取 `/v3/api-docs/<group>`，写出
服务文档并合并 `aggregated.json`。执行前确认这些端口空闲，且本地没有正在运行的 bootJar 后端。

## 校验

```bash
./gradlew :api-docs:validateOpenApiDocs
```

校验器检查 OpenAPI 3.0 结构、路径、操作和聚合结果。当前输出为七份文档、158 个服务操作和
119 条聚合路径；操作数量随接口变化，以实际输出为准。

生产构建可以只保留生成和校验，不要把 Swagger UI 暴露到公网。各服务的 `application.yml` 已经关闭
`springdoc.api-docs` 和 `swagger-ui`；文档是构建期产物。

## 查看

用 Swagger Editor 打开 `api-docs/openapi/v3_0/aggregated.json`：

```text
https://editor.swagger.io/
```

也可以把 `aggregated.json` 交给 Redoc、Postman 或网关平台导入。不要在生产网关后面托管
`swagger-ui`。

## 与错误处理的关系

OpenAPI 描述 controller 的请求和成功响应。IAM 的 404、500 等异常由
`IamApiExceptionHandler` 在运行时处理，不会自动出现在 JSON 中。如果要把 404 作为契约的一部分，
需要在对应接口上显式声明 `@ApiResponse(responseCode = "404")`，再重新生成文档。

部署层面的鉴权、限流、CORS、Cookie 和管理端口见 [overview.md](overview.md)。