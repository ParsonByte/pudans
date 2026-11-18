# 存储策略与系统文件服务方案（Cloudreve v4 → Pudans / Java）

> 状态：待评审  
> 日期：2026-11-18  
> 范围：pudans 后端内核的**系统级存储策略与文件服务**，供其他内核模块调用；**不建设文件管理器系统**  
> 参考实现：`/Users/byte/Global/Reference/cloudreve/cloudreve`  
> 目标栈：Java 25 / Spring Boot WebFlux / Spring Data R2DBC / Reactor / 扩展 CRD 内核  
> 存储约束：**所有持久化数据走扩展存储，不新增数据库表，不新增 SQL**

---

## 0. 实施进度（2026-11-18，第二轮审计后）

- **M1 / M2 已完成**：存储策略 CRD、文件索引 CRD、本地驱动、路径规则、凭据加密、文件类型、分片上传、回收站、代理下载、签名令牌、清理任务、端点、RBAC、参数设置。
- **S3 / KS3 已完成**：AWS SDK v2，原生 multipart、预签名分片、Range、CORS、批量删除、测试连接。
- **OSS / COS / OBS 已完成（后端）**：官方 SDK，原生 multipart、CORS、Range、预签名下载、单次 PUT；未实现预签名分片，客户端分片经服务端转发。
- **七牛 / 又拍已完成（后端）**：官方 SDK，`DirectUploadDriver` 直传凭证（Qiniu upToken、Upyun policy+signature），服务端分片回退、Range、stat/delete/测试连接仍可用；没有 part 级原生 multipart。
- **OneDrive 已完成**：authorize/callback/status、SharePoint 站点根解析、共享 Token 刷新、上传会话、Range、downloadUrl、stat/delete。
- **测试**：517 个测试全部通过，其中存储相关 94 个，覆盖上传、下载、回收站、清理任务、OAuth、策略/文件/传输端点、本地/S3/OSS/COS/OBS/七牛/又拍/OneDrive 驱动。
- **已验证**：完整 Spring 上下文在本地 PostgreSQL/Redis 上启动成功；RSA 登录后 API E2E 通过——`GET /storagepolicies/-/types` 返回 9 种类型、`GET /storagepolicies` 返回默认策略且 `ready=true`、`POST /storagepolicies/storage-policy-default/test` 成功、`GET /storagefiles` 返回空列表。
- **未验证**：真实云环境端到端联调（当前无云账号/凭据）；OneDrive OAuth 回调与 SharePoint 根解析仅有逻辑/编译验证。前端按你的要求不实现。
- **已执行**：`./gradlew :appkernel:test`、`./gradlew :api-docs:validateOpenApiDocs`、`git diff --check` 均通过。代码未提交。

---

## 1. 结论先行

- **存储策略是系统级资源，不绑定用户、用户组或角色**。管理员配置策略，其他内核模块（聊天附件、头像、审计归档等）通过统一的 `StorageService` **按策略名调用**；未指定时使用默认策略。
- 本次交付 **策略 + 文件索引 + 分片上传 + 回收站 + 文件类型校验 + 代理下载** 的系统文件服务，但**不做文件管理器**：没有目录树、重命名/移动/复制、分享链接、WebDAV、预览缩略图、版本控制、用户网盘和配额。
- **所有持久化数据走扩展存储**：`StoragePolicy` 和 `StorageFile` 都是扩展 CRD；文件索引通过扩展索引引擎（内存索引）查询，不新增 `storage_file` 表、不修改 `schema-postgresql.sql`。
- 上传会话元数据存放在 `StorageFile.spec`（`state=UPLOADING`）；分片进度由驱动 `ListParts` / 本地临时目录恢复，complete 由客户端提交分片清单，不引入额外的分片状态存储。
- 驱动层定义 Reactor 化 `StorageDriver` SPI；分片上传优先使用驱动原生 multipart（S3/OSS/COS/OBS/七牛），不支持时由服务层落本地临时分片再合并。代理下载由内核统一流式输出，支持 Range。
- 交付分五期：**M1 策略与文件索引 + 本地驱动 → M2 分片上传/代理下载/回收站 → M3 S3/KS3 → M4 其它云存储 → M5 OneDrive**。
- Cloudreve 的 `remote`（从机节点）不迁移；文件管理器相关能力明确排除。

---

## 2. 范围与边界

### 2.1 本次交付

| 能力 | 说明 |
| --- | --- |
| 存储策略 | `StoragePolicy` CRD：类型、endpoint、桶、凭据、路径规则、文件类型限制、分片大小、默认策略 |
| 文件索引 | `StorageFile` CRD：策略、key、大小、类型、owner/module、状态、时间戳；支持分页与索引筛选 |
| 分片上传 | 服务端上传 + 客户端分片上传；原生 multipart 或本地分片回退；断点续传、完成、中止、过期清理 |
| 回收站 | 软删除、恢复、立即彻底删除、按保留期自动清理 |
| 文件类型 | 扩展名黑白名单 + 文件名正则黑白名单；上传前校验；索引保存扩展名/contentType |
| 代理下载 | 内核流式下载、Range、Content-Disposition、签名下载 URL；私有桶不暴露直链 |
| 驱动 | local、s3、ks3、oss、cos、obs、qiniu、upyun、onedrive |
| 对外 API | 管理策略/文件/回收站；客户端分片上传与代理下载（签名令牌） |
| 内核调用 | `StorageService` 供其他后端模块直接调用，不依赖 HTTP |

### 2.2 明确不做：文件管理器系统

| 不做 | 说明 |
| --- | --- |
| 目录树 / 文件夹 CRUD | 文件索引是扁平结构，模块用 `owner` 和 key 前缀自行组织 |
| 重命名 / 移动 / 复制 | 模块需要新位置时重新上传并删除旧文件 |
| 分享链接 / 协作权限 | 下载能力由模块鉴权后签发短期 URL |
| WebDAV / 挂载盘 / 多用户网盘 | 不属于系统存储 |
| 预览 / 缩略图 / 媒体元数据 | 文件模块能力，本次不做 |
| 版本控制 / 去重 / 用户配额 | 不迁移 |
| 用户侧文件浏览器 | 只有管理端的文件索引/回收站页面 |

### 2.3 设计原则

- **系统级资源**：策略不由用户选择；文件通过 `owner`/`module` 做逻辑隔离，但策略本身与用户无关。
- **按名称调用**：调用模块显式传策略名，未指定时用默认策略；文件记录保存实际策略名，默认策略切换不影响已有文件。
- **能力即接口**：驱动只实现当前有消费方的操作；原生分片、Range 等能力通过可选接口/能力位暴露。
- **安全默认**：凭据不落地明文、不进日志、不出现在响应；本地存储不允许写出工作目录；下载/上传令牌短期有效。
- **不做无调用方的字段和类**：Cloudreve 中属于文件管理器的设置项，在对应能力实现时再增量加入。

