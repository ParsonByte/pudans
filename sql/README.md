# Database Changes

本目录集中保存 PostgreSQL 数据库变更脚本。脚本按服务划分，所有 DB XML 都必须放在这里，不得再放入各模块的 `src/main/resources`。

- 所有变更使用 Liquibase XML。
- 所有 `createTable`、`column`、`addColumn` 必须提供中文 `remarks`。
- 表名统一使用 `pudans_` 前缀，Liquibase 变量为 `${pudans_table_prefix}`。
- 每个服务目录的 Liquibase 总入口统一命名为 `changelog-master.xml`。
- 服务构建会把本目录作为额外资源目录加载，运行期 classpath 路径为 `iam/...`、`repository/...` 等。
- 内核模块的初始 DDL 已移动到对应服务目录，并按版本文件统一命名（如 `1.0.0-repository-changelog.xml`）；内核 changelog 通过 `logicalFilePath` 保持原有 Liquibase 标识，已执行过的数据库不会重复执行。

```text
sql/
├── iam/
│   ├── 1.0.0-init-changelog.xml
│   ├── 1.1.0-changelog.xml
│   ├── 1.2.0-unified-auth-changelog.xml
│   ├── 1.3.0-mfa-changelog.xml
│   └── changelog-master.xml
├── repository/
│   ├── 1.0.0-repository-changelog.xml
│   ├── 1.1.0-repository-changelog.xml
│   └── changelog-master.xml
├── notification/
│   ├── 1.0.0-notification-changelog.xml
│   └── changelog-master.xml
├── mq/
│   ├── 1.0.0-mq-changelog.xml
│   └── changelog-master.xml
├── job/
│   ├── 1.0.0-job-changelog.xml
│   └── changelog-master.xml
└── search/
    ├── 1.0.0-search-changelog.xml
    └── changelog-master.xml
```

## IAM

IAM 服务使用 PostgreSQL，并由 `iam/changelog-master.xml` 按版本顺序执行 Liquibase 变更。默认表前缀为 `pudans_`，可通过
Liquibase 属性 `${pudans_table_prefix}` 覆盖。

### 变更集

| 文件                              | 用途                                                                                      |
|-----------------------------------|-------------------------------------------------------------------------------------------|
| `1.0.0-init-changelog.xml`        | 创建 IAM 迁移项目自身使用的组织、应用和用户主表；通过前置条件兼容已由旧迁移创建的数据库。 |
| `1.1.0-changelog.xml`             | 创建身份管理、认证、应用协议、身份源、通知和系统设置等完整 IAM 核心模型。                 |
| `1.2.0-unified-auth-changelog.xml` | 创建统一认证所需的全局登录会话、SSO 票据、应用会话注册表，以及 CAM 式授权策略、角色、动作、资源、决策日志和 API 密钥模型。 |
| `1.3.0-mfa-changelog.xml`         | 创建 TOTP 和 WebAuthn 多因素认证凭证表。                                                  |
| `changelog-master.xml`            | IAM 服务的 Liquibase 总入口，按版本顺序加载全部变更集。                                   |

### 表说明

