# Package layout rule

## Scope

- Applies to every package, class and interface under `appkernel/src/main/java/cn/tolim/pudans/`.
- Determine the target package from this rule before adding, moving or renaming anything. A directory role that is not listed here must not be created.
- Which side an implementation belongs to is decided by this rule, not by caller convenience.

## Top-level packages (exactly seven)

`cn.tolim.pudans` may contain only `contract`, `core`, `event`, `extension`, `infra`, `security` and `setting`, plus `Application.java`.

| Package | Layer | Constraint |
| --- | --- | --- |
| `contract` | Contract | Business data types only. No `@Component`, `@Service`, `@Repository`, `@Configuration` or `@Bean`; no IO |
| `core` | Business implementation | Business domains only |
| `event` | Events | Cross-layer events, split by domain |
| `extension` | Subsystem | Extension engine plus its contracts in `extension/contract` |
| `infra` | Subsystem | Infrastructure plus its contracts in `infra/contract` |
| `security` | Subsystem | Security implementation plus its contracts in `security/contract` |
| `setting` | Subsystem | Settings implementation plus its contracts in `setting/contract` |

Adding an eighth top-level package requires changing this rule first; do not add one directly in code.

## Mirror rule (hard)

`contract/<domain>` and `core/<domain>` map one to one. Both sides must contain exactly the same domain set, and that set contains business domains only.

Current business domains (seven):

```text
audit  live  rbac  risk  storage  user  webhook
```

Framework subsystems are not part of `core`. `extension`, `infra`, `security` and `setting` are top-level packages, and their contracts live in their own `contract/` package, never in the top-level `contract`.

Adding a business domain means adding both `contract/<domain>` and `core/<domain>`. Adding a subsystem requires changing this rule first.

## Role directory vocabulary (closed set)

| Directory | Responsibility |
| --- | --- |
| `contract/<domain>/` | Resource models (`Extension` subclasses) at the domain root |
| `contract/<domain>/dto/` | HTTP request, response and view types |
| `core/<domain>/endpoint/` | Routes and request binding |
| `core/<domain>/reconciler/` | Extension reconcilers; every `*Reconciler` belongs here |
| `core/<domain>/<subdomain>/` | Domain-internal implementation such as `driver`, `gateway` or `memory` |
| `core/<domain>/policy/` | Rule and policy objects, only when needed |
| `<subsystem>/contract/` | Interfaces and data types exposed by that subsystem |

Each side carries only the roles its domain needs; do not create empty directories for symmetry. Apart from `dto/`, a business domain must not gain another intermediate directory.

## Hard prohibitions

1. No package may be named `service`, and no `service/` directory may exist.
2. No `resource/` intermediate directory; resource models sit at the root of `contract/<domain>/`.
3. `contract` may contain only the seven business domain directories. It must not contain `extension`, `infra`, `security` or `setting`.
4. A business domain root must not contain an `event` package, and the contract layer must not contain an `event` package; event definitions live in `event/<domain>/`. Internal subsystem events such as `extension/event` and `infra/event` are implementation detail and may stay.
5. The top-level `contract` layer must not hold behaviour interfaces. Domain SPI belongs to the implementation side: the domain root, or `<subsystem>/contract/` for a subsystem.
6. No `*ServiceImpl` suffix. Multiple implementations are expressed with a source prefix: `Extension*`, `Local*`, `Feishu*`, `Default*` or `Configured*`.
7. No class name with a `Service` suffix. Name classes by responsibility, following the naming vocabulary below.

Violating any of these is a defect, not a style preference.

## Naming vocabulary (classes)

| Naming | Use |
| --- | --- |
| Plural noun | Read and write entry point of an aggregate, such as `Accounts`, `Plans` or `Policies` |
| `Catalog` / `Directory` | Read-only listing |
| `Guard` | Decision and interception |
| `Pipeline` | Staged processing |
| `Gateway` / `Resolver` / `Sync` | Inbound and outbound adapters, resolution, synchronisation |
| `Log` / `Cache` / `Recovery` | Records, caching, recovery |
| `Keys` / `Tokens` / `Cipher` / `Secrets` | Credentials and cryptography |
| Source prefix | `Extension*`, `Local*`, `Feishu*`, `Default*` or `Configured*` |

## Validation commands

```bash
cd appkernel/src/main/java/cn/tolim/pudans

# exactly seven top-level packages
ls -d */

# both sides must match; output must be empty
diff <(ls contract) <(ls core)

# every subsystem exposes its contract directory
for p in extension infra security setting; do test -d "$p/contract" || echo "missing $p/contract"; done

# the following commands must produce no output
find . -type d -name service
find . -type d -name resource
find . -name '*ServiceImpl.java'
grep -rnE 'class [A-Za-z0-9_]+Service\b|interface [A-Za-z0-9_]+Service\b' .
find ./contract -type d -name event
for d in audit live rbac risk storage user webhook; do find ./core/$d -maxdepth 1 -type d -name event; done
```

## Directory tree

```text
contract/          business contracts only
├── audit/         AuditRecord
├── live/          resource models + dto/
├── rbac/          roles, bindings, user groups
├── risk/          RiskRule + dto/
├── storage/       storage resources
├── user/          User, UserConnection
└── webhook/       Webhook + dto/

core/              the same seven business domains
├── audit/{endpoint}
├── live/{endpoint,reconciler,alert,monitor,recorder,recording,resolve,source}
├── rbac/{endpoint,reconciler,group,permission}
├── risk/{endpoint,reconciler}
├── storage/{endpoint,reconciler,download,upload,driver}
├── user/{endpoint,reconciler}
└── webhook/{reconciler}

extension/         extension engine + contract/ contracts
infra/             infrastructure + contract/ contracts + dashboard/ console read model
security/          security implementation + contract/ contracts
setting/           settings implementation + contract/ contracts

event/
├── DomainEvent.java
├── user/
├── live/
└── security/
```