---

## 3. 数据存储决策

| 数据 | 存储方式 | 理由 |
| --- | --- | --- |
| 存储策略 | 扩展 CRD `StoragePolicy` | 低写入配置；复用扩展存储、RBAC、通用路由、Reconciler |
| 文件索引 | 扩展 CRD `StorageFile` | 满足"全部走扩展存储"；复用扩展索引、分页、过滤、GC |
| 上传会话 | `StorageFile.spec`（`state=UPLOADING`） | 会话元数据持久化在记录中，重启后可恢复 |
| 分片进度 | 驱动 `ListParts` / 本地临时目录；complete 由客户端提交分片清单 | 不引入额外状态存储；原生 multipart 可自恢复 |
| 文件内容 | 驱动后端 | 本地磁盘或对象存储 |
| 下载/上传令牌 | 无状态 HMAC | 不落库，短期有效，按文件限定 |

### 3.1 规则影响

- **不新增表、不修改 `schema-postgresql.sql`、不新增 `.sql` 文件、不引入迁移框架**。
- 文件索引的数据访问完全通过 `ReactiveExtensionClient` + `ListOptions` + 扩展索引；不写手写 SQL。
- 代价：扩展索引是**进程内内存索引**，启动时按前缀全量重建（见 `DefaultIndicesInitializer`），内存和启动时间随文件数增长。
  - 缓解：`StorageFile.spec` 保持精简（不放大对象、不放大段 attributes）；回收站保留期和彻底删除控制记录总量；上线后监控记录数与启动耗时。
  - 适用规模：建议以 10 万–100 万条为观察区间；超过后另立变更评估专用数据访问层，本方案不预先引入。

---

## 4. Cloudreve → Pudans 映射

### 4.1 策略字段

| Cloudreve `ent.StoragePolicy` | Pudans `StoragePolicy.spec` | 说明 |
| --- | --- | --- |
| `name` | `metadata.name` | 资源唯一名，也是模块调用时使用的策略名 |
| — | `displayName` / `description` | 控制台展示 |
| `type` | `type` | `StoragePolicyType` 枚举，小写 code |
| `server` | `server` | 服务端 endpoint；AWS S3 可为空 |
| `bucket_name` | `bucketName` | OneDrive 用作 clientId（M5） |
| `is_private` | `privateBucket` | 私有桶生成签名 URL；代理下载默认开启 |
| `access_key` / `secret_key` | `credentials.accessKey` / `secretKey` | 加密存储 |
| `max_size` | `maxSize` | 单文件上限，0 表示不限制 |
| `dir_name_rule` / `file_name_rule` | `dirNameRule` / `fileNameRule` | 路径规则 |
| `settings.region` | `settings.region` | |
| `settings.s3_path_style` | `settings.s3PathStyle` | |
| `settings.s3_delete_batch_size` | `settings.deleteBatchSize` | |
| `settings.chunk_size` | `spec.chunkSize` | 分片大小 |
| `settings.file_type` / `is_file_type_deny_list` | `spec.fileTypes` / `fileTypeDenyList` | 扩展名黑白名单 |
| `settings.name_regexp` / `is_name_regexp_deny_list` | `spec.nameRegexp` / `nameRegexpDenyList` | 文件名正则 |
| `settings.custom_proxy` / `internal_proxy` | `spec.proxyDownload` | 统一为是否代理下载 |
| `settings.token`（Upyun） | — | Upyun 使用 `accessKey` / `secretKey` 作为操作员和密码；`credentials.token` 仅用于 OneDrive 刷新令牌 |
| `settings.od_*` / OAuth | `settings.tenantId` / `settings.driveId` + `credentials.clientSecret` / `refreshToken` | M5 |
| `node_id` | — | 不迁移 |
| `groups` edge | — | **不迁移**：系统级存储，不按用户/组绑定 |

### 4.2 驱动类型

| Cloudreve type | Java 实现 | SDK | 分片 | 执行模型 | 阶段 |
| --- | --- | --- | --- | --- | --- |
| `local` | `LocalStorageDriver` | JDK NIO | 服务层本地分片回退 | 全异步 | M1 |
| `s3` | `S3StorageDriver` | `software.amazon.awssdk:s3` + `netty-nio-client` | 原生 multipart + 预签名分片 | 原生异步 | M3 |
| `ks3` | 复用 `S3StorageDriver` | 同上 | 同上 | 原生异步 | M3 |
| `oss` | `OssStorageDriver` | `com.aliyun.oss:aliyun-sdk-oss` | 原生 multipart | `boundedElastic` | M4 |
| `cos` | `CosStorageDriver` | `com.qcloud:cos_api` | 原生 multipart | `boundedElastic` | M4 |
| `obs` | `ObsStorageDriver` | `com.huaweicloud:esdk-obs-java` | 原生 multipart | `boundedElastic` | M4 |
| `qiniu` | `QiniuStorageDriver` | `com.qiniu:qiniu-java-sdk` | 原生 multipart | `boundedElastic` | M4 |
| `upyun` | `UpyunStorageDriver` | `com.upyun:java-sdk` | 服务层本地分片回退 | `boundedElastic` | M4 |
| `onedrive` | `OneDriveStorageDriver` | 现有 WebClient + Graph REST | Graph upload session | 原生异步 | M5 |
| `remote` | — | — | — | — | 不迁移 |

---

## 5. 领域模型

### 5.1 `StoragePolicy` CRD

包路径：`cn.tolim.pudans.contract.core.extension.storage`

```java
@GVK(group = StoragePolicy.GROUP, version = StoragePolicy.VERSION,
     kind = StoragePolicy.KIND, plural = "storagepolicies", singular = "storagepolicy")
public class StoragePolicy extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "StoragePolicy";

    private Spec spec = new Spec();
    private Status status = new Status();

    @Data
    public static class Spec {
        @Schema(requiredMode = REQUIRED)
        private String displayName;

        private String description;

        @Schema(requiredMode = REQUIRED)
        private StoragePolicyType type;

        private String server;
        private String bucketName;
        private boolean privateBucket;
        private long maxSize;

        @Schema(requiredMode = REQUIRED)
        private String dirNameRule = "uploads/{path}";

        @Schema(requiredMode = REQUIRED)
        private String fileNameRule = "{randomkey8}_{originname}";

        // 分片大小，0 表示使用驱动默认值
        private long chunkSize;

        // 扩展名黑白名单；列表为空且非拒绝模式时表示不限制
        private List<String> fileTypes = new ArrayList<>();
        private boolean fileTypeDenyList;

        // 文件名正则黑白名单；为空表示不限制
        private String nameRegexp;
        private boolean nameRegexpDenyList;

        // 私有桶是否强制经内核代理下载
        private boolean proxyDownload = true;

        // 同一时间只允许一个 ready 的默认策略
        private boolean defaultPolicy;

        private Credentials credentials = new Credentials();
        private Settings settings = new Settings();
    }

    @Data
    public static class Credentials {
        private String accessKey;
        private String secretKey;
        private String token;         // OneDrive refreshToken
        private String clientSecret;  // OneDrive
    }

    @Data
    public static class Settings {
        private String region;
        private boolean s3PathStyle;
        private int deleteBatchSize = 1000;
        private String tenantId;      // OneDrive
        private String driveId;       // OneDrive
    }

    @Data
    public static class Status {
        private boolean ready;
        private String message;
        private Instant lastTestedAt;
        private Map<String, Boolean> credentialConfigured = new LinkedHashMap<>();
    }
}
```

