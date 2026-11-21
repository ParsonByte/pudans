# 包布局规则

## 适用范围

- 适用于 `appkernel/src/main/java/cn/tolim/pudans/` 下的全部包、类与接口。
- 新增、移动或重命名任何包、类、接口之前，先按本规则确定目标位置；规则没有列出的目录角色一律不允许新建。
- 一个实现放在哪一侧由本规则决定，不由调用方的便利决定。

## 顶层包（只有 3 个）

`cn.tolim.pudans` 下只允许 `contract`、`core`、`event` 三个包，加 `Application.java`。

| 顶层包 | 层 | 约束 |
| --- | --- | --- |
| `contract` | 契约层 | 只放数据类型，禁止 `@Component`、`@Service`、`@Repository`、`@Configuration`、`@Bean`，禁止 IO |
| `core` | 实现层 | 全部实现都在这里，包括业务域与框架子系统 |
| `event` | 事件层 | 跨层事件，按域分子包 |

新增第 4 个顶层包必须先改本规则，不得在实现里直接加。

## 镜像规则（硬）

`core/<域>` 与 `contract/<域>` 一一对应：两侧域集合必须完全相同，不多一个也不少一个。

当前域集合：

```text
ai  audit  dashboard  extension  infra  live  rbac
risk  security  setting  storage  user  webhook
```

新增域时先加 `core/<域>`，同时加 `contract/<域>`，两侧同名。框架子系统不例外：`extension`、`infra`、`security`、`setting` 的实现同样在 `core` 下，契约在 `contract` 下。

## 角色目录词表（封闭集合）

| 目录 | 职责 |
| --- | --- |
| `contract/<域>/` | 资源模型（`Extension` 子类）直接放域根 |
| `contract/<域>/dto/` | HTTP 请求、响应、视图 |
| `core/<域>/endpoint/` | 路由与出入参绑定 |
| `core/<域>/reconciler/` | 扩展资源协调器，所有 `*Reconciler` 必须在此 |
| `core/<域>/<子域>/` | 领域内部实现，如 `driver`、`gateway`、`memory` |
| `core/<域>/policy/` | 规则与策略对象，只有需要时出现 |

每侧只出现该域需要的角色，不得为对称而建空目录。除 `dto/` 外不得在域下再插一层中间目录。

`extension`、`infra`、`security`、`setting` 是框架子系统，可沿用既有内部结构（`core/extension/index`、`core/infra/exception` 等），但同样受下面的硬性禁止约束。

## 硬性禁止

1. 任何包名不得叫 `service`，也不得出现 `service/` 目录。
2. 不得出现 `resource/` 中间目录；资源模型直接放 `contract/<域>/` 根。
3. `contract` 与 `core` 的域集合必须完全一致。
4. 业务域的域根下不得有 `event` 包；契约层不得有 `event` 包；事件定义统一在 `event/<域>/`。子系统与其子域的内部事件（`core/extension/event`、`core/infra/event`、`core/ai/harness/event`）属于实现细节，允许保留。
5. 契约层不得放行为接口；域级 SPI 属于实现侧，放 `core/<域>/` 根。
6. 不得出现 `*ServiceImpl` 后缀类；多实现用来源前缀表达（`Extension*`、`Local*`、`Feishu*`、`Ai*`、`Default*`、`Configured*`）。
7. 不得出现带 `Service` 后缀的类名；类名按职责命名（见命名词表）。

违反任何一条都按缺陷处理，不是风格偏好。

## 命名词表（类名）

| 命名 | 用途 |
| --- | --- |
| 复数名词 | 聚合读写入口，如 `Accounts`、`Plans`、`Policies` |
| `Catalog` / `Directory` | 只读清单 |
| `Guard` | 判定与拦截 |
| `Pipeline` | 分阶段处理 |
| `Gateway` / `Resolver` / `Sync` | 出入站适配、解析、同步 |
| `Log` / `Cache` / `Recovery` | 记录、缓存、恢复 |
| `Keys` / `Tokens` / `Cipher` / `Secrets` | 凭证与密码学 |
| 来源前缀 | `Extension*`、`Local*`、`Feishu*`、`Ai*`、`Default*`、`Configured*` |

## 校验命令

```bash
cd appkernel/src/main/java/cn/tolim/pudans

# 顶层只有 3 个包
ls -d */

# 两侧域集合必须一致，必须无输出
diff <(ls contract) <(ls core)

# 以下命令必须无输出
find . -type d -name service
find . -type d -name resource
find . -name '*ServiceImpl.java'
grep -rnE 'class [A-Za-z0-9_]+Service\b|interface [A-Za-z0-9_]+Service\b' .
for d in ai audit dashboard live rbac risk storage user webhook; do find ./core/$d -maxdepth 1 -type d -name event; done
find ./contract -type d -name event
```

## 目录清单

```text
contract/
├── ai/           资源模型
├── audit/        AuditRecord
├── dashboard/dto/
├── extension/    扩展框架 API（controller、endpoint、exception、index、router）
├── infra/        跨域 SPI（trace、utils）
├── live/         资源模型 + dto/
├── rbac/         角色、绑定、用户组
├── risk/         RiskRule + dto/
├── security/     资源模型 + authentication/、device/
├── setting/      Setting、AnnotationSetting
├── storage/      存储资源
├── user/         User、UserConnection
└── webhook/      Webhook + dto/

core/           与 contract 同名的 13 个域
├── ai/<子域>/
├── audit/{endpoint}
├── dashboard/{endpoint}
├── extension/    扩展引擎实现
├── infra/        基础设施实现
├── live/{endpoint,reconciler,alert,monitor,recorder,recording,resolve,source}
├── rbac/{endpoint,reconciler,group,permission}
├── risk/{endpoint,reconciler}
├── security/     安全实现
├── setting/{endpoint}
├── storage/{endpoint,reconciler,download,upload,driver}
├── user/{endpoint,reconciler}
└── webhook/{reconciler}

event/
├── DomainEvent.java
├── user/
├── live/
└── security/
```