| 表名                                  | 用途                     | 关键字段或说明                                                                           |
|---------------------------------------|--------------------------|------------------------------------------------------------------------------------------|
| `pudans_iam_organization`             | IAM 组织主数据           | `id`、`name`、`status`；保存迁移项目的组织根对象。                                       |
| `pudans_iam_application`              | IAM 应用注册表           | `id`、`client_id`（唯一）、`name`、`status`；保存接入应用。                              |
| `pudans_iam_user`                     | IAM 用户账户             | `id`、`organization_id`、`username`（唯一）、`display_name`、`status`、`password_hash`。 |
| `pudans_administrator`                | 管理后台管理员           | 登录名、密码、手机号、邮箱、状态、认证次数、最近认证信息和软删除标记。                   |
| `pudans_user`                         | 普通用户账户             | 用户名、密码、邮箱、手机号、身份源、外部 ID、状态、锁定时间、认证信息和扩展参数。        |
| `pudans_user_detail`                  | 用户扩展资料             | 关联 `user_id`，保存证件类型、证件号、个人主页和地址。                                   |
| `pudans_user_group`                   | 用户组定义               | 用户组名称、编码及通用审计字段。                                                         |
| `pudans_user_group_member`            | 用户组成员关系           | 关联 `group_id` 与 `user_id`。                                                           |
| `pudans_user_history_password`        | 用户历史密码             | 关联 `user_id`，保存历史密码与修改时间，用于密码策略校验。                               |
| `pudans_user_idp_bind`                | 用户与外部身份提供商绑定 | 关联 `user_id`、第三方用户 ID（OpenID）和绑定时间。                                      |
| `pudans_organization`                 | 组织架构定义             | 名称、编码、类型、父节点、物化路径和显示路径。                                           |
| `pudans_organization_member`          | 组织成员关系             | 关联 `org_id` 与 `user_id`。                                                             |
| `pudans_app`                          | 应用主数据               | 应用编码、客户端 ID、类型、协议、状态和访问配置。                                        |
| `pudans_app_group`                    | 应用分组                 | 应用分组的管理维度数据。                                                                 |
| `pudans_app_group_association`        | 应用与分组关联           | 关联 `group_id` 与 `app_id`。                                                            |
| `pudans_app_access_policy`            | 应用访问授权策略         | 关联应用与访问主体，控制用户或组织可访问的应用。                                         |
| `pudans_app_account`                  | 应用账户                 | 关联应用与用户，保存应用侧账户名和默认账户标记。                                         |
| `pudans_app_cert`                     | 应用证书                 | 保存应用协议签名、加密所需的证书和密钥材料。                                             |
| `pudans_app_form_config`              | 表单代填应用配置         | 关联 `app_id`，保存表单代填字段及脚本配置。                                              |
| `pudans_app_oidc_config`              | OIDC 应用配置            | 关联 `app_id`，保存回调地址、授权模式、令牌有效期和签名算法。                            |
| `pudans_app_jwt_config`               | JWT 应用配置             | 关联 `app_id`，保存 JWT 签发、校验和令牌有效期配置。                                     |
| `pudans_audit`                        | 行为审计日志             | 记录操作者、资源、动作、结果、IP、时间和请求上下文。                                     |
| `pudans_identity_provider`            | 身份提供商               | 保存登录协议类型、配置、状态和展示信息。                                                 |
| `pudans_identity_source`              | 身份源配置               | 保存同步类型、基础配置、连接配置和状态。                                                 |
| `pudans_identity_source_event_record` | 身份源事件记录           | 记录身份源推送或接收的事件及处理结果。                                                   |
| `pudans_identity_source_sync_history` | 身份源同步历史           | 记录每次同步的开始、结束、结果和统计信息。                                               |
| `pudans_identity_source_sync_record`  | 身份源同步明细           | 记录同步过程中用户、组织等对象的处理结果。                                               |
| `pudans_mail_send_record`             | 邮件发送记录             | 保存收件人、模板、发送状态、错误信息和发送时间。                                         |
| `pudans_sms_send_record`              | 短信发送记录             | 保存手机号、模板、发送状态、错误信息和发送时间。                                         |
| `pudans_mail_template`                | 邮件模板                 | 按模板类型保存主题、正文、变量和启停状态。                                               |
| `pudans_setting`                      | 系统设置                 | 以名称和值为核心保存 IAM 系统级配置。                                                    |
| `pudans_third_party_user`             | 第三方用户快照           | 保存外部身份源中的用户标识、资料和来源信息。                                             |
| `pudans_login_session`                | 全局登录会话             | `sid` 主键、用户、组织、有效期、失效标记和登录 IP/UA。                                   |
| `pudans_sso_ticket`                   | SSO 一次性票据           | `ticket` 主键、应用、用户、`sid`、回跳地址、有效期和使用时间。                           |
| `pudans_app_session`                  | 应用会话缓存注册表       | 按 `sid` 与 `appCode` 记录应用侧会话缓存，支持全局登出广播和过期清理。                   |
| `pudans_authz_policy`                 | CAM 式授权策略           | `code` 唯一，保存策略类型、版本、JSONB 文档和状态。                                      |
| `pudans_authz_policy_attachment`      | 授权策略绑定             | 将策略绑定到用户、用户组、角色、组织或客户端。                                           |
| `pudans_authz_role`                   | 授权角色                 | `code` 唯一，保存角色名称、JSONB 信任策略和状态。                                        |
| `pudans_authz_role_binding`           | 授权角色绑定             | 将角色绑定到用户、用户组、角色、组织或客户端。                                           |
| `pudans_service_action`               | 应用动作声明             | 按应用保存 `{appCode}:{action}`、关联资源类型和风险级别。                                |
| `pudans_resource_type`                | 应用资源类型声明         | 按应用保存资源类型编码和资源模式。                                                       |
| `pudans_authz_log`                    | 鉴权决策日志             | 记录主体、应用、动作、资源、决策、原因和 JSONB 上下文。                                  |
| `pudans_api_key`                      | API 密钥                 | 保存主体、访问密钥 ID、密钥摘要、状态和过期时间。                                        |
| `pudans_totp_credential`              | TOTP 认证凭证            | 关联 `user_id`，保存加密的 TOTP 密钥、算法、时间步长、失败次数和锁定时间。                |
| `pudans_webauthn_credential`          | WebAuthn 认证凭证        | 关联 `user_id`，保存 COSE 公钥、签名计数器、传输方式和认证器信息。                        |

## Repository

文件仓储服务使用 PostgreSQL，由 `repository/changelog-master.xml` 按版本顺序执行 Liquibase 变更。
默认表前缀为 `pudans_`，可通过 Liquibase 属性 `${pudans_table_prefix}` 覆盖。

### 变更集