`StoragePolicyType`：

```java
public enum StoragePolicyType {
    LOCAL("local"), S3("s3"), KS3("ks3"), OSS("oss"), COS("cos"),
    OBS("obs"), QINIU("qiniu"), UPYUN("upyun"), ONEDRIVE("onedrive");
}
```

### 5.2 `StorageFile` CRD（文件索引）

`metadata.name` 即文件 ID（使用小写 UUID，符合扩展名称正则）。

```java
@GVK(group = StorageFile.GROUP, version = StorageFile.VERSION,
     kind = StorageFile.KIND, plural = "storagefiles", singular = "storagefile")
public class StorageFile extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "StorageFile";

    private Spec spec = new Spec();

    @Data
    public static class Spec {
        @Schema(requiredMode = REQUIRED)
        private String policy;          // StoragePolicy.metadata.name

        @Schema(requiredMode = REQUIRED)
        private String storageKey;

        @Schema(requiredMode = REQUIRED)
        private String originalName;

        private String extension;
        private String contentType;
        private long size;

        // 模块自定义归属：例如 chat:conversation:123、user:alice
        private String owner;
        private String module;

        // 小型 JSON 元数据，限制大小，避免索引膨胀
        private Map<String, String> attributes = new LinkedHashMap<>();

        private StorageFileState state = StorageFileState.UPLOADING;

        private String sha256;

        // 上传会话字段（state=UPLOADING 时有效）
        private long chunkSize;
        private int partCount;
        private String nativeUploadId;
        private Instant uploadExpiresAt;

        // 生命周期时间戳
        private Instant uploadedAt;
        private Instant trashedAt;
        private Instant trashExpiresAt;
    }
}
```

```java
public enum StorageFileState {
    UPLOADING,  // 上传会话进行中，文件内容尚未完成
    ACTIVE,     // 可读取
    TRASHED     // 已软删除，等待恢复或清理
}
```

`StorageFile` 在 `SchemeInitializer` 中注册并建立索引：

```java
schemeManager.register(StorageFile.class, is -> {
    is.add(IndexSpecs.<StorageFile, String>single("spec.policy", String.class)
            .indexFunc(file -> file.getSpec().getPolicy()));
    is.add(IndexSpecs.<StorageFile, String>single("spec.owner", String.class)
            .indexFunc(file -> file.getSpec().getOwner()));
    is.add(IndexSpecs.<StorageFile, String>single("spec.module", String.class)
            .indexFunc(file -> file.getSpec().getModule()));
    is.add(IndexSpecs.<StorageFile, StorageFileState>single("spec.state", StorageFileState.class)
            .indexFunc(file -> file.getSpec().getState()));
    is.add(IndexSpecs.<StorageFile, String>single("spec.extension", String.class)
            .indexFunc(file -> file.getSpec().getExtension()));
    is.add(IndexSpecs.<StorageFile, Instant>single("spec.trashExpiresAt", Instant.class)
            .indexFunc(file -> file.getSpec().getTrashExpiresAt()));
    is.add(IndexSpecs.<StorageFile, Instant>single("spec.uploadExpiresAt", Instant.class)
            .indexFunc(file -> file.getSpec().getUploadExpiresAt()));
});
```

> 索引字段保持精简，`attributes` 不建索引。`StorageFile` 记录会随启动过程全量加载到内存索引，记录数和 `attributes` 大小需要控制。

### 5.3 文件生命周期

```
initiateUpload
      │  (创建 UPLOADING 记录 + 原生 multipart / 临时目录)
      ▼
   UPLOADING ──complete──▶ ACTIVE ──trash──▶ TRASHED ──purge──▶ (删除记录+对象)
      │                      │                  │
      │ abort / 过期          │                  └──restore──▶ ACTIVE
      ▼                      │
   (删除记录+临时对象)         └──purge──▶ (删除记录+对象)
```

- `UPLOADING`：不可下载；过期后由清理任务中止并删除。
- `ACTIVE`：可下载、可软删除；文件索引可查询。
- `TRASHED`：不可下载（管理员可查看/恢复/彻底删除）；达到保留期后自动彻底删除。
- 彻底删除先删对象，再删索引记录；对象不存在视为成功，保证重试幂等。

### 5.4 上传会话与分片

- 一次上传对应一个 `StorageFile` 记录，`metadata.name` 即上传 ID / 文件 ID。
- 会话元数据（策略、key、大小、分片大小、分片数、原生 uploadId、过期时间）存放在该记录的 `spec` 中，应用重启后可恢复。
- 分片进度不单独持久化：
  - 原生 multipart：通过驱动 `ListParts` 查询已上传分片；
  - 本地分片回退：扫描临时目录中的分片文件；
  - complete 时客户端提交分片清单（分片号 + ETag + 大小），服务端校验后完成；
  - 客户端丢失分片清单时，可重新上传缺失分片或查询 status。
- 过期会话由清理任务中止原生上传、删除临时目录和 `UPLOADING` 记录。

---

## 6. 文件索引与回收站

### 6.1 索引服务

`StorageFileService` 基于 `ReactiveExtensionClient` 和扩展索引：

```java
Mono<StorageFile> create(StorageFile file);
Mono<StorageFile> update(StorageFile file);
Mono<StorageFile> get(String fileId);
Mono<ListResult<StorageFile>> list(StorageFileQuery query);
Mono<Long> count(StorageFileQuery query);
Mono<Boolean> existsByPolicy(String policyName);
Mono<Void> delete(String fileId);
```

查询示例：

```java
var options = ListOptions.builder()
        .andQuery(equal("spec.state", StorageFileState.ACTIVE))
        .andQuery(equal("spec.owner", owner))   // 仅非空时追加
        .build();
var page = PageRequestImpl.of(pageNumber, pageSize,
        Sort.by(desc("metadata.creationTimestamp"), asc("metadata.name")));
client.listBy(StorageFile.class, options, page);
```

- 过期扫描使用 `lessThan("spec.uploadExpiresAt", now, true)` / `lessThan("spec.trashExpiresAt", now, true)`。
- 更新使用扩展客户端的版本机制；冲突时重新读取并有限重试。
- `attributes` 在服务层做成 JSON 字符串或小型 Map，限制条目数（例如 32）和总大小（例如 4 KiB）。

