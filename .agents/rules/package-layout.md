# 包布局规则

## 适用范围

- 适用于 `appkernel/src/main/java/cn/tolim/pudans/` 下的全部包、类与接口。
- 新增、移动或重命名任何包、类、接口之前，先按本规则确定目标位置；规则没有列出的目录角色一律不允许新建。
- 一个实现放在哪一侧由本规则决定，不由调用方的便利决定。

## 顶层包（只有 7 个）

`cn.tolim.pudans` 下只允许 `contract`、`core`、`event`、`extension`、`infra`、`security`、`setting` 这 7 个包，加 `Application.java`。

| 顶层包 | 层级 | 约束 |
| --- | --- | --- |
| `contract` | 契约层 | 只放数据类型，禁止 `@Component`、`@Service`、`@Repository`、`@Configuration`、`@Bean`，禁止 IO |
| `core` | 领域实现层 | 领域实现都在这里 |
| `event` | 事件定义层 | 按域分子包 |
| `extension` | 子系统实现层 | 契约在 `contract/extension` |
| `infra` | 子系统实现层 | 契约在 `contract/infra` |
| `security` | 子系统实现层 | 契约在 `contract/security` |
| `setting` | 子系统实现层 | 契约在 `contract/setting` |

新增第 8 个顶层包必须先改本规则，不得在实现里直接加。

## 镜像规则

对任意实现根包 `P`，其契约在 `contract/P`。`core` 是透明容器：`core/<域>` 的契约在 `contract/<域>`。

| 实现根包 | 契约位置 |
| --- | --- |
| `core/user` | `contract/user` |
| `extension` | `contract/extension` |
| `infra` | `contract/infra` |

## 角色目录词表（封闭集合，不得新增）

| 目录 | 职责 |
| --- | --- |
| `contract/<域>/resource/` | 扩展资源模型（`Extension` 子类） |
| `contract/<域>/dto/` | HTTP 请求、响应、视图 |
| `core/<域>/endpoint/` | 路由与出入参绑定 |
| `core/<域>/reconciler/` | 扩展资源协调器，所有 `*Reconciler` 必须在此 |
| `core/<域>/<子域>/` | 领域内部实现，如 `driver`、`gateway`、`memory` |
| `core/<域>/policy/` | 规则与策略对象，只有需要时出现 |

每侧只出现该域需要的角色，不得为对称而建空目录。

## 硬性禁止

1. 任何包名不得叫 `service`，也不得出现 `service/` 目录。
2. `core` 下不得有 `event` 包；契约层不得有 `event` 包；事件统一在 `event/<域>/`。
3. 契约层不得放行为接口（域级 SPI 属于实现侧，放 `core/<域>/` 根）。
4. 不得出现 `*ServiceImpl` 后缀类；多实现用来源前缀表达（`Extension*`、`Local*`、`Feishu*`、`Ai*`、`Default*`、`Configured*`）。
5. 不得出现带 `Service` 后缀的类名；类名按职责命名（见命名词表）。

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
# 以下命令必须无输出
grep -rn 'package .*\.service' appkernel/src/main/java
find appkernel/src/main/java -type d -name service
find appkernel/src/main/java -name '*ServiceImpl.java'
grep -rnE 'class [A-Za-z0-9_]+Service\b|interface [A-Za-z0-9_]+Service\b' appkernel/src/main/java
find appkernel/src/main/java/cn/tolim/pudans/core -maxdepth 2 -type d -name event
find appkernel/src/main/java/cn/tolim/pudans/contract -type d -name event

# 顶层只允许 7 个包加 Application.java
find appkernel/src/main/java/cn/tolim/pudans -mindepth 1 -maxdepth 1
```

## 目录清单

目标形态如下（contract/、core/、event/ 三段），与本规则不一致的实现以本清单为准：

```text
contract/
├── ai/resource/、audit/resource/、dashboard/dto/、live/{resource,dto}、rbac/{resource}、
│   risk/{resource,dto}、storage/resource/、user/resource/、webhook/{resource,dto}
├── extension/{,endpoint}
├── infra/{,trace}
├── security/{resource,authentication,device}
└── setting/resource/

core/
├── ai/<子域>/
├── audit/{endpoint}
├── dashboard/{endpoint}
├── live/{endpoint,reconciler,alert,monitor,recorder,recording,resolve,source}
├── rbac/{endpoint,reconciler,permission,group}
├── risk/{endpoint,reconciler}
├── storage/{endpoint,reconciler,download,upload,driver}
├── user/{endpoint,reconciler}
└── webhook/{reconciler}

event/
├── DomainEvent.java
├── user/、live/、security/
```