| 文件 | 用途 |
|---|---|
| `1.0.0-repository-changelog.xml` | 创建文件元数据表（内核初始 DDL，保留原 `logicalFilePath`）。 |
| `1.1.0-repository-changelog.xml` | 增加按租户、应用和创建时间查询的索引。 |
| `changelog-master.xml` | 仓储服务的 Liquibase 总入口。 |

### 表说明

| 表名 | 用途 | 关键字段或说明 |
|---|---|---|
| `pudans_repository_file` | 文件元数据 | 租户、应用、分类、存储提供方、对象 key、大小、校验和、JSONB 元数据和状态。 |

## Notification

通知服务使用 PostgreSQL，由 `notification/changelog-master.xml` 按版本顺序执行 Liquibase 变更。
默认表前缀为 `pudans_`，可通过 Liquibase 属性 `${pudans_table_prefix}` 覆盖。

### 变更集

| 文件                              | 用途                                                     |
|-----------------------------------|----------------------------------------------------------|
| `1.0.0-notification-changelog.xml` | 创建通知模板、发送渠道配置、发送记录和死信表。           |
| `changelog-master.xml`            | 通知服务的 Liquibase 总入口，按版本顺序加载全部变更集。  |

### 表说明

| 表名                                  | 用途             | 关键字段或说明                                                         |
|---------------------------------------|------------------|------------------------------------------------------------------------|
| `pudans_notification_template`        | 通知模板         | `template_code`（唯一）、渠道、主题、正文、服务商模板编码和启停状态。   |
| `pudans_notification_provider`        | 通知渠道配置     | `provider_code`（唯一）、渠道、服务商类型、优先级和加密后的配置。       |
| `pudans_notification_send_record`     | 通知发送记录     | `request_id`（幂等键，唯一）、渠道、服务商、状态、重试次数和发送结果。  |
| `pudans_notification_dead_letter`     | 通知死信         | 关联原发送记录，保存收件人、失败原因、重放状态和重放时间。             |

## MQ

消息中心使用 PostgreSQL，由 `mq/changelog-master.xml` 加载 Liquibase 变更。表名统一使用 `pudans_` 前缀。

### 变更集

| 文件 | 用途 |
|---|---|
| `1.0.0-mq-changelog.xml` | 创建消息、投递和死信表（内核初始 DDL，保留原 `logicalFilePath`）。 |
| `changelog-master.xml` | 消息中心的 Liquibase 总入口。 |

### 表说明

| 表名 | 用途 | 关键字段或说明 |
|---|---|---|
| `pudans_mq_message` | 消息主表 | 业务键、topic、事件类型、生产者、payload、状态、重试次数和下次投递时间。 |
| `pudans_mq_delivery` | 投递记录 | 消息、消费组、投递状态、尝试次数、下次重试时间和错误信息。 |
| `pudans_mq_dead_letter` | 死信 | 关联消息与投递，保存失败原因、重放/丢弃状态和补偿状态。 |

## Job

任务执行器使用 PostgreSQL，由 `job/changelog-master.xml` 加载 Liquibase 变更。表名使用
Liquibase 变量 `${pudans_table_prefix}`，默认值为 `pudans_`。

### 变更集

| 文件 | 用途 |
|---|---|
| `1.0.0-job-changelog.xml` | 创建任务定义和任务执行表（内核初始 DDL，保留原 `logicalFilePath`）。 |
| `changelog-master.xml` | 任务执行器的 Liquibase 总入口。 |

### 表说明

| 表名 | 用途 | 关键字段或说明 |
|---|---|---|
| `${pudans_table_prefix}job_definition` | 任务定义 | 编码、名称、Handler、调度类型、cron/固定周期、超时、重试和阻塞策略。 |
| `${pudans_table_prefix}job_execution` | 执行历史 | 任务、触发类型、执行状态、开始/结束时间、耗时、结果和错误信息。 |

## Search

搜索服务使用 PostgreSQL 全文检索，由 `search/changelog-master.xml` 加载 Liquibase 变更。
表名统一使用 `pudans_` 前缀。

### 变更集

| 文件 | 用途 |
|---|---|
| `1.0.0-search-changelog.xml` | 创建搜索索引和文档表。 |
| `changelog-master.xml` | 搜索服务的 Liquibase 总入口。 |

### 表说明

| 表名 | 用途 | 关键字段或说明 |
|---|---|---|
| `pudans_search_index` | 搜索索引 | 应用、索引编码、名称、启用状态和描述。 |
| `pudans_search_document` | 搜索文档 | 索引、文档 ID、标题、摘要、正文、标签、JSONB 元数据和全文检索列。 |

## 维护规则

- 新增变更时只能追加新的 XML changeSet，不修改已在生产环境执行过的 changeSet。
- 表名必须使用 `${pudans_table_prefix}` 变量或明确的 `pudans_` 前缀。
- 新增表时同步更新本 README 的“表说明”。
- 每个服务脚本只存放在各自的 `sql/<service>` 目录；其他服务使用各自目录，禁止跨服务混放。