### 6.2 回收站

- `StorageService.trash(fileId)`：`ACTIVE → TRASHED`，写入 `trashedAt`、`trashExpiresAt`；对象保留。
- `StorageService.restore(fileId)`：`TRASHED → ACTIVE`，清空回收站时间。
- `StorageService.purge(fileId)`：删除对象后删除扩展记录；`ACTIVE` 和 `TRASHED` 都允许（管理端需二次确认）。
- 保留期来自参数设置 `storage.trashRetentionDays`：
  - `0` 表示不自动清理，只能手动彻底删除；
  - `>0` 时 `trashExpiresAt = trashedAt + N 天`，清理任务按此扫描。
- 管理端接口：列表（按 policy/owner/module/state/extension 过滤）、恢复、彻底删除。
- 模块通过 `StorageService` 完成同样的操作，内核不做模块级权限判断。

### 6.3 文件类型

- 策略上配置：
  - `fileTypes` + `fileTypeDenyList`：扩展名黑白名单；
  - `nameRegexp` + `nameRegexpDenyList`：文件名正则黑白名单；
  - `maxSize`：单文件上限。
- 上传发起时校验一次（快速失败），完成时再校验大小与最终类型。
- 扩展名统一小写、去掉前导点；无扩展名文件在"允许列表非空"时视为不允许。
- 正则在校验策略时编译；文件名长度限制 512，避免正则灾难性回溯。
- `contentType` 由调用方提供，缺省 `application/octet-stream`；索引保存 `extension` 和 `contentType` 供模块筛选。本阶段不做重型 MIME 嗅探（不引入 Tika）。

---

## 7. 分片上传

### 7.1 两种模式

| 模式 | 使用方 | 说明 |
| --- | --- | --- |
| 服务端上传 | 后端模块直接调用 | 模块把 `Flux<DataBuffer>` 交给 `StorageService.upload(...)`，内核按 `chunkSize` 切分并写入驱动 |
| 客户端分片上传 | 前端/外部客户端 | 模块鉴权后创建上传会话并签发上传令牌；客户端直接向内核分片接口上传，或使用预签名 URL 直传对象存储 |

### 7.2 客户端分片流程

1. 模块鉴权后调用 `StorageService.initiateUpload(StorageUploadRequest)`：
   - 校验策略、文件类型、大小、分片大小；
   - 生成存储 key；
   - 创建 `UPLOADING` 扩展记录；
   - 若驱动支持原生 multipart，创建原生上传并保存 `nativeUploadId`；
   - 否则创建本地临时分片目录；
   - 返回 `UploadTicket`（fileId、chunkSize、partCount、expiresAt、uploadToken、是否预签名）。
2. 模块把 `UploadTicket` 返回给客户端。
3. 客户端按分片号上传：
   - 原生且支持预签名：`GET /storagefiles/{name}/upload/parts/{n}/url?token=...` 获取短期 URL，直传对象存储；
   - 否则：`PUT /storagefiles/{name}/upload/parts/{n}?token=...`，内核流式写入驱动或临时分片文件。
4. 客户端可随时 `GET /storagefiles/{name}/upload?token=...` 查询已上传分片，实现断点续传。
5. 客户端调用 `POST /storagefiles/{name}/upload/complete?token=...`，提交分片清单（分片号、ETag、大小）：
   - 校验所有分片、大小与顺序；
   - 原生 multipart：完成上传，得到对象；
   - 本地回退：按序合并分片，调用驱动 `put`；
   - 校验最终大小与 `maxSize`；
   - 记录置为 `ACTIVE`，清空上传会话字段；
   - 幂等：重复 complete 返回已激活的文件。
6. 失败或用户取消：`DELETE /storagefiles/{name}/upload?token=...` 中止原生上传、删除临时分片和扩展记录。

### 7.3 令牌

- 上传令牌和下载令牌统一由 `StorageTokenService` 签发，HMAC-SHA256，密钥文件 `keys/storage-token.key`。
- 载荷：`type|fileId|expiresAtEpochSeconds`，Base64Url 编码 + 签名；常量时间比较。
- 上传令牌只对指定 `fileId` 有效，不能用于其他文件；过期后模块可调用 `StorageService.uploadToken(fileId)` 重新签发。
- 下载令牌只对指定 `fileId` 有效，短期有效；文件被移入回收站后拒绝。
- 令牌端点在校验通过前不访问文件内容；错误统一返回 404/403，避免泄露文件存在性。

### 7.4 原生分片接口

```java
public interface MultipartStorageDriver {
    Mono<NativeMultipartUpload> createMultipart(StorageMultipartRequest request);
    Mono<UploadPartUrl> presignPart(NativeMultipartUpload upload, int partNumber, Duration ttl);
    Mono<UploadedPart> putPart(NativeMultipartUpload upload, int partNumber,
                               Flux<DataBuffer> body, long size);
    Mono<StorageObject> completeMultipart(NativeMultipartUpload upload, List<UploadedPart> parts);
    Mono<Void> abortMultipart(NativeMultipartUpload upload);
    Mono<List<UploadedPart>> listParts(NativeMultipartUpload upload);
}
```

- S3/KS3、OSS、COS、OBS、七牛实现该接口；local、upyun 由服务层使用本地分片回退。
- 服务层按 `StorageCapability.MULTIPART_UPLOAD` / `PRESIGNED_PARTS` 决定路径。
- S3 分片大小默认 25 MiB，最小 5 MiB，最大 5 GiB，分片数不超过 10,000；其它驱动使用各自的默认值，策略可覆盖 `chunkSize`。
- 非最后一片小于最小值时，complete 直接失败并给出明确错误。

---

## 8. 代理下载

### 8.1 下载流程

1. 模块鉴权后调用 `StorageService.downloadUrl(fileId, ttl)`，获得短期签名 URL。
2. 模块把 URL 返回给客户端（或 302 重定向）。
3. 客户端访问 `GET /storagefiles/{name}/content?token=...`：
   - 校验令牌、加载 `ACTIVE` 文件记录；
   - 按文件策略解析驱动；
   - 流式输出，不整体缓冲。
4. 如果策略 `proxyDownload=false` 且驱动支持 `PRESIGNED_SOURCE`，内核返回 302 到对象存储的预签名 URL；否则一律经内核代理。

### 8.2 响应头与 Range

- `Content-Type`、`Content-Length`、`Content-Disposition`（附件名按 RFC 5987 编码）、`Accept-Ranges`、`Content-Range`、`ETag`、`Last-Modified`。
- 支持 `Range: bytes=...`：
  - local：按偏移读取文件；
  - s3：`GetObjectRequest.range(...)`；
  - 其它驱动：能力位 `RANGE_READ` 未实现时返回完整内容（200），不返回错误的 206。
- 客户端断开时取消上游流，释放文件句柄/中止 SDK 请求。
- 下载失败不返回对象存储的原始错误详情，只返回安全错误码和请求 ID。

### 8.3 权限模型

- 内核不知道模块的业务权限；**模块先鉴权，再签发下载/上传令牌**。
- 管理端列表/恢复/彻底删除使用 RBAC；管理端下载通过 `POST /storagefiles/{name}/download-url` 获取 URL。
- 令牌端点（content、upload 分片、complete、abort、status）在安全配置中 `permitAll`，由端点自身校验令牌；其余 `/storagefiles/**` 仍走 RBAC。

---

## 9. 驱动 SPI

### 9.1 接口

```java
public interface StorageDriver extends AutoCloseable {

    StoragePolicyType type();

    Set<StorageCapability> capabilities();

    Mono<StorageObject> put(StoragePutRequest request);

    Mono<StorageReadResult> get(String key, StorageReadOptions options);

    Mono<StorageObject> stat(String key);

    Mono<Void> delete(Collection<String> keys);

    Mono<StorageSource> source(String key, StorageSourceOptions options);

    Mono<StorageTestResult> testConnection();

    @Override
    default void close() { }
}
```

```java
public record StoragePutRequest(
        String key, long size, String contentType,
        Flux<DataBuffer> body, boolean overwrite) { }

public record StorageReadOptions(ByteRange range) { }

public record ByteRange(long start, long end) { }

public record StorageObject(
        String key, long size, Instant lastModified, String etag) { }

public record StorageReadResult(
        StorageObject object, long rangeStart, long rangeEnd,
        long contentLength, Flux<DataBuffer> body) { }

public record StorageSource(URI url, Instant expiresAt, boolean proxyRequired) { }

public record StorageTestResult(boolean success, String message, Instant testedAt) { }
```

### 9.2 能力

```java
public enum StorageCapability {
    PRESIGNED_SOURCE,   // 可生成带过期时间的直链
    PRESIGNED_PARTS,    // 分片可直传对象存储
    MULTIPART_UPLOAD,   // 原生分片上传
    RANGE_READ,         // 支持 Range 读取
    PROXY_REQUIRED,     // 只能经服务端中转读取
    CORS_CONFIGURABLE   // 支持配置跨域
}
```

### 9.3 工厂、解析与缓存

```java
public interface StorageDriverFactory {
    Set<StoragePolicyType> supportedTypes();
    StoragePolicyTypeDescriptor descriptor();
    StorageDriver create(ResolvedStoragePolicy policy);
}

public record ResolvedStoragePolicy(
        StoragePolicy policy,
        StoragePolicy.Spec.Credentials credentials) { }
```

- `ResolvedStoragePolicy.credentials` 是解密后的副本，仅在构造驱动时短暂存在；策略对象本身保留密文。
- `StorageDriverResolver` 按策略名取策略、解密凭据、构造/缓存驱动；比较 `spec` 的 `equals`（含密文），凭据轮换或配置变更时自动重建。
- 更新/删除策略时 Reconciler 调用 `evict(name)`；应用关闭时统一 `close()`。
- 驱动缓存是进程内的 `ConcurrentHashMap`，不依赖 Redis。

### 9.4 流式与阻塞边界

- `StoragePutRequest.body` / 分片 body 是 `Flux<DataBuffer>`，不允许整体读入内存。
- S3：`AsyncRequestBody.fromPublisher` 接受 `Publisher<ByteBuffer>`；逐块 `DataBuffer.toByteBuffer()`（复制）后立即释放原 DataBuffer，避免池化缓冲区泄漏。
- 阻塞 SDK：把 `Flux<DataBuffer>` 桥接成有界 `InputStream`，在 `Schedulers.boundedElastic()` 上执行；Netty 事件循环不做阻塞调用。
- 本地驱动：写入临时文件后 `ATOMIC_MOVE` 落盘；key 解析后必须仍在工作目录内，拒绝 `..` 和绝对路径。
- 原生 multipart 的分片直传不经过内核，内核对分片大小、数量和完成结果做校验。

---

## 10. 路径规则

移植 Cloudreve `pkg/util/common.go` 的 `ReplaceMagicVar`：

| 变量 | 含义 |
| --- | --- |
| `{randomkey16}` / `{randomkey8}` | 随机字符串（`SecureRandom` + Base62） |
| `{randomnum2/3/4/8}` | 随机数字 |
| `{uuid}` | UUID v4 |
| `{uid}` | 调用方传入的 `owner`；系统存储可为空 |
| `{timestamp}` / `{timestamp_nano}` | Unix 秒 / 纳秒 |
| `{datetime}` / `{date}` / `{year}` / `{month}` / `{day}` / `{hour}` / `{minute}` / `{second}` | 时间格式化 |
| `{originname}` / `{originname_without_ext}` | 原始文件名 |
| `{ext}` | 扩展名（含点） |
| `{path}` | 调用方传入的 `originPath` |

- 系统默认策略使用 `uploads/{path}` + `{randomkey8}_{originname}`，不依赖 `{uid}`。
- 需要按用户隔离的模块传入 `owner=userId`，并配置含 `{uid}` 的规则。
- 校验：非空、无绝对路径、无 `..`、无反斜杠、无未知变量、至少一个唯一性变量（`randomkey*` / `uuid` / `timestamp*`）。
- `{originname}` / `{path}` 拼接前清洗路径分隔符与控制字符，防止 key 注入。
- 本地策略最终路径必须 `startsWith(workDir)`。

---

## 11. 凭据与安全

### 11.1 加密实现复用

- 抽取共享内核 `infra/crypto/FileKeyCipher`：密钥文件生成（32 字节、`rw-------`）、读取、`enc:` 前缀、AES-GCM 加解密。
- `ParameterSettingSecretCipher` 委托 `FileKeyCipher`，公开 API 不变。
- 新增 `StoragePolicySecretCipher`，使用独立密钥文件 `keys/storage-policy.key`。
- `WebhookSecretCipher` 因 KeyStore 和历史密码迁移，暂不改动。

### 11.2 写入与回显

- `StoragePolicyReconciler` 对 `credentials.*` 加密；已加密的值保持不变（幂等）。
- HTTP 响应通过 `SensitiveExtensionMixins` 的 `WRITE_ONLY` 隐藏凭据；`status.credentialConfigured` 表示是否已配置。
- 策略更新使用 PATCH，避免 PUT 清空凭据；缺少必填凭据的策略标记为 `ready=false`。
- 驱动构造时只在内存解密；日志、异常、测试连接结果不输出凭据。

### 11.3 令牌安全

- 下载/上传令牌使用独立密钥文件 `keys/storage-token.key`，HMAC-SHA256，常量时间比较。
- 令牌只绑定 `fileId` 和过期时间，不包含用户/角色信息；模块负责业务鉴权。
- 令牌短期有效；上传令牌与上传会话同寿命，可重新签发。
- 令牌端点不泄露文件是否存在；统一错误码。

---

## 12. API 与权限

### 12.1 策略接口

| 方法 | 路径 | 说明 | 权限资源 |
| --- | --- | --- | --- |
| GET/POST | `/apis/api.pudans.tolim.cn/v1alpha1/storagepolicies` | 通用列表/创建 | `storagepolicies` |
| GET/PUT/PATCH/DELETE | `.../storagepolicies/{name}` | 通用读写删 | `storagepolicies` |
| GET | `.../storagepolicies/-/types` | 类型与字段元数据 | `storagepolicies/types` |
| POST | `.../storagepolicies/-/validate` | 不落库校验 | `storagepolicies/validate` |
| POST | `.../storagepolicies/{name}/test` | 测试连接 | `storagepolicies/test` |
| POST | `.../storagepolicies/{name}/cors` | 配置跨域 | `storagepolicies/cors` |
| GET/POST | `.../storagepolicies/{name}/oauth/*` | OneDrive 授权（M5） | `storagepolicies/oauth` |

### 12.2 文件与回收站接口

`StorageFile` 是扩展 CRD，通用路由会自动提供 `GET/POST/PUT/PATCH/DELETE /storagefiles`。为避免绕过服务层直接改文件记录：

- `BuiltinEntityPolicy` 中对 `StorageFile` 的 **HTTP 创建/更新/删除** 直接拒绝；
- 只开放通用 `GET /storagefiles` / `GET /storagefiles/{name}` 用于管理端浏览；
- 文件操作通过自定义子资源完成。

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `.../storagefiles` | 管理端文件列表（field selector 过滤） | `storagefiles` get/list |
| GET | `.../storagefiles/{name}` | 文件元数据 | `storagefiles` get |
| POST | `.../storagefiles/{name}/trash` | 移入回收站 | `storagefiles/trash` create |
| POST | `.../storagefiles/{name}/restore` | 从回收站恢复 | `storagefiles/restore` create |
| DELETE | `.../storagefiles/{name}/purge` | 彻底删除 | `storagefiles/purge` delete |
| POST | `.../storagefiles/{name}/download-url` | 管理端获取签名下载 URL | `storagefiles/download-url` create |

### 12.3 上传/下载令牌接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `.../storagefiles/{name}/content?token=` | 代理下载/302 到预签名 URL |
| GET | `.../storagefiles/{name}/upload?token=` | 上传状态与已上传分片 |
| PUT | `.../storagefiles/{name}/upload/parts/{n}?token=` | 上传分片 |
| GET | `.../storagefiles/{name}/upload/parts/{n}/url?token=` | 获取预签名分片 URL |
| POST | `.../storagefiles/{name}/upload/complete?token=` | 完成上传 |
| DELETE | `.../storagefiles/{name}/upload?token=` | 中止上传 |

> 这些令牌端点需要在 `AuthorizationExchangeConfigurers` 中精确 `permitAll`，由端点内部校验令牌；管理端操作仍走 RBAC。没有 `storagepolicybindings`、`effective` 等用户绑定接口。

### 12.4 RBAC 与菜单

- `role-api-template-storage-policy.yaml`：
  - view：`storagepolicies`、`storagepolicies/types`；
  - manage：`storagepolicies` 写操作、`storagepolicies/test`、`validate`、`cors`、`oauth`。
- `role-api-template-storage-file.yaml`：
  - view：`storagefiles` 读；
  - manage：`storagefiles/trash`、`storagefiles/restore`、`storagefiles/purge`、`storagefiles/download-url`。
  - **不授予** `storagefiles` 的 create/update/patch/delete。
- 菜单：
  - `console.storage-policies` → `/storage-policies`；
  - `console.storage-files` → `/storage-files`（文件索引/回收站）。
- 在 `system-admin-role` 的依赖中加入上述 manage 角色。

---

## 13. 生命周期与清理任务

### 13.1 `StoragePolicyReconciler`

- 创建/更新：
  - 加密凭据；
  - 运行 `StoragePolicyValidator`；
  - 写 `status.ready` / `message` / `credentialConfigured`；
  - 收敛唯一的默认策略；
  - 添加 finalizer `storage-policy-protection`。
- 删除：
  - 内置默认策略 / 默认策略 / 仍被 `StorageFile` 引用的策略（`spec.policy` 索引计数 > 0）：清除 `deletionTimestamp`，写状态说明，保留 finalizer；
  - 否则：`StorageDriverResolver.evict(name)`，移除 finalizer，由 GC 删除。

### 13.2 `StorageCleanupTask`

复用 `AuditArchiveTask` 的 `Flux.interval` 模式：

- 每 `pudans.storage.cleanup-interval-seconds`（默认 300 秒）执行：
  1. 通过扩展索引查出 `state=UPLOADING` 且 `uploadExpiresAt <= now` 的记录（每批 100 条）：中止原生 multipart / 删除临时分片目录 / 删除记录；
  2. 查出 `state=TRASHED` 且 `trashExpiresAt <= now` 的记录：调用 purge（删对象 + 删记录）；
  3. 处理前重新读取记录并确认状态/时间，避免与用户操作竞争；
  4. 记录数量与失败原因，单条失败不影响下一批。
- 清理任务使用 `Schedulers.boundedElastic()` 执行阻塞文件操作。
- 启动时不需要全量扫描；按索引查询过期记录即可。

### 13.3 参数设置

新增 `ParameterSetting.Storage`：

```java
public static class Storage {
    public static final String GROUP = "storage";
    private Integer trashRetentionDays = 30;      // 0 = 不自动清理
    private Integer uploadSessionTtlHours = 24;
    private Integer downloadTokenTtlSeconds = 300;
}
```

- 通过现有 `ParameterSettingGroupService` 读取；参数变更后清理任务下次执行即生效。
- `PudansProperties` 增加 `storage.cleanup-interval-seconds`，在 `application.yaml` 中记录。

---

## 14. 校验规则

| 校验项 | 规则 |
| --- | --- |
| 策略类型 | 必须由已注册的 `StorageDriverFactory` 支持 |
| 必填凭据 | 按 `StoragePolicyTypeDescriptor`；local 不需要 |
| `server` | http/https URL；AWS S3 可为空 |
| `bucketName` | 非空（local 除外） |
| `maxSize` | `>= 0` |
| `chunkSize` | 0 或 `>= 驱动最小值`；S3 5 MiB–5 GiB，分片数 ≤ 10,000 |
| `deleteBatchSize` | `1..1000` |
| `fileTypes` | 扩展名规范化；拒绝列表/允许列表语义明确 |
| `nameRegexp` | 保存时编译；文件名长度上限 512 |
| 路径规则 | 非空、无绝对路径、无 `..`、无未知变量、至少一个唯一性变量 |
| 本地路径 | 解析后必须位于 `pudans.work-dir` 内 |
| 默认策略 | 最多一个 ready 的默认策略 |
| 文件上传 | 类型/正则/大小/分片数在发起和完成时各校验一次 |
| 回收站 | 只有 `ACTIVE` 可软删除；只有 `TRASHED` 可恢复 |
| 文件记录 | `attributes` 条目数与总大小受限；`storageKey` 不超过驱动限制 |

---

## 15. 测试与验收

| 测试类 | 覆盖 |
| --- | --- |
| `StoragePolicyPathGeneratorTest` | 全部变量、`{uid}` 映射 owner、默认规则、路径穿越拒绝、文件名清洗 |
| `StoragePolicyValidatorTest` | 必填项、URL、chunkSize、文件类型、正则、未知变量 |
| `StoragePolicySecretCipherTest` | 加解密、幂等、密钥文件权限 |
| `StorageFileIndexTest` | 索引注册、按 policy/owner/state/extension 查询、分页、过期范围查询 |
| `StorageFileServiceTest` | 创建/激活/trash/restore/purge、版本冲突重试、policy 引用检查、attributes 限制 |
| `StorageGenericMutationGuardTest` | `StorageFile` 通用 HTTP 创建/更新/删除被拒绝 |
| `LocalStorageDriverTest` | `@TempDir` 下 put/get/stat/delete、Range、越界路径拒绝、大小上限 |
| `StorageUploadServiceTest` | 发起、分片、完成、中止、断点续传、过期、幂等 complete、类型/大小校验 |
| `LocalMultipartUploadTest` | 本地分片临时目录、按序合并、清理 |
| `S3MultipartUploadTest` | Mockito 模拟 S3 async：create/upload part/presign/complete/abort/list parts |
| `StorageDownloadServiceTest` | 签名 URL、令牌过期/篡改、Range 头、回收站拒绝、302 预签名 |
| `StorageCleanupTaskTest` | 过期上传中止、过期回收站清理、单条失败不影响后续 |
| `StoragePolicyReconcilerTest` | 凭据加密、状态、默认策略唯一、删除保护（默认/被引用） |
| `SensitiveExtensionMixinsTest` | 凭据响应隐藏、请求可写 |

验收命令：

```bash
./gradlew :appkernel:test --tests 'cn.tolim.pudans.core.storage.*'
./gradlew :appkernel:test
./gradlew :api-docs:validateOpenApiDocs
git diff --check
```

S3/OSS 的真实签名与网络行为用 Mockito 单测 + 手动 MinIO/真实桶验证；不把 Testcontainers 作为常规 CI 门槛。

---

## 16. 分期实施

| 阶段 | 交付物 | 预估文件 |
| --- | --- | --- |
| **M1 策略与文件索引 + 本地驱动** | `StoragePolicy` / `StorageFile` CRD、类型枚举、策略服务/校验器/Reconciler、默认策略 YAML、文件索引服务与索引注册、本地驱动（put/get/stat/delete/source/test）、策略/文件管理端点、通用写接口守卫、RBAC/菜单、测试 | ~30 个 |
| **M2 分片上传 + 代理下载 + 回收站** | 上传会话、分片/完成/中止/状态端点、服务端上传、令牌服务、代理下载 + Range、回收站、清理任务、参数设置、安全配置、测试 | ~28 个 |
| **M3 S3/KS3** | AWS SDK 依赖、S3 驱动、原生 multipart、预签名分片、Range、CORS、测试 | ~12 个 |
| **M4 其它云存储** | OSS/COS/OBS/七牛/又拍驱动、各自 multipart/回退、CORS、测试 | ~20 个 |
| **M5 OneDrive** | Graph 客户端、OAuth、upload session、下载/直链、测试 | ~10 个 |

### 16.1 核心文件清单（M1+M2）

```text
新增
appkernel/src/main/java/cn/tolim/pudans/contract/core/extension/storage/StoragePolicy.java
appkernel/src/main/java/cn/tolim/pudans/contract/core/extension/storage/StoragePolicyType.java
appkernel/src/main/java/cn/tolim/pudans/contract/core/extension/storage/StorageFile.java
appkernel/src/main/java/cn/tolim/pudans/contract/core/extension/storage/StorageFileState.java

appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageService.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StoragePolicyService.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StoragePolicyValidator.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StoragePolicyReconciler.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StoragePolicyPathGenerator.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StoragePolicySecretCipher.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageFileService.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageFileQuery.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageUploadService.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageDownloadService.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageTokenService.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageCleanupTask.java

appkernel/src/main/java/cn/tolim/pudans/core/storage/upload/StorageUploadRequest.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/upload/StorageWriteRequest.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/upload/UploadTicket.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/upload/UploadedPart.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/upload/NativeMultipartUpload.java

appkernel/src/main/java/cn/tolim/pudans/core/storage/download/StorageReadOptions.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/download/ByteRange.java

appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageDriver.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/MultipartStorageDriver.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageDriverFactory.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageDriverResolver.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/ResolvedStoragePolicy.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StoragePolicyTypeDescriptor.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageCapability.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StoragePutRequest.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageObject.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageReadResult.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageSource.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageSourceOptions.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/StorageTestResult.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/local/LocalStorageDriver.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/driver/local/LocalStorageDriverFactory.java

appkernel/src/main/java/cn/tolim/pudans/core/storage/endpoint/StoragePolicyEndpoint.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/endpoint/StorageFileEndpoint.java
appkernel/src/main/java/cn/tolim/pudans/core/storage/endpoint/StorageTransferEndpoint.java

appkernel/src/main/java/cn/tolim/pudans/infra/crypto/FileKeyCipher.java
appkernel/src/main/java/cn/tolim/pudans/infra/crypto/HmacTokenSigner.java

appkernel/src/main/resources/extensions/storage/storagepolicy-template-default.yaml
appkernel/src/main/resources/extensions/role/api/role-api-template-storage-policy.yaml
appkernel/src/main/resources/extensions/role/api/role-api-template-storage-file.yaml
appkernel/src/main/resources/extensions/workspace/console/workspace-console-menu-template-storage-policies.yaml
appkernel/src/main/resources/extensions/workspace/console/workspace-console-menu-template-storage-files.yaml

修改
appkernel/src/main/java/cn/tolim/pudans/infra/setting/SchemeInitializer.java
appkernel/src/main/java/cn/tolim/pudans/infra/config/SensitiveExtensionMixins.java
appkernel/src/main/java/cn/tolim/pudans/infra/setting/ParameterSettingSecretCipher.java
appkernel/src/main/java/cn/tolim/pudans/contract/infra/ParameterSetting.java
appkernel/src/main/java/cn/tolim/pudans/infra/properties/PudansProperties.java
appkernel/src/main/resources/application.yaml
appkernel/src/main/java/cn/tolim/pudans/security/authorization/AuthorizationExchangeConfigurers.java
appkernel/src/main/java/cn/tolim/pudans/core/rbac/BuiltinEntityPolicy.java
appkernel/src/main/resources/extensions/builtin/builtin-role-template-roles.yaml
```

> 注意：没有 `storage_file` 表、没有 `StorageFileRepository`、没有修改 `schema-postgresql.sql`。

---

## 17. 复用证据

| 责任 | 候选 | 决策 |
| --- | --- | --- |
| 密钥加密 | `ParameterSettingSecretCipher`、`WebhookSecretCipher`、`MasterKeyService` | 抽取 `FileKeyCipher`；策略使用独立密钥文件；不新增第三个加密实现 |
| 本地路径守卫 | `AvatarStorageService` | 复用 `normalize + startsWith(workDir)` |
| Reconciler / 状态 | `WebhookReconciler`、`UserReconciler` | 复用凭据加密、finalizer、状态写回模式 |
| 定时任务 | `AuditArchiveTask`、`ParameterGarbageCollector` | 复用 `Flux.interval` / `ThreadPoolTaskScheduler` 模式 |
| 扩展索引/分页 | `DefaultIndexEngine`、`PageRequestImpl`、`ListOptions` | 直接复用扩展索引的查询、分页、排序能力 |
| 端点 | `PermissionCatalogEndpoint`、`UserAvatarEndpoint` | 复用 `CustomEndpoint` + `SpringdocRouteBuilder` |
| 参数设置 | `ParameterSettingGroupService`、`ParameterSetting` | 复用设置读取与校验 |
| 扩展初始化 | `ExtensionResourceInitializer`、`BuiltinRoleDefinitions` | 默认策略与权限 YAML |
| 敏感字段 | `SensitiveExtensionMixins` | 扩展 mixin |
| 驱动 SPI / 路径规则 | 仓库无对应实现 | 新建；Cloudreve 是参考实现 |

---

## 18. 关键决策

| 决策 | 选择 | 理由 |
| --- | --- | --- |
| 策略归属 | 系统级资源，不绑定用户/组 | 存储是内核基础设施；模块负责业务鉴权 |
| 策略选择 | 模块显式传策略名，未指定用默认策略 | 配置清晰，文件记录固定实际策略名 |
| 策略存储 | 扩展 CRD | 低写入配置，复用扩展内核 |
| 文件索引 | 扩展 CRD `StorageFile` | 遵循"全部走扩展存储"；复用索引、分页、GC |
| 文件索引代价 | 内存索引 + 启动全量重建 | 以精简字段、回收站保留期和记录量控制成本；超出观察区间另立变更 |
| 上传会话 | `StorageFile.spec` | 元数据持久化、重启可恢复；不引入额外状态存储 |
| 分片进度 | 驱动 ListParts / 本地临时目录 + 客户端分片清单 | 无额外存储；原生 multipart 可自恢复 |
| 分片实现 | 原生 multipart 优先，本地回退 | 兼顾性能与驱动覆盖 |
| 回收站 | 软删除状态 + 定时清理 | 不复制对象，恢复成本低 |
| 文件类型 | 扩展名黑白名单 + 文件名正则 | 对齐 Cloudreve，简单可校验 |
| 代理下载 | 默认经内核，支持 Range | 私有桶不暴露直链；模块鉴权后签发短期令牌 |
| 下载/上传鉴权 | HMAC 短期令牌 | 内核不感知模块权限；无状态、可水平扩展 |
| 文件管理器 | 不做 | 目录/分享/预览/版本等不属于系统存储 |

---

## 19. 风险与取舍

1. **文件索引常驻内存**：`StorageFile` 记录会在启动时全量加载到内存索引，内存和启动时间随文件数增长。缓解：精简 `spec`、限制 `attributes`、回收站保留期、彻底删除、监控记录数与启动耗时；如果记录量超过观察区间，另立变更评估专用数据访问层。
2. **策略名是模块契约**：模块配置中引用策略名，删除非默认策略前必须确认没有文件引用；策略删除守卫会检查 `StorageFile.spec.policy`，但仍建议控制台提示。
3. **策略配置变更影响存量文件**：修改 endpoint/bucket/凭据可能使已有文件不可读。控制台应提示；必要时新建策略并迁移文件。
4. **分片实现的差异**：S3 分片大小有严格下限；本地回退会产生临时磁盘占用；多实例部署下本地临时分片依赖共享工作目录或粘性会话，对象存储直传更稳妥。
5. **阻塞 SDK**：OSS/COS/OBS/七牛/又拍 SDK 是阻塞式，必须只在驱动内部切 `boundedElastic`；M4 评审时用线程模型检查确认。
6. **凭据更新语义**：通用 PUT 会整体替换对象，策略编辑必须使用 PATCH；缺少必填凭据的策略标为 `ready=false`。
7. **令牌安全**：令牌是 bearer capability，必须短期有效、绑定 fileId、使用独立密钥；不得写入日志。模块必须在签发前完成业务鉴权。
8. **对象与索引一致性**：complete 先完成对象再激活索引；purge 先删对象再删索引；重复操作必须幂等，清理任务可重试。
9. **通用 CRUD 绕过风险**：`StorageFile` 是扩展 CRD，通用路由会自动暴露写接口；必须在 `BuiltinEntityPolicy` 中拒绝 `StorageFile` 的 HTTP 创建/更新/删除，并用测试覆盖。
10. **不做文件管理器**：目录树、重命名/移动/复制、分享、预览、版本、去重、配额均不在范围内，避免把系统存储做成半个网盘。

---

## 20. 实施前检查清单

- [ ] 阅读 `.agents/rules/development-repo.md`、`reuse-and-cleanup.md`、`testing.md`、`sql.md`、`.agents/skills/development-repo/SKILL.md`、`.agents/skills/reuse-check/SKILL.md`
- [ ] 记录复用搜索命令、候选文件与结论（PR/任务摘要）
- [ ] 先写测试：索引查询、路径、校验、上传、下载、回收站、清理任务
- [ ] 确认没有新增表、没有修改 `schema-postgresql.sql`、没有新增 SQL 文件
- [ ] 确认 `StorageFile` 的通用 HTTP 创建/更新/删除被拒绝
- [ ] 确认文件索引字段精简、`attributes` 有大小限制，避免内存索引膨胀
- [ ] 确认凭据只在构造驱动时解密，日志/响应无明文
- [ ] 确认令牌端点精确 `permitAll`，其余文件接口仍走 RBAC
- [ ] 确认默认策略不可删除、被文件引用的策略不可删除
- [ ] 运行 `./gradlew :appkernel:test`、`./gradlew :api-docs:validateOpenApiDocs`、`git diff --check`
- [ ] 提交前阅读 `.agents/rules/commit.md` 与 `.agents/skills/commit-repo/SKILL.md`
