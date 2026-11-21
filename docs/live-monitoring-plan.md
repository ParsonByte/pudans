# 直播监控开发方案

> 文档性质：开发方案（设计 + 落地计划），不是已完成实现。本文基于当前 pudans 仓库勘察编写，未改动任何代码。
> 适用范围：抖音直播间链接解析、直播源管理、开播监控、自动录制、录制文件归档与清理、监控看板。
> 目标基线：Java 25 / Spring Boot 4.2.0-RC2 / WebFlux / R2DBC PostgreSQL / Reactive Redis / React 19。
> 对应需求：R1–R6、S1–S6、C1–C13、M1–M7。

## 0. 一页结论

1. 只新增两个 Extension 资源：`LiveSource`（复数 `livesources`）与 `LiveRecording`（复数 `liverecordings`）。不建新表，不改 `schema-postgresql.sql`，不引入 JDBC/JPA/Flyway。
2. 直播源主键固定为 `douyin-{roomId}`，数据库主键天然阻止重复创建；比“同一 platform + roomId 只允许一个启用源”更严格，也避免应用层并发校验漏洞。
3. 抖音公开直播间页面无需登录即可查看，解析默认不发送 Cookie；可选 Cookie 仅由运维显式配置，作为风控缓解，不是解析、创建源或录制的前置条件。解析不引入 GraalJS、Jsoup 或 Playwright，用 WebClient 拉 HTML，字符串状态机提取 `self.__pace_f.push([1,"c:..."])` 的最后一个参数，再用 Jackson 解析，绝不执行远程脚本。
4. 录制使用 JavaCV 1.5.11 的 `FFmpegFrameGrabber.grabPacket()` + `FFmpegFrameRecorder.start(AVFormatContext)` + `recordPacket(AVPacket)` 流复制，不重新编码。
5. 分片只做本地缓存：每 10s 或 30s 一个 `segment-*.ts`，不再逐段上传。默认每 1 小时把已关闭分片合并成一个 `part-*.ts` 成品（可配置间隔或关闭周期合并），合并成功后立即物理删除分片；成品上传到对象存储成功后立即删除本地成品。这样磁盘占用始终是“当前小时分片 + 少量待传成品”，不会无限增长。
6. 存储只允许对象存储：每个源可动态选择就绪的 `StoragePolicy`（S3/KS3/OSS/COS/OBS），明确拒绝 `StoragePolicyType.LOCAL`；未选择时使用默认策略，但默认策略也必须是对象存储。录制创建前完成校验，避免录到一半才发现只能落本地盘。
7. 所有阻塞操作（HTML 解析、JavaCV 拉流、分片写入、合并、对象存储上传）都放在专用 `boundedElastic`、有界线程池或 controller 虚拟线程，绝不占用 WebFlux 事件循环。
8. 删除统一走 Extension finalizer：`LiveSource` 先停录再移除 finalizer；`LiveRecording` 先删对象存储成品再删记录。审计沿用 `AuditWebFilter`，不新增审计实现。
9. 监控、录制、合并、清理的调度沿用仓库现有的 `@PostConstruct + Flux.interval` 模式；指标沿用 Micrometer；告警发 `DomainEventPublisher` 事件，复用现有 Webhook，不新增通知库。
10. 主要风险是抖音反爬/页面结构变化、JavaCV native 打包、短视频分片合并的开销、对象存储多段上传兼容性、磁盘水位与单实例内存状态。对应缓解措施和 Phase 0 验证点见第 17、18 节。

## 1. 目标、范围与非目标

### 1.1 目标

用户只填一个抖音直播间链接，系统完成：

```text
链接 -> 提取 roomId -> 解析直播间信息/流地址 -> 创建直播源
     -> 定时检测开播 -> 自动创建录制任务 -> 拉流写本地 TS 分片（10s/30s）
     -> 每小时合并成品 -> 成品上传对象存储 -> 删除分片与本地成品
     -> 录制列表/下载/删除/保留策略清理
     -> 监控看板、指标、告警、审计
```

### 1.2 范围内

- R1–R6：链接提取、解析适配、短缓存、降级/错误码/冷却、流地址重解析、解析指标。
- S1–S6：以链接创建、CRUD、重复防护、状态机、启停监控、删除。
- C1–C13：任务创建、JavaCV 录制、分段、重解析、退避重连、手动停止、归档、失败处理、列表详情、下载、删除、保留清理、并发限制。
- M1–M7：开播检测、状态快照、录制健康、断流/失败告警、风控告警、看板 API、Actuator 指标。

### 1.3 非目标

- 不接第二个直播平台；`LivePlatform` 先只定义 `DOUYIN`，但字段预留。
- 不做弹幕、评论、礼物、观众数采集；参考项目的 WebSocket 弹幕链路不迁移。
- 不做转码、转封装、切片 HLS、水印、封面生成；录制文件就是 MPEG-TS 流复制产物。
- 不做多 CDN 调度、断点续传录制、跨机房容灾。
- 不新增通知库；告警只发领域事件/Webhook。
- 不把录制文件读入 JVM 堆；不新增对象存储驱动；不把本地存储作为录制成品的最终存储。
- 不实现抖音登录、Cookie 自动刷新或验证码破解；默认不发送 Cookie，只有运维显式配置 `PUDANS_LIVE_DOUYIN_COOKIE` 时才附带。

## 2. 现状与复用证据

### 2.1 仓库现状

- 当前 `appkernel/src/main/java/cn/tolim/pudans/core` 下只有 `ai`、`audit`、`dashboard`、`event`、`rbac`、`risk`、`storage`、`user`、`webhook`，没有 `live` 包。
- `contract` 下没有 `live` 包；`contract/core/extension` 下没有直播资源。
- 全仓库没有 `FFmpegFrameGrabber`、`FFmpegFrameRecorder`、`javacv`、`bytedeco` 的使用；录制循环必须自研。
- 参考实现 `C:/Users/Develop-1/Downloads/live-room-watcher-master/live-room-watcher-master` 只有直播间解析器和弹幕 WebSocket，没有录制器；不能照搬 C2/C3。

### 2.2 复用清单（按能力）

| 能力 | 现有实现 | 本方案用法 | 结论 |
| --- | --- | --- | --- |
| 资源模型/存储 | `contract/extension/AbstractExtension.java`、`ReactiveExtensionClient`、`ExtensionClient` | 新增两个 CRD；不建表 | 复用 |
| 资源注册/索引 | `appkernel/src/main/java/cn/tolim/pudans/infra/setting/SchemeInitializer.java` | 注册 `LiveSource`/`LiveRecording` 及查询索引 | 复用并扩展 |
| 控制器/终结器 | `core/storage/StoragePolicyReconciler.java` | 照抄 finalizer 范式；删除清理写在自己的 Reconciler | 复用 |
| 通用 CRUD 路由 | `extension/router/ExtensionRouterFunctionFactory.java` | 保留通用 GET/PUT/PATCH/DELETE；创建走安全子路径 | 复用 |
| 自定义端点 | `core/risk/endpoint/RiskRuleEndpoint.java`、`core/storage/endpoint/StorageFileEndpoint.java` | `CustomEndpoint` + `SpringdocRouteBuilder.route()` | 复用 |
| 鉴权 | `security/.../RequestInfoAuthorizationManager.java` | 资源名=路径静态段；只授权安全子路径 | 复用 |
| 审计 | `security/audit/AuditWebFilter.java`、`core/audit/service/AuditService.java` | 通用写请求自动审计；定制端点补 `recordChild` | 复用 |
| 对象存储 | `core/storage/StorageService.java`、`StorageUploadService.java` | 成品上传、预签名/应用签名下载、分片会话 | 复用，新增一个“先删对象”方法 |
| 事件 | `core/event/DomainEventPublisher.java` | 状态变更、断流、录制失败、风控告警 | 复用 |
| Redis | `ReactiveStringRedisTemplate` | 解析缓存、single-flight、冷却、告警去重、租约/心跳 | 复用 |
| 指标 | `MeterRegistry`，参考 `core/ai/metrics/AiMetrics.java` | 新增 `LiveMetrics` | 复用 |
| 调度 | `StorageCleanupTask.java`、`AiTaskWorker.java` | `Flux.interval` + boundedElastic + `@PreDestroy` | 复用 |
| 前端 | `web/src/modules/workspace/console/...`、TanStack Query、shadcn/ui | 新增 `/live` 页面与菜单/角色模板 | 复用 |
| 测试 | JUnit 5 / Mockito / Reactor Test / WebTestClient | 新增镜像测试包 | 复用 |

### 2.3 复用检索结论

- 没有可复用的直播解析、直播源、录制、录制清理实现；新增是必要的，不是重复造轮子。
- 资源生命周期、路由、鉴权、审计、存储、事件、Redis、指标、调度均已有稳定设施，不新增平行实现。
- 唯一需要扩展的既有代码是 `StorageService`：新增 `purgeObjectFirst(String fileId)`，因为现有 `purge` 是先删记录再删对象，不满足 C11“先删对象存储对象，再删记录”的顺序。该方法只给直播录制使用，不改变既有 `purge` 行为。

## 3. 总体架构

```text
浏览器 /live
   │  REST（WebFlux，非阻塞）
   ▼
LiveSourceEndpoint / LiveRecordingEndpoint
   │  create / update / delete / query
   ▼
Extension 框架（ReactiveExtensionClient + controller + GC）
   │  watch / reconcile
   ├─ LiveSourceReconciler ──> LiveSourceRegistry（内存快照）
   │                              └─ LiveMonitorWorker（单 leader，boundedElastic）
   │                                    └─ LiveResolveService（WebClient + Redis）
   │                                          └─ DouyinLiveRoomResolver / PacePayloadExtractor
   │
   ├─ LiveRecordingReconciler ──> finalizer：先删对象成品，再删本地分片/成品与记录
   │
   └─ LiveRecordingCleanupTask ──> 保留策略：client.delete -> finalizer

LiveMonitorWorker
   └─ LiveRecorderService（准入队列）
         └─ LiveRecorderRegistry
               ├─ LiveRecorderTask（每任务独占 FFmpegFrameGrabber）
               │     ├─ LiveSegmentWriter（本地 TS 分片，10s/30s）
               │     ├─ LivePartMerger（每小时/停止时合并分片）
               │     └─ LiveStreamOpener（HLS/FLV 依次尝试）
               ├─ LiveStoragePolicyResolver（只解析就绪的对象存储策略）
               └─ LivePartUploader（成品上传，独立 1 线程）
                     └─ StorageService.initiateUpload/putPart/completeUpload（不 spool 整文件）
```

### 3.1 线程模型

| 执行单元 | 线程/调度器 | 允许的阻塞 | 说明 |
| --- | --- | --- | --- |
| HTTP 端点 | WebFlux event loop | 禁止 | 只做参数校验、Reactive 调用；不碰 JavaCV、不读大文件 |
| HTML/JSON 解析 | `Schedulers.boundedElastic()` | 允许 | R2 明确要求；用 `Mono.fromCallable(...).subscribeOn(boundedElastic)` |
| 监控 tick | 专用 `boundedElastic(2, 1000, "live-monitor")` | 允许 | 每 tick 只处理到期源；不并发解析同一源 |
| 录制任务 | `Schedulers.newBoundedElastic(maxConcurrent, queueCapacity, "live-recorder", 60, true)` | 允许 | 每任务一个线程；JavaCV 阻塞读包 |
| 分片合并 | `newBoundedElastic(2, 100, "live-merge")` | 允许 | 与录制线程隔离；只读已关闭分片，同一录制串行合并 |
| 成品上传 | `newBoundedElastic(1, 100, "live-upload")` | 允许 | 每小时只上传少量成品；上传慢不阻塞拉流和合并 |
| 保留清理 | 专用 `boundedElastic(1, 100)` | 允许 | 逐条处理，失败不阻塞后续 |
| 状态写回 | 与调用方同线程，但节流 | 允许 | 见 5.3/8.3；不得每个 packet 写库 |

### 3.2 部署假设

- 默认按**单活应用实例**设计：内存 `LiveSourceRegistry`、`LiveRecorderRegistry` 只在本实例有效。
- 为防止多实例重复录制，增加 Redis 监控 leader 租约：`pudans:live:monitor:leader`，只有 leader 执行开播检测和创建录制任务；非 leader 只读、只服务 API。
- 录制任务写 `pudans:live:recording:owner:{name}` 心跳（TTL 90s），启动恢复时据此判断“本进程遗留”还是“别的实例在录”。
- 停止请求用 `pudans:live:recording:stop:{name}`（TTL 1h）广播；录制任务每 1s 检查，任意实例的停止/删除都能通知 owner，不依赖本地 registry。
- 若明确只部署单实例，leader 租约仍保留，成本很低，且为后续扩容留路。

## 4. 数据模型

### 4.1 两个 CRD 及命名

| 资源 | kind | plural | 名称规则 | 说明 |
| --- | --- | --- | --- | --- |
| 直播源 | `LiveSource` | `livesources` | `douyin-{roomId}` | roomId 为纯数字；名称小写，符合资源名约束 |
| 录制 | `LiveRecording` | `liverecordings` | `rec-{sourceName}-{yyyyMMddHHmmss}-{4位随机}` | 不用 `query`/`active`/`batch` 等保留段 |

命名规则说明：

- `douyin-{roomId}` 同时承担 S3 的去重职责；重复创建时 `client.fetch` 已存在，返回 `LIVE_SOURCE_DUPLICATE`。
- 录制名中的时间戳用 UTC，避免服务器时区变化；`4位随机` 只用于同一秒并发创建，不参与业务语义。
- 资源名一旦创建不可修改；`roomId`/`platform` 在 spec 中视为不可变字段，PATCH 修改会被拒绝或忽略并写 `status.message`。

### 4.2 LiveSource

```java
@GVK(group = LiveSource.GROUP, version = LiveSource.VERSION,
     kind = LiveSource.KIND, plural = "livesources", singular = "livesource")
public class LiveSource extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "LiveSource";

    private Spec spec = new Spec();
    private Status status = new Status();

    @Data
    public static class Spec {
        private LivePlatform platform = LivePlatform.DOUYIN;
        private String roomId;              // 必填，纯数字
        private String roomUrl;             // 规范化后的 https://live.douyin.com/{roomId}
        private String alias;               // 用户别名，可空
        private boolean enabled = true;
        private boolean autoRecord = true;
        private LiveRecordingFormat recordingFormat = LiveRecordingFormat.TS;
        private int segmentSeconds = 30;    // 10 或 30，仅本地分片时长
        private int mergeIntervalSeconds = 3600; // 0 表示关闭周期合并，仅录制结束合并
        private int checkIntervalSeconds = 60; // 30..3600
        private String storagePolicy;      // 空则解析默认策略；仍须为非 LOCAL 对象存储
    }

    @Data
    public static class Status {
        private LiveSourceState state = LiveSourceState.IDLE;
        private String message;
        private String roomTitle;
        private String anchorName;
        private String anchorAvatar;
        private String anchorId;
        private boolean live;
        private String liveStatus;          // 原始 room.status/liveStatus，供排查
        private Instant lastResolvedAt;
        private Instant lastLiveAt;
        private Instant lastOfflineAt;
        private Instant lastStreamAt;
        private Instant nextCheckAt;
        private Instant cooldownUntil;
        private int resolveFailures;
        private boolean rateLimited;
        private String lastResolveCode;
        private String lastResolveError;
        private String currentRecording;
    }
}
```

字段约束：

| 字段 | 约束 | 校验位置 |
| --- | --- | --- |
| `roomId` | `[0-9]{1,20}`，不可变 | `LiveRoomLinkParser` + `LiveSourceValidator` |
| `roomUrl` | 仅 `https://live.douyin.com/{roomId}`，不可变 | 同上 |
| `segmentSeconds` | 只允许 10 或 30，缺省 30；只影响本地分片，不影响最终成品时长 | `LiveSourceValidator` |
| `mergeIntervalSeconds` | 0 或 60–21600，缺省 3600；0 表示不做周期合并、只在录制结束时合并 | `LiveSourceValidator` |
| `checkIntervalSeconds` | 30–3600，缺省 60 | `LiveSourceValidator` |
| `storagePolicy` | 可空；非空时必须是就绪且 `type != LOCAL` 的 `StoragePolicy`；`autoRecord=true` 时最终解析出的策略也必须是对象存储 | `LiveStoragePolicyResolver` + `LiveSourceValidator`（创建/更新/开录前） |
| `enabled` | false 时停止监控并停录 | `LiveSourceReconciler` |

### 4.3 LiveRecording

```java
@GVK(group = LiveRecording.GROUP, version = LiveRecording.VERSION,
     kind = LiveRecording.KIND, plural = "liverecordings", singular = "liverecording")
public class LiveRecording extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "LiveRecording";

    private Spec spec = new Spec();
    private Status status = new Status();

    @Data
    public static class Spec {
        private String sourceName;          // 必填
        private LivePlatform platform;
        private String roomId;
        private String roomTitle;
        private String anchorName;
        private LiveRecordingFormat format = LiveRecordingFormat.TS;
        private Instant startedAt;          // 请求开始时间
        private boolean auto;               // 自动触发/手动触发
        private String requestedBy;         // 手动触发时的操作者
        private String storagePolicy;       // 创建时快照，录制中不改
        private int segmentSeconds = 30;    // 10 或 30，创建时从源快照
        private int mergeIntervalSeconds = 3600; // 创建时从源快照
        private String retryOf;             // 重试来源录制名，可空
    }

    @Data
    public static class Status {
        private LiveRecordingState state = LiveRecordingState.QUEUED;
        private String message;
        private String errorCode;
        private Instant startedAt;          // 实际开始
        private Instant endedAt;
        private Instant lastPacketAt;
        private Instant lastWriteAt;
        private Instant heartbeatAt;
        private String ownerInstance;
        private Instant stopRequestedAt;
        private long durationMillis;
        private long totalBytes;
        private int segmentCount;           // 已关闭的本地分片总数，仅用于展示
        private int partCount;              // 已创建成品数，等于 parts.size()
        private List<Part> parts = new ArrayList<>(); // 只保存成品元数据，不保存分片元数据
        private long localBytes;            // 当前本地分片+成品占用，用于磁盘水位展示
        private int attempts;
        private int resolveAttempts;
        private int cleanupAttempts;      // finalizer 清理失败次数，跨进程保留
    }

    @Data
    public static class Part {
        private int index;                  // 从 1 开始，与 part-00001.ts 文件名对应
        private String filename;            // part-00001.ts
        private PartState state = PartState.MERGING; // 合并中或已合并待上传，以成品文件是否存在区分
        private Instant startedAt;          // 首个分片开始时间
        private Instant endedAt;            // 末个分片结束时间
        private long durationMillis;
        private long size;
        private int segmentCount;           // 本成品包含的分片数
        private String storageFile;         // StorageFile 资源名
        private String errorCode;
        private String message;
    }
}
```

状态枚举：

```java
public enum LivePlatform { DOUYIN }

public enum LiveSourceState {
    IDLE, MONITORING, LIVE, RECORDING, OFFLINE, ERROR, DISABLED
}

public enum LiveRecordingState {
    QUEUED, RECORDING, FINALIZING, COMPLETED, FAILED, CANCELED
}

public enum LiveRecordingFormat { TS }

public enum PartState { MERGING, UPLOADING, UPLOADED, FAILED }
```

`MERGING` 覆盖“合并中”和“已合并待上传”两个阶段：成品文件存在即已合并，不存在则用分片重建；不额外增加状态，避免恢复逻辑分叉。

### 4.4 示例 YAML

```yaml
apiVersion: pudans.tolim.cn/v1alpha1
kind: LiveSource
metadata:
  name: douyin-106288756897
spec:
  platform: DOUYIN
  roomId: "106288756897"
  roomUrl: https://live.douyin.com/106288756897
  alias: 示例直播间
  enabled: true
  autoRecord: true
  recordingFormat: TS
  segmentSeconds: 30
  mergeIntervalSeconds: 3600
  checkIntervalSeconds: 60
  storagePolicy: s3-live
status:
  state: MONITORING
  live: false
  nextCheckAt: "2026-06-01T10:00:00Z"
```

```yaml
apiVersion: pudans.tolim.cn/v1alpha1
kind: LiveRecording
metadata:
  name: rec-douyin-106288756897-20260601100000-a1b2
spec:
  sourceName: douyin-106288756897
  platform: DOUYIN
  roomId: "106288756897"
  roomTitle: 示例直播间
  format: TS
  startedAt: "2026-06-01T10:00:00Z"
  auto: true
  storagePolicy: s3-live
  segmentSeconds: 30
  mergeIntervalSeconds: 3600
status:
  state: RECORDING
  startedAt: "2026-06-01T10:00:01Z"
  lastPacketAt: "2026-06-01T11:01:30Z"
  segmentCount: 122
  partCount: 1
  localBytes: 31457280
  parts:
    - index: 1
      filename: part-00001.ts
      state: UPLOADED
      startedAt: "2026-06-01T10:00:01Z"
      endedAt: "2026-06-01T11:00:03Z"
      durationMillis: 3602000
      size: 1887436800
      segmentCount: 120
      storageFile: live-rec-douyin-106288756897-20260601100000-a1b2-00001
```

### 4.5 索引注册

必须在 `SchemeInitializer` 注册，否则通用 controller/路由/索引查询都不存在。建议索引：

```java
schemeManager.register(LiveSource.class, is -> {
    is.add(IndexSpecs.<LiveSource, LivePlatform>single("spec.platform", LivePlatform.class)
            .indexFunc(source -> source.getSpec().getPlatform()));
    is.add(IndexSpecs.<LiveSource, String>single("spec.roomId", String.class)
            .indexFunc(source -> source.getSpec().getRoomId()));
    is.add(IndexSpecs.<LiveSource, Boolean>single("spec.enabled", Boolean.class)
            .indexFunc(source -> source.getSpec().isEnabled()));
    is.add(IndexSpecs.<LiveSource, LiveSourceState>single("status.state", LiveSourceState.class)
            .indexFunc(source -> source.getStatus().getState()));
    is.add(IndexSpecs.<LiveSource, Instant>single("status.nextCheckAt", Instant.class)
            .indexFunc(source -> source.getStatus().getNextCheckAt()));
});

schemeManager.register(LiveRecording.class, is -> {
    is.add(IndexSpecs.<LiveRecording, String>single("spec.sourceName", String.class)
            .indexFunc(recording -> recording.getSpec().getSourceName()));
    is.add(IndexSpecs.<LiveRecording, LivePlatform>single("spec.platform", LivePlatform.class)
            .indexFunc(recording -> recording.getSpec().getPlatform()));
    is.add(IndexSpecs.<LiveRecording, LiveRecordingState>single("status.state", LiveRecordingState.class)
            .indexFunc(recording -> recording.getStatus().getState()));
    is.add(IndexSpecs.<LiveRecording, Instant>single("status.startedAt", Instant.class)
            .indexFunc(recording -> recording.getStatus().getStartedAt()));
    is.add(IndexSpecs.<LiveRecording, Instant>single("status.endedAt", Instant.class)
            .indexFunc(recording -> recording.getStatus().getEndedAt()));
});
```

索引只建查询会用到的字段。`status.parts` 不进索引；录制详情直接读对象，不做按成品查询。

### 4.6 为什么不用第三张表/第三个 CRD

- 成品元数据放 `LiveRecording.status.parts`：默认 1 小时一个成品，单次录制上限 6 小时，最多 6 个成品；即使把合并间隔调到 10 分钟，也只有几十个，远小于资源对象上限。避免 `LivePart` 的额外 controller、RBAC、路由和清理逻辑。
- 分片元数据不放进 CRD：30 秒一片、6 小时是 720 片，10 秒一片是 2160 片；逐片写 status 既放大资源对象，又增加写库次数。分片只在本地目录的 `segments.jsonl` 中登记，进程崩溃后由启动恢复扫描。
- 本地目录固定为 `${pudans.live.recorder.work-dir}/{recordingName}/`：`segment-00001.ts`、`part-00001.ts.tmp`、`part-00001.ts`、`segments.jsonl`、`parts.jsonl`。分片和未上传成品都只在这里；录制记录只登记成品。
- 不为直播建关系表：Extension 的资源名就是外键；`sourceName` 只做逻辑引用，不建数据库约束。
- 不把流地址持久化：流地址短命且带签名参数，持久化既无用又增加泄漏面；只在 `LiveRoomInfo`/内存任务里存在。

## 5. 状态机

### 5.1 直播源状态

| 当前 | 事件 | 下一状态 | 动作 |
| --- | --- | --- | --- |
| `IDLE` | reconciler 接受且 `enabled=true` | `MONITORING` | 加入 registry，`nextCheckAt=now` |
| `IDLE` | `enabled=false` | `DISABLED` | 不加入 registry |
| `MONITORING` | 解析成功且在线 | `LIVE` 或 `RECORDING` | 写标题/主播/流时间；满足自动录制则创建录制 |
| `MONITORING` | 解析成功且离线 | `OFFLINE` | 写 `lastOfflineAt`，按离线间隔重查 |
| `MONITORING` | 连续失败 ≥3 | `ERROR` | 写错误码，进入退避 |
| `LIVE` | 解析离线 | `OFFLINE` | 停掉当前录制（若有），清 `currentRecording` |
| `LIVE` | 自动录制开启且无活动录制 | `RECORDING` | 创建录制任务 |
| `RECORDING` | 录制结束/失败/被停 | `LIVE` 或 `OFFLINE` | 清 `currentRecording`；失败进 `ERROR` 需按错误码区分 |
| `OFFLINE` | 解析在线 | `LIVE`/`RECORDING` | 写 `lastLiveAt` |
| `ERROR` | 解析成功 | `LIVE`/`OFFLINE` | 清 `resolveFailures` |
| `ERROR` | 连续失败继续 | `ERROR` | 退避上限 15 分钟 |
| 任意 | `enabled=false` | `DISABLED` | 移出 registry；停当前录制 |
| `DISABLED` | `enabled=true` | `MONITORING` | 重新加入 registry，`nextCheckAt=now` |

状态语义澄清：

- `MONITORING` 是“已启用、尚未得到首次解析结果或等待下一次检查”，不是“正在拉流”。
- `LIVE` 是“已确认在线、但没有活动录制”，常见于 `autoRecord=false` 或录制刚结束。
- `RECORDING` 是“已确认在线且有活动录制任务”。
- `OFFLINE` 是“解析成功但当前未开播”，不是错误。
- `ERROR` 是“连续解析失败达到阈值”，仍保留退避重试，不自动禁用。

### 5.2 录制状态

| 当前 | 事件 | 下一状态 | 动作 |
| --- | --- | --- | --- |
| `QUEUED` | 准入成功 | `RECORDING` | 写 `startedAt`、`ownerInstance`；开始拉流 |
| `QUEUED` | 队列满/超过等待上限 | `FAILED` | `LIVE_RECORDING_LIMIT_REACHED` |
| `QUEUED` | 用户停止 | `CANCELED` | 不产生本地文件与成品 |
| `RECORDING` | 用户停止 | `FINALIZING` | 关闭当前分片，合并剩余分片并上传成品 |
| `RECORDING` | 房间下播 | `FINALIZING` | 正常收尾，最终 `COMPLETED` |
| `RECORDING` | 流失败且重试耗尽 | `FAILED` | `LIVE_STREAM_FAILED`；先尽力合并/上传已有关闭分片，失败也不丢本地文件 |
| `RECORDING` | 磁盘水位低于阈值或本地成品积压 | `FAILED` | `LIVE_DISK_FULL` 或 `LIVE_STORAGE_BACKLOG`；保留分片/成品待恢复 |
| `RECORDING` | 存储策略不可用（LOCAL、未就绪、缺失） | `FAILED` | `LIVE_STORAGE_POLICY_INVALID`/`LIVE_STORAGE_POLICY_MISSING`，开录前已校验，此处只兜底 |
| `FINALIZING` | 所有成品上传成功且已删除本地分片/成品 | `COMPLETED` | 写 `endedAt`/`durationMillis`/`totalBytes` |
| `FINALIZING` | 没有产生任何有效分片 | `CANCELED` | `LIVE_NO_DATA`，不生成成品 |
| `FINALIZING` | 合并失败且重试耗尽 | `FAILED` | `LIVE_MERGE_FAILED`，保留本地分片与已合并成品 |
| `FINALIZING` | 任一成品上传失败且重试耗尽 | `FAILED` | `LIVE_STORAGE_FAILED`，保留本地成品与尚未合并的分片 |
| `FINALIZING` | 停止超时 | `FAILED` | `LIVE_STOP_TIMEOUT`，强制释放 native；后台继续收尾，不丢文件 |
| `FAILED` | 上传重试 | `FINALIZING` | 只重试未上传成品与未合并分片，不重新拉流 |
| `FAILED` | 拉流重试 | 新建 `LiveRecording` | `retryOf=oldName`；新旧录制目录独立，旧记录与本地文件保持不变，旧数据可另走 `UPLOAD` 或删除 |

终态：`COMPLETED`、`FAILED`、`CANCELED`。终态不允许回到 `RECORDING`；重试要么同记录重传，要么新建记录。

### 5.3 状态写回与并发

- `LiveSourceReconciler` 和 `LiveMonitorWorker` 都可能写同一资源，必须走 `LiveSourceStatusService.update(name, mutation)`：fetch → 修改 → update，遇到版本冲突重试 3 次（50/150/300ms），仍失败记日志并等下一 tick。
- 录制状态写回分两类：
  - 状态迁移、分片关闭、合并开始/完成、成品上传结果：立即写。
  - `lastPacketAt`/`heartbeatAt`：内存更新，节流每 10s 合并写一次；不得每包写库。
- `client.update` 是阻塞调用；在 Reactor 链路中用 `Mono.fromCallable(...).subscribeOn(boundedElastic())`，不占用事件循环。

## 6. 解析设计（R1–R6）

### 6.1 R1 链接提取

只接受精确域名，且**只用解析出的 roomId 重建 URL 去请求**，不直接请求用户输入的原 URL。这样既满足“只接受 live.douyin.com”，也从根上消除 SSRF/重定向绕过。

```java
public final class LiveRoomLinkParser {

    private static final String HOST = "live.douyin.com";
    private static final Pattern ROOM_ID = Pattern.compile("[0-9]{1,20}");

    public ParsedLiveRoom parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException error) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !HOST.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        var path = Optional.ofNullable(uri.getPath()).orElse("");
        var roomId = path.replaceAll("^/+|/+$", "");
        if (!ROOM_ID.matcher(roomId).matches()) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        return new ParsedLiveRoom(LivePlatform.DOUYIN, roomId,
                "https://" + HOST + "/" + roomId);
    }
}
```

规则：

- 拒绝 `http`、其他 host、`userinfo`、非 443 端口、路径多段、非数字 roomId、`URI` 非法。
- 允许查询串和 fragment，但忽略；规范化 URL 不带它们。
- 不做 DNS 解析；请求的是固定 host `live.douyin.com`，且 WebClient `followRedirect(false)`。
- 单测覆盖：`https://live.douyin.com/106288756897`、带尾斜杠、带 query、`http`、`live.douyin.com.evil.com`、带 `user@`、带 `:8080`、`/106288756897/extra`、非数字、空串、非法 URI。

### 6.2 R2 解析适配

#### 请求

- 新建 `LiveWebClientConfiguration`，提供名为 `liveWebClient` 的 `WebClient`：
  - Reactor Netty `HttpClient`：连接超时 5s、响应超时 10s、`followRedirect(false)`。
  - `ExchangeStrategies.maxInMemorySize` 8MB；超过映射 `LIVE_RESOLVE_FAILED`。
  - 固定 UA（参考实现）：`Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36 Edg/109.0.1518.49`。
  - `Accept: text/html,application/xhtml+xml,...`。**默认不发送 Cookie**：公开直播间页面无需登录即可查看；只有 `pudans.live.resolve.cookie` 非空时才加 `Cookie` 头，值来自可选环境变量 `PUDANS_LIVE_DOUYIN_COOKIE`。
  - 绝不把 Cookie、HTML 全文、流地址写日志。
- 只请求 `https://live.douyin.com/{roomId}`。

#### 页面载荷提取

参考实现用 GraalJS 执行页面里的 `self.__pace_f.push(...)` 只为拿到数组参数；本方案用状态机扫描，不执行脚本。

```text
extract(html):
  last = null
  from = 0
  while (i = html.indexOf("self.__pace_f.push(", from)) >= 0:
      open = index of '(' at/after i
      end  = scanBalanced(html, open)     // 字符串感知，处理引号与反斜杠转义
      arg  = html.substring(open + 1, end).trim()
      if arg.startsWith("["): last = arg
      from = end + 1
  if last == null: throw LIVE_RESOLVE_FAILED
  arr = jackson.readTree(last)            // [1, "c:{...}"]
  encoded = arr.get(1).asText()
  colon = encoded.indexOf(':')
  json = colon >= 0 ? encoded.substring(colon + 1) : encoded
  payload = jackson.readTree(json)
  root = payload.isArray() ? payload.get(payload.size() - 1) : payload
  return root
```

实现要点：

- `scanBalanced` 必须识别双引号、单引号、反斜杠转义和嵌套 `[]/{}`；不能用简单 `indexOf("])")`。
- 只取最后一个 `self.__pace_f.push([...])`；多个 push 表示分片，最后一个才包含完整 roomStore。
- `c:` 前缀只去掉第一个冒号前的内容；不要按 `:` 分割整个串。
- 用 Jackson 的 `JsonNode` 映射；字段缺失用 `path(...)` 而不是强转，避免 NPE。
- 全程不执行远程 JS，不引入 GraalJS/Jsoup。

#### 字段映射

```text
root.state.roomStore.roomInfo.room.title              -> title
root.state.roomStore.roomInfo.roomId                 -> roomId
root.state.roomStore.roomInfo.anchor.nickname         -> anchorName
root.state.roomStore.roomInfo.anchor.avatar_thumb.url_list[0] -> anchorAvatar
root.state.roomStore.roomInfo.anchor.sec_uid          -> anchorId
root.state.roomStore.roomInfo.room.status             -> liveStatus（原始值）
root.state.roomStore.roomInfo.web_stream_url.hls_pull_url_map.FULL_HD1/SD1/SD2
root.state.roomStore.roomInfo.web_stream_url.flv_pull_url.FULL_HD1/SD1/SD2
```

- 流地址顺序固定：HLS `FULL_HD1 → SD1 → SD2`，再 FLV `FULL_HD1 → SD1 → SD2`；过滤 null/空白；去重但保持顺序。
- 在线判定：优先 `room.status == 2`；字段缺失时用“至少一个非空流地址”兜底；两者都没有判定离线。保留 `liveStatus` 原始值供排查，不把猜测写死。
- `LiveRoomInfo` 是 `contract/live` 下的不可变 record，包含 `platform/roomId/roomUrl/title/anchor.../live/liveStatus/streams/resolvedAt`。
- `LiveStreamUrl` 含 `protocol（HLS/FLV）`、`quality（FULL_HD1/SD1/SD2）`、`url`。

#### 错误与限流识别

| 上游表现 | 映射 | 处理 |
| --- | --- | --- |
| HTTP 404 | `LIVE_ROOM_NOT_FOUND`（404） | 不重试；源进入 `ERROR` 或提示房间不存在 |
| HTTP 403/429，或页面含验证/滑块/captcha 标记 | `LIVE_RATE_LIMITED`（429） | 进入冷却；不缓存失败结果 |
| HTTP 5xx、超时、连接失败 | `LIVE_RESOLVE_FAILED`（502） | 指数退避；连续 3 次进 `ERROR` |
| 200 但找不到 `self.__pace_f`/JSON 解析失败 | `LIVE_RESOLVE_FAILED` | 同上；记告警，提示页面结构可能变化 |
| 200 且解析成功但无流 | `LiveRoomInfo(live=false)` | 正常离线，不报错 |

`LIVE_ROOM_OFFLINE` 只用于“要求必须开播”的动作（例如手动录制、从链接创建后立即录制）；自动监控把离线当正常状态。这样不会把每天正常下播记成错误。

### 6.3 R3 短缓存与 single-flight

Redis 键：

| 键 | 值 | TTL | 说明 |
| --- | --- | --- | --- |
| `pudans:live:resolve:{platform}:{roomId}` | `LiveRoomInfo` JSON | 在线 45s；离线 20s | 只缓存解析成功的结果 |
| `pudans:live:resolve:lock:{platform}:{roomId}` | `instanceId` | 15s | SET NX PX，single-flight |
| `pudans:live:cooldown:{platform}:{roomId}` | 冷却截止时间戳 | 10m 起，翻倍上限 1h | 限流冷却 |
| `pudans:live:ratelimit:streak:{platform}:{roomId}` | 连续限流次数 | 1h | 用于退避与告警 |
| `pudans:live:alert:{type}:{subject}` | `1` | 10m | 告警去重 |

解析流程：

```text
resolve(roomId, force):
  if !force:
    cached = cache.get(roomId)
    if cached != null: metric(cache.hit); return cached
  if cooldown active:
    metric(rate_limited); throw LIVE_RATE_LIMITED
  lock = SET NX lockKey instanceId PX 15s
  if lock acquired:
    try:
      info = doResolve(roomId)            // WebClient + boundedElastic 解析
      if info.live: cache.put(info, 45s) else cache.put(info, 20s)
      clear rate-limit streak
      return info
    finally:
      releaseLock with Lua compare-and-delete
  else:
    wait for cache up to 2s (poll 100ms, Reactor delay)
    if cache appears: metric(cache.hit); return it
    return doResolve(roomId)             // 锁持有者死亡/超时，降级直连
```

- 等待用 `Mono.delay` 重复查缓存，不用 `Thread.sleep` 占线程。
- 锁释放用 Lua：`if redis.call("get", KEYS[1]) == ARGV[1] then return redis.call("del", KEYS[1]) else return 0 end`，避免误删别人的锁。
- Redis 不可用：捕获连接异常，记 `live.resolve.redis.degraded` 计数并直接解析；不阻断监控。
- 缓存反序列化失败：删除坏键并按 miss 处理，不抛给调用方。

### 6.4 R4 降级、错误码、冷却

- 解析失败：`LIVE_RESOLVE_FAILED`，`status.lastResolveError` 记录简短原因；连续失败 `3` 次进 `ERROR`。
- 未开播：正常结果 `live=false`；手动录制返回 `LIVE_ROOM_OFFLINE`。
- 限流：`LIVE_RATE_LIMITED`；写冷却键，`status.cooldownUntil`、`rateLimited=true`；冷却期内自动监控跳过解析（只更新 `nextCheckAt`），不反复触发风控。
- 冷却时长：基础 10 分钟，连续限流翻倍：10m → 20m → 40m → 60m 封顶；解析成功清零。
- 手动解析可以忽略缓存（`force=true`），但不绕过冷却；返回 `429` 并带 `Retry-After` 语义（响应体写 `cooldownUntil`）。
- 冷却是按 `roomId` 的，不按整个应用；一个房间被限流不阻塞其他房间。

### 6.5 R5 录制中流地址重解析

- 只由 `LiveRecorderTask` 在流读取失败/EOF 时触发；触发一次“强制解析”（`force=true`，绕过缓存，但仍受冷却和 single-flight 约束）。
- 每次中断最多重解析 `2` 次；两次结果都与当前地址相同，则不再把时间浪费在解析上，转入网络退避。
- 新地址列表与当前列表不同：关闭当前 `FFmpegFrameGrabber`，关闭当前分片，强制把“当前分片”切成成品边界（旧地址和参数的分片不能与新地址混在一个成品里），打开新地址，开始新分片，**同一个 `LiveRecording` 继续**；成品按正常流程合并上传。
- 新地址解析成功但仍无流 / `live=false`：按“房间下播”收尾，`FINALIZING → COMPLETED`，不标失败。
- 限流：`LIVE_RATE_LIMITED`，录制无法继续且冷却期内不允许自动解析，标 `FAILED`，错误码 `LIVE_RATE_LIMITED`，保留本地分片和已上传成品。
- 解析失败：退避重试（与网络共用退避），超过上限标 `FAILED`/`LIVE_RESOLVE_FAILED`。
- 绝不无限重试旧地址：同一 URL 的网络重试最多 5 次，之后停止。

### 6.6 R6 解析指标

见第 12 节 `LiveMetrics`。解析入口、缓存命中、限流、失败、single-flight 等待分别计数；标签只加 `platform=douyin`，不加 `roomId`，避免高基数。

## 7. 直播源管理（S1–S6）

### 7.1 S1 以链接创建

端点：`POST /apis/api.pudans.tolim.cn/v1alpha1/livesources/from-url`

请求：

```json
{
  "roomUrl": "https://live.douyin.com/106288756897",
  "alias": "示例直播间",
  "autoRecord": true,
  "segmentSeconds": 30,
  "mergeIntervalSeconds": 3600,
  "storagePolicy": "s3-live"
}
```

流程：

1. `LiveRoomLinkParser` 提取 `roomId`、规范化 `roomUrl`；非法 → `400 LIVE_ROOM_LINK_INVALID`。
2. 资源名 `douyin-{roomId}`；`client.fetch` 已存在 → `409 LIVE_SOURCE_DUPLICATE`。
3. 存储策略校验（只在 `autoRecord=true` 时强制）：`LiveStoragePolicyResolver.validateForSource(storagePolicy)`。
   - 未填 → 解析默认策略；没有默认策略 → `503 LIVE_STORAGE_POLICY_MISSING`，不创建，提示管理员配置对象存储默认策略。
   - 默认策略或显式策略不存在、未就绪、`type=LOCAL` → `400 LIVE_STORAGE_POLICY_INVALID`，不创建；内置 `storage-policy-default` 是 `local`，不能直接用于直播录制。
   - `autoRecord=false` 时允许 `storagePolicy` 为空；填了仍必须是就绪的对象存储，避免留下“以后一定录不了”的源。
4. `LiveResolveService.resolvePreview(roomId)`（默认不带 Cookie）：
   - 在线：拿到标题/主播/流地址。
   - 离线：拿到标题/主播（可能为空），`live=false`；**仍创建**源，状态 `OFFLINE`，等监控继续检查。
   - 限流：`429 LIVE_RATE_LIMITED`，不创建；提示稍后重试。
   - 解析失败：`502 LIVE_RESOLVE_FAILED`，不创建。
5. 创建 `LiveSource`：`spec` 填 roomId/roomUrl/alias/autoRecord/recordingFormat/segmentSeconds/mergeIntervalSeconds/checkIntervalSeconds/storagePolicy；`status.state` 在线为 `LIVE`（无录制）或 `MONITORING`，离线为 `OFFLINE`；写标题/主播/解析时间/`nextCheckAt`。
6. 审计：`AuditWebFilter` 会记录 POST `livesources/from-url`；再用 `AuditService.recordChild` 补真实资源名 `douyin-{roomId}`（路径里没有资源名）。
7. 返回 201 + 创建后的 `LiveSource`（或 `LiveSourceView`）。

为什么离线也创建：用户的目标是“监控这个房间”，离线是常态；只有解析失败/限流才拒绝，避免创建后永远无法解析的垃圾源。

### 7.2 S2 CRUD

通用 Extension 路由已提供：

| 方法 | 路径 | 用途 | 默认 RBAC |
| --- | --- | --- | --- |
| GET | `/livesources` | 列表 | `livesources` get/list |
| GET | `/livesources/{name}` | 详情 | `livesources` get |
| PUT/PATCH | `/livesources/{name}` | 更新别名/开关/录制参数 | `livesources` update |
| DELETE | `/livesources/{name}` | 删除 | `livesources` delete |

- 创建**不**走通用 `POST /livesources`，只走 `from-url`；RBAC 默认不授予 `livesources` create。这样不会绕过 roomId 解析/去重/解析校验。
- 更新字段限制：`roomId`/`platform`/`roomUrl` 不可变；`segmentSeconds` 只允许 10/30；`mergeIntervalSeconds` 只允许 0 或 60–21600；`storagePolicy` 必须是就绪对象存储（`autoRecord=true` 时不可为空，且解析结果必须是对象存储）；非法值由 `LiveSourceValidator` 拒绝（端点层）或写 `status.message` 并停止监控（通用 PATCH 无法在框架层拒绝时）。
- 把 `autoRecord` 从 false 改成 true 时，按创建同样规则校验存储策略；不通过则拒绝 PATCH，不能等开播才发现。
- 录制中的记录继续使用创建时的 `storagePolicy`、`segmentSeconds`、`mergeIntervalSeconds` 快照；PATCH 只影响下一次录制。
- 前端只暴露“别名/自动录制/分片时长/合并间隔/存储策略/启用开关”的 PATCH，不暴露 raw JSON 编辑。

### 7.3 S3 重复创建防护

- 资源名 `douyin-{roomId}` 是唯一键；创建前 `fetch` 检查，创建时仍可能并发，最终由 Extension 存储主键冲突兜底，映射 `409 LIVE_SOURCE_DUPLICATE`。
- 比“只允许一个启用源”更强：禁用源也不能重复创建；要重新启用就 PATCH 原资源。
- 不额外建数据库唯一索引；资源名就是主键。

### 7.4 S4 状态机与事件

- 状态机实现为纯函数 `LiveSourceStateMachine.next(current, event)`，输入事件：`ENABLED`、`DISABLED`、`RESOLVED_LIVE`、`RESOLVED_OFFLINE`、`RESOLVE_FAILED`、`RECORDING_STARTED`、`RECORDING_ENDED`、`RECORDING_FAILED`；输出下一状态和需要写回的字段。
- 每次状态变化发 `DomainEventPublisher.publish("live.source.state.changed", payload, traceFlowId)`：
  `payload = {sourceName, roomId, from, to, reason, at}`。
- 同时发 Spring `ApplicationEvent`（`LiveSourceStateChangedEvent`）供内部监听（监控看板缓存、指标 gauge）。
- 事件失败不影响主流程；`DomainEventPublisher` 队列满会丢事件并计现有指标，不重试。

### 7.5 S5 启停监控

- `LiveSourceReconciler` 在 `spec.enabled` 变化时：
  - true：`registry.upsert(source)`，`state=MONITORING`，`nextCheckAt=now`。
  - false：`registry.remove(name)`，`state=DISABLED`，`currentRecording` 若有则请求停止录制。
- 不在每个源上建独立定时器；`LiveMonitorWorker` 每 `tick=5s` 扫 registry 中到期的源，避免几百个定时器。
- 应用启动时 `LiveSourceRegistry` 用 `client.listAll(LiveSource.class, ...)` 全量加载 `enabled=true` 的源；随后由 Reconciler/watch 增量维护。

### 7.6 S6 删除

采用“先停录、再删除”的语义，避免 DELETE 返回成功但资源仍卡在 finalizer：

1. 通用 `DELETE /livesources/{name}` 由框架写 `metadata.deletionTimestamp`。
2. `LiveSourceReconciler.reconcile` 检测 `isDeleted`：
   - 立即 `registry.remove(name)`，不再开播检测。
   - 查询 `LiveRecording` `spec.sourceName=name` 且 `status.state in (QUEUED, RECORDING, FINALIZING)`。
   - 有活动录制：对每条调用 `LiveRecordingService.requestStop(recordingName)`（写 `status.stopRequestedAt` + Redis 停止键 + 本地 `requestStop`），`return Result.requeue(Duration.ofSeconds(5))`；最多等 60s。
   - 无活动录制：`removeFinalizers(metadata, Set.of("pudans.tolim.cn/live-source-cleanup"))` + `client.update`；GC 物理删除。
   - 超过 60s 仍有活动录制：强制停止并标记录制 `FAILED`/`LIVE_STOP_TIMEOUT`，然后移除 finalizer；记告警。
3. 历史录制不级联删除：录制是资产，保留 `spec.sourceName` 作为来源标识；删除源后录制仍可查/下载。前端来源列显示“已删除源”。
4. 审计：`AuditWebFilter` 自动记录 DELETE；资源段是 `livesources`，已足够；如需真实名称，Reconciler 不改审计，端点层没有额外动作。

如果产品坚持“有活动录制时拒绝删除并返回 409”，需要新增自定义 `DELETE /livesources/{name}` 与通用路由冲突，方案不采用；当前语义是“先停录再删”，符合需求中的“有则拒绝或先停止录制”。

## 8. 录制设计（C1–C13）

### 8.1 C1 录制任务创建

触发：

- 自动：`LiveMonitorWorker` 解析在线、`source.spec.autoRecord=true`、`status.currentRecording==null`、`enabled=true`。
- 手动：`POST /livesources/{name}/record`，要求当前解析在线；离线返回 `409 LIVE_ROOM_OFFLINE`。

创建：

```java
var recording = new LiveRecording();
recording.getMetadata().setName(recordingName);
recording.getSpec().setSourceName(sourceName);
recording.getSpec().setPlatform(source.getSpec().getPlatform());
recording.getSpec().setRoomId(source.getSpec().getRoomId());
recording.getSpec().setRoomTitle(source.getStatus().getRoomTitle());
recording.getSpec().setAnchorName(source.getStatus().getAnchorName());
recording.getSpec().setFormat(LiveRecordingFormat.TS);
recording.getSpec().setStartedAt(Instant.now());
recording.getSpec().setAuto(auto);
recording.getSpec().setRequestedBy(requestedBy);
recording.getSpec().setStoragePolicy(storagePolicy); // 已校验：非空且非 LOCAL，或解析出的默认对象存储
recording.getSpec().setSegmentSeconds(source.getSpec().getSegmentSeconds());
recording.getSpec().setMergeIntervalSeconds(source.getSpec().getMergeIntervalSeconds());
recording.getStatus().setState(LiveRecordingState.QUEUED);
client.create(recording);
source.getStatus().setCurrentRecording(recordingName);
source.getStatus().setState(LiveSourceState.RECORDING);
```

- 创建前必须通过 `LiveStoragePolicyResolver.resolve(recording.storagePolicy)`：结果为空 → `LIVE_STORAGE_POLICY_MISSING`；策略未就绪、不存在或 `type=LOCAL` → `LIVE_STORAGE_POLICY_INVALID`。校验失败时不创建录制，手动录制直接返回错误；自动录制把源置 `ERROR` 并告警，避免反复开录失败。
- 创建时把源的 `storagePolicy`/`segmentSeconds`/`mergeIntervalSeconds` 快照到录制；录制中不再读源，PATCH 只影响下一次录制。
- 创建后由准入器决定立即开始还是排队；不要让 HTTP 请求等待拉流。
- 同一源通过 `currentRecording` + registry `inFlight` 双重防重；并发创建只有一个成功。

### 8.2 C13 并发限制与准入

配置：`maxConcurrent=2`、`queueCapacity=10`。

```java
public boolean tryAdmit(String recordingName) {
    if (active.get() < maxConcurrent) {
        active.incrementAndGet();
        return true;
    }
    if (queue.size() >= queueCapacity) {
        return false;
    }
    queue.add(recordingName);
    return true;
}
```

- 准入成功：`QUEUED → RECORDING`，提交 `LiveRecorderTask` 到录制调度器。
- 进入队列：保持 `QUEUED`，`status.message="等待录制槽位"`；某任务结束时 `queue.poll()` 启动下一个。
- 队列满：`FAILED`，`LIVE_RECORDING_LIMIT_REACHED`；不静默丢弃。
- 队列中的任务在 `LiveRecordingReconciler` 启动恢复时重新排队；超过 30 分钟仍 `QUEUED` 标 `FAILED`/`LIVE_RECORDER_LOST`。
- 指标 `live.recordings.active`/`live.recordings.queued` 由 `LiveRecorderRegistry` 实时提供。

### 8.3 C2 JavaCV 录制循环

依赖（Phase 0 验证）：

```groovy
// gradle/libs.versions.toml
javacv = "1.5.11"
ffmpeg = "7.1-1.5.11"

// appkernel/build.gradle
implementation(libs.javacv) {
    exclude group: "org.bytedeco", module: "openblas"
    exclude group: "org.bytedeco", module: "opencv"
    exclude group: "org.bytedeco", module: "flycapture"
    exclude group: "org.bytedeco", module: "libdc1394"
    exclude group: "org.bytedeco", module: "libfreenect"
    exclude group: "org.bytedeco", module: "libfreenect2"
    exclude group: "org.bytedeco", module: "librealsense"
    exclude group: "org.bytedeco", module: "librealsense2"
    exclude group: "org.bytedeco", module: "videoinput"
    exclude group: "org.bytedeco", module: "artoolkitplus"
}
implementation("org.bytedeco:javacpp:1.5.11:linux-x86_64")
implementation("org.bytedeco:javacpp:1.5.11:windows-x86_64")
implementation("org.bytedeco:ffmpeg:7.1-1.5.11:linux-x86_64")
implementation("org.bytedeco:ffmpeg:7.1-1.5.11:windows-x86_64")
```

说明：

- `javacv` 主 jar 没有 native；必须显式加 `javacpp` 与 `ffmpeg` 的 classifier。
- 不要用 `javacv-platform`（会拉全部平台/全部模块，镜像体积不可控）。
- 版本目录新增后，在 `appkernel/build.gradle` 写明用途“直播录制流复制”和“仅在录制线程使用，不进入请求路径”。
- Docker 基础镜像 `eclipse-temurin:25-jre` 先验证；若 native 加载缺库，补 `libgomp1`（Debian/Ubuntu 包名）；仍失败则 Phase 0 决策切换到系统 `ffmpeg` CLI（见第 18 节）。

拉流与写包：

```java
public final class LiveRecorderTask {

    private void record(String url) throws Exception {
        var grabber = new FFmpegFrameGrabber(url);
        grabber.setOption("rw_timeout", "15000000"); // 15s，单位微秒
        grabber.setOption("stimeout", "15000000");
        grabber.setOption("protocol_whitelist", "http,https,tcp,tls,crypto"); // 只允许网络协议，阻断 file/concat
        grabber.start();

        var writer = LiveSegmentWriter.open(grabber, workDir, recordingName, 1);
        AVPacket packet = null;
        try {
            while (!stopRequested()) {
                packet = grabber.grabPacket();
                if (packet == null) {
                    break;
                }
                try {
                    if (writer.shouldRotate(packet, grabber)) {
                        writer = writer.rotate(grabber);
                    }
                    writer.record(packet);
                    writer.afterPacket();
                } finally {
                    av_packet_unref(packet);
                }
            }
        } finally {
            writer.close();
            try {
                grabber.stop();
            } finally {
                grabber.release();
            }
        }
    }
}
```

关键点：

- `grabPacket()` 返回的是 grabber 内部复用 `AVPacket`，不是新对象；`recordPacket` 会改 `stream_index` 和 pts/dts，且不 unref。因此 `av_packet_unref(packet)` 必须放在 `finally`，且切段判断必须在 `recordPacket` 之前。
- `writer.record(packet)` 内部调用 `recorder.recordPacket(packet)`；不要在 packet 被 unref 后再访问。
- `FFmpegFrameRecorder.start(grabber.getFormatContext())` 对输入流做 `avcodec_parameters_copy`，即流复制，不重新编码。
- 音频输出流只在 `audioChannels > 0 && audioBitrate > 0 && sampleRate > 0` 时创建；打开 recorder 时按 grabber 参数设置：`setAudioChannels(grabber.getAudioChannels())`、`setSampleRate(grabber.getSampleRate())`、`setAudioBitrate(128000)`（仅当输入有音频）。无音频时不要设置 audio 参数。
- `grabber.setOption("rw_timeout", ...)` 是 FFmpeg 协议层读超时；不同协议支持不同，HLS/FLV 以实测为准；业务层再用 `stream-idle-timeout` 兜底。
- `grabber.start()` / `grabber.stop()` / `release()` 都可能抛异常；`release()` 放 `finally`，防止 native 内存泄漏。
- 不在本循环里发 HTTP、写数据库、上传文件；只做内存状态更新，上传交给独立队列。

### 8.4 C3 本地分片写入（10s/30s）

`LiveSegmentWriter` 职责：

- 在 `${pudans.live.recorder.work-dir}/{recordingName}/` 下创建 `segment-00001.ts`；目录名/文件名只由资源名和数字索引组成，已做路径规范化，禁止路径穿越。
- 打开时设置 `recorder.setFormat("mpegts")`；`recorder.start(grabber.getFormatContext())`；不设置视频 codec，走流复制。
- 首个视频关键帧到达前不创建 recorder（音频包先丢弃），避免段首无关键帧；纯音频流则在第一个音频包时创建。
- 切段条件（纯函数，便于单测）：
  - 已写入至少一个包；
  - 当前包是视频关键帧（`packet.stream_index() == videoStreamIndex && (packet.flags() & AV_PKT_FLAG_KEY) != 0`）；
  - 距本段首个包 `>= segmentSeconds`（只允许 10 或 30）。
- 切段动作在 `recordPacket` 之前：`writer.closeCurrent()`（`recorder.stop/release`，flush 文件）→ 计算 size/duration → 在本地 `segments.jsonl` 追加一行 `{index,filename,startedAt,endedAt,durationMillis,size}` → 打开下一段 → 再写当前包。
- 分片元数据只写本地 `segments.jsonl`，不写 `LiveRecording.status`；`status.segmentCount` 每 30s 节流更新一次，仅用于展示进度，丢一次也不影响恢复。
- 切段还应在分辨率/音频参数变化、流地址变化（R5）时强制触发，避免同一 recorder 参数与实际流不匹配，也避免把两个 CDN/两套参数的包混进同一个成品。
- 分片关闭后只通知 `LivePartMerger` 有新分片可合并；**不上传、不删除**。删除只发生在合并成功之后。
- 录制结束时 `writer.close()` 必须 flush；若最后一段无关键帧或 0 字节，删除空文件，不写入 `segments.jsonl`，不计入 `segmentCount`。
- 总时长上限 `max-duration`（默认 6h）到点后正常收尾，防止无限录制。
- 录制线程不执行合并、上传、写数据库；这些都在 `live-merge`/`live-upload` 线程池，避免 JavaCV 读包被磁盘 I/O 拖慢。

### 8.5 C4/C5 重解析与退避

```text
onStreamFailure:
  if stopRequested: finish normally
  if resolveAttempts < 2:
     info = resolve(force=true)
     resolveAttempts++
     if info.live && urls != currentUrls:
        currentUrls = info.streams
        streamRetries = 0
        resolveAttempts = 0
        closeSegmentAndForcePartBoundary(); reopen new stream; continue
     if !info.live:
        finish COMPLETED (reason=OFFLINE); return
  if streamRetries >= maxRetries:
     fail LIVE_STREAM_FAILED; return
  streamRetries++
  sleep(backoff(streamRetries))   // 1s,2s,4s,8s,16s,30s 封顶，±20% 抖动
  reopen same currentUrls
```

- 网络抖动与地址过期分开：先判断“重解析后地址是否变化”，变化才换地址；没变化就是网络问题，走退避。
- 重解析最多 2 次/中断；网络重试最多 5 次；总中断时间超过 `stream-idle-timeout=30s` 直接收尾，避免卡死。
- 重解析失败按 `LIVE_RESOLVE_FAILED`；限流按 `LIVE_RATE_LIMITED`；两者都标 `FAILED`，保留本地分片、已上传成品和未上传成品。
- 断流超过阈值（默认 60s）发 `live.stream.interrupted`；恢复发 `live.stream.recovered`。
- 地址变化时关闭当前分片并强制成品边界：旧地址的分片先合并成一个成品，再打开新地址；不把两个不同 CDN 的包写进同一个 TS（时间基/参数可能不同）。

### 8.6 C6 手动停止

端点：`POST /liverecordings/{name}/stop`

1. `QUEUED`：直接 `CANCELED`，从准入队列移除。
2. `RECORDING`：`LiveRecordingService.requestStop(name)` 写 `status.stopRequestedAt=now`、`state=FINALIZING`，设置 Redis `pudans:live:recording:stop:{name}`（TTL 1h），并调用本地 `LiveRecorderRegistry.requestStop(name)`；owner 任务每 1s 检查 Redis 键，因此任意实例都能停止。
3. `FINALIZING`：幂等返回当前状态，不重复停止。
4. 终态：返回 `409 LIVE_RECORDING_NOT_ACTIVE`。
5. 任务收到停止信号：跳出 packet 循环 → `writer.close()` 关闭当前分片 → 等待 `LivePartMerger` 合并剩余分片、`LivePartUploader` 上传成品（最多 5 分钟）→ 删除停止键 → `COMPLETED`；`finally` 中 `grabber.stop/release`。
6. 超过 `stop-timeout=30s` 仍未关闭 native：强制关闭 grabber/recorder，`FAILED`/`LIVE_STOP_TIMEOUT`，后台继续合并/上传，保留本地分片和成品。
7. 停止请求落 `status.stopRequestedAt` 是为了进程重启后仍能识别“用户已请求停止”；恢复逻辑据此把 `RECORDING` 收尾为 `FAILED` 或尝试上传。

### 8.7 C7 分片合并与成品归档

本节把 C3（本地分片）和 C7（成品归档）接起来：**分片只落本地，成品只进对象存储**；合并成功后立即删分片，上传成功后立即删本地成品。

#### 8.7.1 本地目录与清单

```text
${pudans.live.recorder.work-dir}/{recordingName}/
  segments.jsonl
  parts.jsonl
  segment-00001.ts
  part-00001.ts.tmp
  part-00001.ts
```

- `segments.jsonl`：每关闭一个分片追加一行 `{index,filename,startedAt,endedAt,durationMillis,size}`；只用于恢复，不进 CRD、不通过 API 暴露。
- `parts.jsonl`：每合并出一个成品追加一行 `{index,filename,startedAt,endedAt,durationMillis,size,segmentCount,state}`；上传成功后追加 `state=UPLOADED` 或直接在原行更新。
- 清单采用“追加 + fsync”而不是每包更新；崩溃后以“文件实际存在 + 清单行”为准，坏行忽略并告警。
- 所有文件名由资源名和数字索引拼成，索引为 5 位补零；拼接前做路径规范化，禁止 `..`、绝对路径、分隔符注入。

#### 8.7.2 合并触发

`LivePartMerger` 每个录制一个串行队列，触发条件：

- 周期：距上次合并或录制开始达到 `mergeIntervalSeconds`（默认 3600s；0 表示不做周期合并）。
- 收尾：用户停止、房间下播、拉流失败、重解析改变地址（R5）时强制合并当前已关闭分片。
- 磁盘保护：`status.localBytes` 超过 `max-local-bytes`、或未合并分片数超过 `max-local-segments` 时强制合并。
- 录制线程只负责“关闭当前分片并投入合并队列”；合并线程不读 grabber、不碰当前正在写的分片。

#### 8.7.3 合并算法

```java
public LiveRecording.Part merge(LiveRecording recording, boolean force) {
    var closed = segmentManifest.closedSegments(recording); // 已关闭且文件存在的分片
    if (closed.isEmpty()) {
        return null;
    }
    int index = status.getPartCount() + 1;
    var part = new LiveRecording.Part();
    part.setIndex(index);
    part.setFilename("part-%05d.ts".formatted(index));
    part.setState(PartState.MERGING);
    part.setStartedAt(closed.getFirst().getStartedAt());
    part.setEndedAt(closed.getLast().getEndedAt());
    part.setSegmentCount(closed.size());
    status.getParts().add(part);
    appendPart(part); // 先写清单，崩溃后可发现半成品

    var tmp = partPath.resolve(part.getFilename() + ".tmp");
    try (var out = FileChannel.open(tmp, CREATE_NEW, WRITE)) {
        for (var segment : closed) {
            try (var in = FileChannel.open(segmentPath(segment), READ)) {
                long position = 0;
                while (position < in.size()) {
                    position += in.transferTo(position, in.size() - position, out);
                }
            }
        }
        out.force(true);
    }
    Files.move(tmp, partPath.resolve(part.getFilename()), StandardCopyOption.ATOMIC_MOVE);
    part.setSize(Files.size(partPath.resolve(part.getFilename())));
    part.setDurationMillis(closed.stream().mapToLong(LiveSegment::durationMillis).sum());
    appendPart(part); // 成品已落盘；state 保持 MERGING，等待上传

    // 只有成品落盘并 fsync 后才允许删分片；删失败只告警，恢复时按清单重删
    for (var segment : closed) {
        Files.deleteIfExists(segmentPath(segment));
    }
    return part;
}
```

实现要求：

- 按分片索引升序复制，保证 TS 时间顺序；不做解码/转码，仍是流复制后的字节拼接。
- `transferTo` 分块复制，JVM 堆只占几十 KB；不用 `Files.readAllBytes`，不把整小时成品读进内存。
- `part-*.ts.tmp` 先写、`force(true)`、再原子改名；失败时删 tmp，原分片保留，状态置 `LIVE_MERGE_FAILED`。
- 删除分片放在“成品已落盘、清单已 fsync”之后；进程在删除中途崩溃时，恢复逻辑发现成品已存在，删除残留分片即可，不会重复合并。
- 合并失败不丢分片；同一录制可重试合并，不重新拉流。

#### 8.7.4 成品上传

`LivePartUploader` 每个成品独立上传，串行队列，避免对象存储并发打满：

- 上传前再次校验策略：`type != LOCAL`、`status.ready=true`；策略在录制中被删除或改为本地，直接 `LIVE_STORAGE_POLICY_INVALID`，不尝试本地兜底。
- 先读策略的 `chunkSize`：成品大小 `<= policy.chunkSize` 时调用现有 `StorageService.upload(StorageWriteRequest)`；该路径不会触发 `StorageIo.spool`，因为只有 `size > chunkSize` 才走 spool。
- 成品更大时使用现有分片会话：`StorageService.initiateUpload` 拿到 `ticket`，按 `ticket.chunkSize()` 从本地文件逐段 `putPart`，最后 `completeUpload`。这条路径对原生 multipart 驱动直接上传文件 range，不复制整文件到临时目录。
- 驱动不支持原生 `MULTIPART_UPLOAD` 且成品大于策略 `chunkSize` 时，拒绝该策略（`LIVE_STORAGE_POLICY_INVALID`，提示换支持分片直传的对象存储），**不做 spool 回退**，避免磁盘峰值翻倍。
- 上传请求的 `originPath` 用 `live-recordings/{recordingName}/part-00001.ts`，`originalName` 用成品文件名，`contentType=video/mp2t`；`attributes` 带 `recording/sourceName/roomId/part/segmentCount`。

```java
var ticket = storageService.initiateUpload(request).block();
var uploaded = new ArrayList<UploadedPart>();
for (int number = 1; number <= ticket.partCount(); number++) {
    long offset = (long) (number - 1) * ticket.chunkSize();
    long length = Math.min(ticket.chunkSize(), size - offset);
    var body = DataBufferUtils.read(path, offset, length, DefaultDataBufferFactory.sharedInstance, 64 * 1024);
    uploaded.add(storageService.putPart(ticket.fileId(), number, length, body).block());
}
var file = storageService.completeUpload(ticket.fileId(), uploaded).block();
```

- 上传开始时先把 `part.state=UPLOADING` 写清单；崩溃后恢复逻辑据此 abort 半截会话并重传。
- 成功后写 `part.storageFile`、`part.state=UPLOADED`，累加 `status.totalBytes`，再删除本地 `part-*.ts`；删除失败只告警，下次恢复重删。
- 失败时写 `part.state=FAILED` 和错误码，保留本地成品；会话已创建则 `abort`，不留半截上传对象。按 1s→2s→4s 重试 3 次，仍失败标 `LIVE_STORAGE_FAILED`。
- 上传线程不读流、不写记录，只做文件 range 和对象存储调用；JVM 堆只保留 64KB 级缓冲。

#### 8.7.5 磁盘水位与积压保护

默认值（可配置）：

| 配置 | 默认 | 作用 |
| --- | --- | --- |
| `max-local-bytes` | 6GiB | 单个录制本地分片+未上传成品上限 |
| `max-local-segments` | 360 | 未合并分片数上限（30s 约 3 小时） |
| `max-pending-parts` | 2 | 待上传成品上限；超过说明对象存储跟不上 |
| `min-free-disk-bytes` | 8GiB | 磁盘保留水位 |

- 打开新分片前检查文件系统可用空间：低于 `min-free-disk-bytes + 一个分片预估大小` 时，先强制合并并触发上传；释放后仍不足则收尾，标 `LIVE_DISK_FULL`，保留已有文件。
- 每关闭一个分片重算 `localBytes`；超过 `max-local-bytes` 或 `max-local-segments` 时立即合并，不等到 1 小时。
- 待上传成品超过 `max-pending-parts` 或本地空间仍超限时，停止拉流并标 `LIVE_STORAGE_BACKLOG`；不继续写盘把磁盘吃满，也不删除未上传成品。
- 合并成功即删分片、上传成功即删成品，正常情况下磁盘峰值约为“1 小时分片 + 1 个成品”，而不是整场录制。
- `mergeIntervalSeconds=0` 只关闭周期合并，仍受上述水位保护；不建议在磁盘紧张环境关闭周期合并。

#### 8.7.6 完成条件

- `FINALIZING` 收尾时：关闭当前分片 → 合并全部已关闭分片 → 等待所有成品 `UPLOADED` → 删除本地分片/成品 → `COMPLETED`。
- `durationMillis` 取首个分片开始到最后一个分片结束；`totalBytes` 取所有成品 size 之和；`partCount=status.parts.size()`。
- 没有任何有效分片：`CANCELED` / `LIVE_NO_DATA`，不生成空对象。
- 合并/上传失败：`FAILED`，保留 `status.parts` 和本地文件；对象存储中已成功上传的成品不回滚，重试只补未上传部分。

### 8.8 C8 失败处理

| 场景 | errorCode | 保留 | 可重试 |
| --- | --- | --- | --- |
| 链接/roomId 非法 | `LIVE_ROOM_LINK_INVALID` | 无录制 | 用户修正 |
| 解析失败 | `LIVE_RESOLVE_FAILED` | 本地分片、已上传成品、`status.parts` | 冷却后 STREAM 新录制；旧数据走 UPLOAD |
| 限流 | `LIVE_RATE_LIMITED` | 本地分片、已上传成品 | 冷却后 STREAM 新录制；旧数据走 UPLOAD |
| 房间下播 | 不标失败，`COMPLETED` | 已上传成品 | 无 |
| 拉流失败 | `LIVE_STREAM_FAILED` | 本地分片、`status.parts` | UPLOAD 收尾，或 STREAM 新录制 |
| 合并失败 | `LIVE_MERGE_FAILED` | 全部分片、成品 tmp | UPLOAD 重合并 |
| 上传失败 | `LIVE_STORAGE_FAILED` | 本地成品、`status.parts` | UPLOAD 重传 |
| 磁盘水位不足 | `LIVE_DISK_FULL` | 本地分片/成品 | 清理磁盘后 UPLOAD |
| 上传积压 | `LIVE_STORAGE_BACKLOG` | 本地成品 | 对象存储恢复后 UPLOAD |
| 策略缺失 | `LIVE_STORAGE_POLICY_MISSING` | 本地分片/成品 | 配置对象存储策略后 UPLOAD |
| 策略非法/LOCAL/驱动不支持 | `LIVE_STORAGE_POLICY_INVALID` | 本地分片/成品 | 修策略后 UPLOAD，不本地兜底 |
| 停止超时 | `LIVE_STOP_TIMEOUT` | 本地分片/成品 | 后台继续收尾或恢复后 UPLOAD |
| 进程崩溃 | `LIVE_RECORDER_LOST` | 本地分片/成品 | 恢复扫描后 UPLOAD |
| 无有效数据 | 不标失败，`CANCELED` / `LIVE_NO_DATA` | 空文件已删 | 无 |

失败处理规则：

- 失败录制不删除 `status.parts`、本地分片和本地成品；只清理 0 字节/无关键帧的空分片。
- `status.parts` 是恢复依据：`MERGING` 表示合并中断或已合并待上传（看成品文件是否存在）、`UPLOADING` 表示上传中断、`UPLOADED` 已有对象、`FAILED` 需重试；`localBytes` 按实际目录重算，不靠累加估算。
- 已上传成品不回滚；重试只补“未合并分片 + 未上传成品”。对象存储中已有对象不会因为后续失败被删除。
- 多错误并存时，`errorCode` 取最阻塞恢复的一项，优先级：`LIVE_STORAGE_POLICY_INVALID` > `LIVE_DISK_FULL` > `LIVE_STORAGE_BACKLOG` > `LIVE_MERGE_FAILED` > `LIVE_STORAGE_FAILED` > `LIVE_STREAM_FAILED` > `LIVE_RESOLVE_FAILED` > `LIVE_RATE_LIMITED`；其余原因写 `message` 和日志，便于一次看懂为什么没传上去。
- 收尾等待超过 `finalize-timeout`（默认 5 分钟）只停止 API 同步等待，状态仍为 `FINALIZING`，后台继续合并/上传；只有 owner 心跳丢失或进程退出才由恢复逻辑转 `FAILED`/`LIVE_RECORDER_LOST`。
- 失败事件 `live.recording.failed`，payload 含 `errorCode`、`sourceName`、`recordingName`、`roomId`、`partCount`、`segmentCount`、`localBytes`、`durationMillis`；不含 Cookie、HTML、流地址。

重试入口：`POST /liverecordings/{name}/retry`。

```json
{"mode": "UPLOAD"}
```

- `UPLOAD`：仅允许 `FAILED` 录制，且必须存在“未合并分片、未上传成品或 `MERGING/FAILED` part”。
  1. 扫描 `segments.jsonl`，把未合并的已关闭分片按索引合并为新 part；删除残留 `part-*.ts.tmp`。
  2. 对 `MERGING` part，如果成品文件已存在则改为待上传；不存在则用分片重建。
  3. 把 `FAILED` 或没有 `storageFile` 的 part 逐个重传；成功后写 `UPLOADED`、累加 `totalBytes`、删本地成品。
  4. 所有 part `UPLOADED` 且没有未合并分片 → `COMPLETED`，清空 `errorCode/message`，发 `live.recording.completed`，删本地目录。
  5. 本地分片和成品都已不存在、但仍有未上传 part → 直接 `409 LIVE_RECORDING_NOT_READY`，提示数据已丢失，不能伪造对象。
- `STREAM`：仅允许 `FAILED`，要求源 `enabled=true` 且对象存储策略可用；新建一条 `retryOf={name}` 的录制，源快照取当前配置，原记录保持不变。
- `QUEUED/RECORDING/FINALIZING` 返回 `409 LIVE_RECORDING_ACTIVE`；非 `FAILED` 终态或无任何可重试数据返回 `409 LIVE_RECORDING_NOT_READY`。
- `UPLOAD` 进入 `live-upload` 串行队列，`STREAM` 进入准入队列，仍受 `maxConcurrent` 限制；队列满返回 `429 LIVE_RECORDING_LIMIT_REACHED`。
- 手动重试写审计 `recordChild`（`action=retry`、`mode`、`recordingName`），不记录流地址和 Cookie。

### 8.9 C9 录制列表与详情

端点：`GET /liverecordings/query?sourceName=&state=&from=&to=&page=&size=`

- `sourceName` 可选；`state` 支持逗号分隔多值（含 `CANCELED`）；`from`/`to` 为 ISO-8601 Instant，按 `status.startedAt` 过滤。
- 查询用 `ListOptions.builder().andQuery(equal/in/lessThan/greaterThan)`；分页 `PageRequestImpl.of(page, size, defaultSort())`；默认按 `status.startedAt` 倒序，size 上限 100。
- 返回 `LiveRecordingPage(items, total, page, size)`；列表项 `LiveRecordingView` 只含 `name/sourceName/platform/roomId/roomTitle/format/state/errorCode/startedAt/endedAt/durationMillis/totalBytes/segmentCount/partCount/localBytes/storagePolicy/retryOf/auto`，不带 `parts`。
- 详情：`GET /liverecordings/{name}`（通用 GET）返回完整资源；前端从 `status.parts` 渲染成品列表，不新增 `/detail` 路由。
- 详情展示字段：`index/filename/state/startedAt/endedAt/durationMillis/size/segmentCount/errorCode/message`，以及 `storagePolicy/localBytes` 供运维看磁盘；列表/视图 DTO **不返回** `storageFile`，也不落对象键与本地绝对路径。
- 通用 GET 返回完整资源，会带出 `status.parts.storageFile` 资源名，以及 `ownerInstance/heartbeatAt` 等恢复字段；它们不是对象键或驱动凭证，前端不展示，且 `liverecordings` 的 get 只授予可信角色。
- RBAC：`liverecordings` get/list；列表只读，不从列表接口触发合并/上传。

### 8.10 C10 录制下载

端点：`POST /liverecordings/{name}/download-url`

请求可选：`{"partIndex": 1, "ttlSeconds": 300}`；不传 `partIndex` 返回所有已上传成品的下载地址。

流程：

1. fetch 录制；`status.state` 必须是 `COMPLETED`/`FAILED`/`FINALIZING` 且至少一个 `UPLOADED` part；否则 `409 LIVE_RECORDING_NOT_READY`。
2. `partIndex` 指定时只取该 part，且必须 `UPLOADED` 且有 `storageFile`；不存在或未上传返回 `409 LIVE_RECORDING_NOT_READY`。
3. 不指定时取所有 `UPLOADED` part，按 `index` 升序；超过 100 个要求显式传 `partIndex`，返回 `400 LIVE_INVALID_ARGUMENT`（例如关闭周期合并、整场只产出一个超大 part 的极端配置）。
4. 对每个 `storageFile`：
   - 先 `storageService.sourceUrl(fileId, new StorageSourceOptions(true, filename, ttl))`；
   - 若为空（策略 `proxyDownload=true` 或驱动不支持 `PRESIGNED_SOURCE`），回退 `storageService.downloadUrl(fileId, ttl)`，返回应用内签名 URL；
   - `ttl` 默认 300s，限制在 60–900s，且不超过现有 `downloadTokenTtlSeconds`；响应写 `expiresAt`、`proxyRequired`。
5. 返回：

```json
{
  "recording": "rec-live-106288756897-20260601100000-a1b2",
  "parts": [
    {
      "index": 1,
      "filename": "part-00001.ts",
      "url": "https://...",
      "expiresAt": "2026-06-01T10:10:00Z",
      "proxyRequired": false,
      "size": 188743680
    }
  ]
}
```

- 客户端直连对象存储下载，不经过应用中转；只有回退到 `downloadUrl` 时才走应用代理。
- 下载文件名用 `{recordingName}-{part.filename}`，避免多个录制都叫 `part-00001.ts`；不暴露对象键、驱动凭证、bucket 内网地址。
- `StorageService.sourceUrl` 返回 `StorageSource(url, expiresAt, proxyRequired)`；过滤 `url==null` 或 `proxyRequired!=false` 的结果。

### 8.11 C11 录制删除

- 单条删除：`DELETE /liverecordings/{name}`（通用）会走 finalizer；默认 RBAC 不授予 `liverecordings` delete，避免误删活动录制。
- finalizer 是所有删除路径的兜底：`LiveRecordingReconciler` 发现 `deletionTimestamp` 时，若状态仍为 `QUEUED/RECORDING/FINALIZING`，先 `recordingService.requestStop(name)`（写 Redis 停止键，owner 实例据此收尾）并 `requeue(5s)`，等 `COMPLETED/FAILED/CANCELED` 后再清理；若本实例无任务且 `pudans:live:recording:owner:{name}` 心跳缺失/过期，按 `LIVE_RECORDER_LOST` 置 `FAILED` 后清理；不能只靠端点守卫，否则通用 DELETE 仍可绕过。
- 前端/批量使用：`DELETE /liverecordings/batch`，body `{"names": ["rec-..."]}`：
  - 逐条 fetch；不存在 → `NOT_FOUND`；
  - `QUEUED/RECORDING/FINALIZING` → `REJECTED`/`LIVE_RECORDING_ACTIVE`，不标记删除；
  - 终态 → `client.delete(recording)`，写 `deletionTimestamp`，由 `LiveRecordingReconciler` 清理，返回 `ACCEPTED`（不是同步删除完成）；
  - 返回逐条结果，HTTP 200；至少一条成功不整体失败。
- finalizer 清理顺序：
  1. 对每个 `UPLOADED` part 调 `storageService.purgeObjectFirst(part.storageFile)`；
  2. `UPLOADING` part 且 `storageFile` 非空时先 `storageService.abortUpload(storageFile)`，再按对象可能已存在执行一次 `purgeObjectFirst`（幂等）；`storageFile` 为空说明会话尚未创建，只删本地；
  3. `MERGING` part 无对象，只删 `part-*.ts.tmp`；
  4. 删除本地 `segment-*.ts`、`part-*.ts`、残留 `*.tmp`、`segments.jsonl`、`parts.jsonl`，最后删录制目录；
  5. 所有对象删除成功（或对象本就不存在）、本地目录清理成功后，`removeFinalizers(...)` + `client.update`；
  6. 任一对象删除失败：`status.cleanupAttempts++` 写回；未超过 10 次 `Result.requeue(30s)`，超过后改为 `requeue(1h)` 并告警 `LIVE_CLEANUP_FAILED`，保持 finalizer 和记录，等待人工处理或后续重试；绝不先删记录。
- 本地删除失败同样进入 `cleanupAttempts` 重试，避免磁盘残留；删除前用 `Path.toRealPath(LinkOption.NOFOLLOW_LINKS)` 校验目录必须位于 `{work-dir}/{recordingName}` 下，禁止跟随符号链接删除外部文件。
- `purgeObjectFirst` 是新方法，不改变 `StorageService.purge` 的既有语义：
  - 驱动删除成功 → `fileService.delete(file)`；
  - 驱动删除失败 → 保留 `StorageFile` 记录并抛错，便于重试；
  - 对象不存在视为成功（幂等）；
  - `UPLOADING` 文件先调 `StorageService.abortUpload(fileId)`。
- `purgeObjectFirst` 返回 `Mono<Void>`；Reconciler 在 controller 的虚拟线程上 `block(Duration.ofSeconds(30))`（`DefaultController` 用 `Thread.ofVirtual()`），超时按失败处理；不得在 WebFlux event loop 调用。
- 审计：批量端点对每个 name 记录 `recordChild`（`action=delete`、`result`）；通用 DELETE 由 `AuditWebFilter` 记录。

### 8.12 C12 保留策略清理

`LiveRecordingCleanupTask`：

- `@PostConstruct` 启 `Flux.interval(interval, interval).onBackpressureDrop().concatMap(...).onErrorContinue(...)`；`@PreDestroy` dispose。
- 每小时（可配置）查询：`status.state in (COMPLETED, FAILED, CANCELED)` 且 `status.endedAt < now - retentionDays`；`status.endedAt` 为空时回退 `status.startedAt`；按 `status.endedAt`（缺失用 `startedAt`）升序，批量 100。
- 对每条：
  - 已有 `deletionTimestamp` → 跳过（幂等）。
  - `client.delete(recording)` 触发 finalizer；不等待对象删除完成。
  - 失败只记日志/指标，不阻塞后续。
- `retentionDays<=0` 时禁用按时间清理（便于测试/保留全部），但启动孤儿目录清理仍执行。
- 清理失败事件 `live.cleanup.failed`，payload 含 `recordingName`、`errorCode`；告警去重。
- 启动时额外清理 `work-dir` 下超过 24h、不属于任何活动录制、且没有 `pudans:live:recording:owner:*` 心跳的临时目录（含分片、成品、清单），防止崩溃遗留占盘；删除前校验 real path，不跟随符号链接。

### 8.13 启动恢复

`LiveRecordingRecovery` 在 `@PostConstruct` 后用 `boundedElastic` 异步执行，不阻塞应用启动：

1. 查询 `state in (QUEUED, RECORDING, FINALIZING)` 的录制。
2. 读 `ownerInstance`/`heartbeatAt`：
   - 心跳在 90s 内且 `ownerInstance` 不是本实例 → 别的实例在录，跳过。
   - 否则视为遗留，先抢 Redis 恢复锁 `pudans:live:recovery:{name}`（`SET NX PX 10m`），抢不到说明别的实例正在恢复，跳过。
3. 按本地目录恢复：
   - 扫 `segment-*.ts` 与 `segments.jsonl`，把清单缺失但文件非空的孤儿分片追加进清单（`endedAt` 用文件最后修改时间，`durationMillis` 按相邻时间差估算）；空文件删除；残留 `*.tmp` 删除。
   - `MERGING` part：成品文件存在则改为待上传；不存在则用清单里的分片重建。
   - 合并所有未合并分片，上传所有未上传成品；成功后写 `UPLOADED`、`totalBytes`，删本地文件。
4. 落状态：
   - 所有 part `UPLOADED` 且无未合并分片 → `COMPLETED`；
   - 有本地数据但合并/上传失败 → `FAILED` + 对应 `LIVE_MERGE_FAILED`/`LIVE_STORAGE_FAILED`/`LIVE_STORAGE_POLICY_*`；
   - 完全没有分片和成品 → `CANCELED` + `LIVE_NO_DATA`；
   - `QUEUED` 且创建超过 30 分钟、无任何本地文件 → `FAILED` + `LIVE_RECORDER_LOST`。
5. 清理 `LiveSource.status.currentRecording` 指向已终态录制的悬挂引用，避免源永远处于 `RECORDING`；源状态按最近解析结果回到 `MONITORING`/`ERROR`。
6. 删除对应 Redis 停止键/心跳键；记录恢复事件 `live.recording.recovered`，payload 含 `recordingName`、`recoveredParts`、`state`。
7. 恢复过程幂等：重复启动、多个实例同时恢复都以 Redis 锁和“文件是否存在”为准，不重复合并/上传；状态写回用 revision 冲突重试，失败只告警。

## 9. 监控设计（M1–M7）

### 9.1 M1 开播检测

`LiveMonitorWorker` 每 `tick`（5s）：

```text
if !leaderLease.isLeader(): return
now = Instant.now()
due = registry.snapshotDue(now, maxPerTick)   // 按 nextCheckAt 升序，过滤冷却/在途
due.flatMap(source -> resolve(source), resolveConcurrency=4)
   .concatMap(result -> stateMachine.apply(result))
   .subscribe()
```

- 同一源用 `inFlight` set 防重入；上一轮未完成不开始下一轮。
- `maxPerTick=20` 防止一次 tick 打爆；未处理的源下一 tick 继续。
- 解析在线且满足条件 → 创建录制（C1）；离线 → `OFFLINE`；失败 → 计数/退避。
- 解析在线时写 `lastStreamAt = now`；录制任务成功打开流时也更新，用于 M2 判断流地址新鲜度。
- 状态写回后更新 `nextCheckAt`：
  - 在线：`now + 30s`；
  - 离线：`now + max(checkIntervalSeconds, 60s)`；
  - 失败：`now + min(60s * 2^(failures-3), 15m)`；
  - 限流：`cooldownUntil + 30s 抖动`。
- leader 租约：`SET NX PX 15s` + 每 5s Lua `compare-and-pexpire`；非 leader 不解析、不创建录制，但仍可响应 API。

### 9.2 M2 直播状态快照

源状态字段即快照：`state/live/roomTitle/anchorName/lastResolvedAt/lastLiveAt/lastOfflineAt/lastStreamAt/nextCheckAt/cooldownUntil/currentRecording`。

端点：

- `GET /livesources/{name}/health`：单源快照 + 当前录制状态 + 最近错误。
- `GET /livesources/summary`：总数/启用数/在线数/录制中/离线/错误/限流中；从 registry + 状态计数得到，不每次全表扫描。
- 前端 10s 轮询 summary 和源列表；不订阅 WebSocket。

### 9.3 M3 录制健康监控

`LiveRecorderRegistry` 维护每个活动任务的：

- `recordingName/sourceName/startedAt/lastPacketAt/lastWriteAt/bytesWritten/segmentCount/partCount/state`。
- `lastPacketAt` 每包更新内存；`stale` 判定 `now - lastPacketAt > streamIdleTimeout`（30s）→ 认为断流，触发 C4/C5。
- 录制速率 = 两次采样 `bytesWritten` 差值 / 时间；可选，不持久化。
- `GET /livesources/{name}/health` 合并当前录制的 `lastPacketAt/lastWriteAt`。
- 任务异常退出：`LiveRecorderTask` 的 `finally` 上报 `registry.complete(name, error)`；Reconciler/恢复逻辑兜底。

### 9.4 M4 断流/录制失败告警

事件：

| 事件 | 触发 | 去重 |
| --- | --- | --- |
| `live.stream.interrupted` | 断流超过 `stream-interrupt-threshold`（60s） | 每录制 10m |
| `live.stream.recovered` | 重连成功 | 不额外去重 |
| `live.recording.failed` | 录制进入 `FAILED` | 每录制 10m |
| `live.resolve.rate_limited` | 限流 | 每 room 10m |
| `live.cleanup.failed` | finalizer 清理失败 | 每录制 10m |

- 统一走 `DomainEventPublisher.publish(type, payload, traceFlowId)`；payload 含 `sourceName/recordingName/roomId/errorCode/message/at`。
- 不新增通知库；现有 Webhook 订阅者按 `type` 消费。
- 告警去重键 `pudans:live:alert:{type}:{subject}` TTL 10m，`setIfAbsent`；Redis 故障时允许重复发，不阻断主流程。

### 9.5 M5 解析风控告警

- 连续限流计数 `pudans:live:ratelimit:streak:{roomId}`；≥3 次发 `live.resolve.rate_limited`，提示“降低解析频率或检查出口 IP”，不把更换 Cookie 作为默认建议。
- 5 分钟内 ≥3 个不同房间被限流：发一条全局风控告警（subject=`global`），提示出口 IP/账号可能被风控。
- 冷却期间不自动解析；前端在源详情显示 `cooldownUntil`，并提供“稍后重试”提示，不提供绕过按钮。

### 9.6 M6 监控看板 API

| 方法 | 路径 | 说明 | RBAC |
| --- | --- | --- | --- |
| GET | `/livesources/summary` | 汇总统计 | `livesources/summary` get |
| GET | `/livesources/{name}/health` | 单源健康 | `livesources/health` get |
| GET | `/livesources` | 源列表/筛选 | `livesources` list |
| GET | `/liverecordings/query` | 录制列表/筛选/分页 | `liverecordings/query` list |
| GET | `/liverecordings/active` | 活动录制 | `liverecordings/active` list |

- 全部只读，自定义端点走 `contract.live` 的 DTO；`GET /livesources` 与录制详情用通用 Extension GET，后者会带出 `ownerInstance/heartbeatAt/storageFile` 等内部字段，只授予可信角色，前端不展示。
- `size` 上限 100；`page` 从 1 开始；非法参数 `400`。

### 9.7 M7 Actuator 指标

见第 12 节。

## 10. API 设计

统一前缀：`/apis/api.pudans.tolim.cn/v1alpha1`。

### 10.1 端点清单

| 方法 | 路径 | 用途 | RBAC 资源/动作 | 主要错误 |
| --- | --- | --- | --- | --- |
| POST | `/livesources/from-url` | 链接创建源 | `livesources/from-url` create | 400/409/429/502 |
| POST | `/livesources/resolve` | 预览解析，不创建 | `livesources/resolve` create | 400/429/502 |
| POST | `/livesources/{name}/resolve` | 强制重解析并更新状态 | `livesources/resolve` create | 404 `LIVE_SOURCE_NOT_FOUND`/429/502 |
| POST | `/livesources/{name}/record` | 手动开始录制 | `livesources/record` create | 404/409/429 |
| GET | `/livesources/summary` | 汇总 | `livesources/summary` get | 无 |
| GET | `/livesources/{name}/health` | 单源健康 | `livesources/health` get | 404 |
| GET | `/liverecordings/query` | 录制查询 | `liverecordings/query` list | 400 |
| GET | `/liverecordings/active` | 活动录制 | `liverecordings/active` list | 无 |
| POST | `/liverecordings/{name}/stop` | 停止录制 | `liverecordings/stop` create | 404/409 |
| POST | `/liverecordings/{name}/download-url` | 下载地址 | `liverecordings/download-url` create | 404/409/503 |
| POST | `/liverecordings/{name}/retry` | 重试 | `liverecordings/retry` create | 404/409 |
| DELETE | `/liverecordings/batch` | 批量删除 | `liverecordings/batch` delete | 400/409 |

通用路由仍存在：

- `GET/PUT/PATCH/DELETE /livesources/{name}`：管理员使用；PATCH 受字段校验。
- `GET /liverecordings/{name}`：详情；`DELETE /liverecordings/{name}` 默认不授权，删除走 batch。
- 自定义端点由 `WebFluxConfig.customEndpoints`（`@Order(HIGHEST_PRECEDENCE + 100)`）先于通用扩展路由注册，`ExtensionCompositeRouterFunction` 未标注 `@Order`；因此 `/livesources/summary`、`/liverecordings/query|active|batch` 会优先匹配，不会被 `/{name}` 捕获。实现时不要给扩展路由提升顺序。

### 10.2 请求/响应约定

- 成功：`ServerResponse.ok().bodyValue(...)` 或 `201 Created`；与现有 `RiskRuleEndpoint`/`StorageFileEndpoint` 风格一致。
- 错误：`ApiException(status, code, reason)`，RFC7807 响应体里的 `code` 即 `LiveErrorCode` 枚举名。
- 参数错误：`ServerWebInputException` 或 `ApiException` `400 LIVE_INVALID_SPEC`；消息中文。
- 分页：返回 `{items, total, page, size}`；不返回 `null` 数组，空列表用 `[]`。
- 时间：ISO-8601 UTC；`Instant` 序列化沿用 Jackson 配置。
- 不返回流地址；不返回 Cookie；不返回 `storageKey`。

### 10.3 DTO（`contract/live`）

```java
public record LiveSourceCreateRequest(
        String roomUrl, String alias, Boolean autoRecord,
        Integer segmentSeconds, Integer mergeIntervalSeconds, String storagePolicy) {}

public record LiveSourceSummary(
        long total, long enabled, long live, long recording,
        long offline, long error, long rateLimited) {}

public record LiveSourceHealth(
        String name, String state, boolean live, String roomTitle,
        String anchorName, Instant lastResolvedAt, Instant nextCheckAt,
        Instant cooldownUntil, String currentRecording,
        String lastResolveCode, String lastResolveError) {}

public record LiveRecordingQuery(
        String sourceName, List<LiveRecordingState> states,
        Instant from, Instant to, int page, int size) {}

public record LiveRecordingPage(
        List<LiveRecordingView> items, long total, int page, int size) {}

public record LiveRecordingView(
        String name, String sourceName, LivePlatform platform, String roomId,
        String roomTitle, String anchorName, String format,
        LiveRecordingState state, String errorCode, Instant startedAt,
        Instant endedAt, long durationMillis, long totalBytes,
        int segmentCount, int partCount, long localBytes,
        String storagePolicy, String retryOf, boolean auto) {}

public record LiveRecordingPartView(
        int index, String filename, PartState state, Instant startedAt,
        Instant endedAt, long durationMillis, long size,
        int segmentCount, String errorCode, String message, boolean downloadable) {}

public record PartDownload(
        int index, String filename, String url, Instant expiresAt,
        boolean proxyRequired, long size) {}

public record LiveRecordingDownload(String recording, List<PartDownload> parts) {}

public record BatchDeleteRequest(List<String> names) {}

public record BatchDeleteItem(String name, String result, String code, String message) {}

public record LiveRecordingRetry(String recording, String mode) {}
```

- `LiveRecordingView` 不含 `parts`；详情用通用 GET 返回完整资源，前端按 `status.parts` 渲染 `LiveRecordingPartView`。
- `LiveRecordingPartView.downloadable` 等价于 `state==UPLOADED && storageFile!=null`；不返回 `storageFile`/对象键/本地路径。
- `BatchDeleteItem.result` 取值 `ACCEPTED`/`REJECTED`/`NOT_FOUND`；`ACCEPTED` 只表示已写 `deletionTimestamp`，对象清理异步完成。
- `LiveRecordingRetry.mode` 取值 `UPLOAD`/`STREAM`。

### 10.4 错误码

```java
public enum LiveErrorCode {
    LIVE_ROOM_LINK_INVALID(BAD_REQUEST, "直播间链接无效。"),
    LIVE_ROOM_NOT_FOUND(NOT_FOUND, "直播间不存在。"),
    LIVE_RESOLVE_FAILED(BAD_GATEWAY, "直播间解析失败。"),
    LIVE_ROOM_OFFLINE(CONFLICT, "直播间当前未开播。"),
    LIVE_RATE_LIMITED(TOO_MANY_REQUESTS, "直播间解析被限流，请稍后重试。"),
    LIVE_SOURCE_DUPLICATE(CONFLICT, "该直播间已存在直播源。"),
    LIVE_SOURCE_NOT_FOUND(NOT_FOUND, "直播源不存在。"),
    LIVE_RECORDING_ACTIVE(CONFLICT, "录制正在进行中，请先停止。"),
    LIVE_RECORDING_NOT_ACTIVE(CONFLICT, "录制不在进行中。"),
    LIVE_RECORDING_NOT_READY(CONFLICT, "录制尚无可下载的成品。"),
    LIVE_RECORDING_LIMIT_REACHED(TOO_MANY_REQUESTS, "录制并发已达上限。"),
    LIVE_STREAM_FAILED(BAD_GATEWAY, "拉流失败。"),
    LIVE_DISK_FULL(INSUFFICIENT_STORAGE, "录制磁盘空间不足。"),
    LIVE_STORAGE_POLICY_MISSING(SERVICE_UNAVAILABLE, "未配置可用的对象存储策略。"),
    LIVE_STORAGE_POLICY_INVALID(BAD_REQUEST, "存储策略不可用：直播录制只支持就绪的对象存储，大成品还需支持分片直传。"),
    LIVE_STORAGE_BACKLOG(INSUFFICIENT_STORAGE, "成品上传积压，已暂停拉流以避免磁盘耗尽。"),
    LIVE_MERGE_FAILED(INTERNAL_SERVER_ERROR, "录制分片合并失败。"),
    LIVE_STORAGE_FAILED(BAD_GATEWAY, "录制成品上传失败。"),
    LIVE_STOP_TIMEOUT(GATEWAY_TIMEOUT, "录制停止超时，后台继续收尾。"),
    LIVE_RECORDER_LOST(INTERNAL_SERVER_ERROR, "录制任务意外中断。"),
    LIVE_CLEANUP_FAILED(INTERNAL_SERVER_ERROR, "录制文件清理失败。"),
    LIVE_NO_DATA(CONFLICT, "录制没有产生有效数据。"),
    LIVE_INVALID_SPEC(BAD_REQUEST, "直播资源配置无效。"),
    LIVE_INVALID_ARGUMENT(BAD_REQUEST, "请求参数无效。")
}
```

`LiveErrorCode.exception()` 返回 `new ApiException(status, name(), message)`；`message` 可直接展示给用户。内部错误可带 `Throwable`，但不要把上游 HTML/Cookie 放进 `reason`。

## 11. 配置

```yaml
pudans:
  live:
    resolve:
      connect-timeout: 5s
      response-timeout: 10s
      max-html-bytes: 8388608
      cache-ttl: 45s
      offline-cache-ttl: 20s
      lock-ttl: 15s
      lock-wait: 2s
      cooldown: 10m
      max-cooldown: 1h
      user-agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36 Edg/109.0.1518.49"
      cookie: "${PUDANS_LIVE_DOUYIN_COOKIE:}"  # 可选；默认空，不带 Cookie 也能解析
    monitor:
      tick: 5s
      default-interval: 60s
      min-interval: 30s
      live-interval: 30s
      max-per-tick: 20
      resolve-concurrency: 4
      failure-backoff: 1m
      max-failure-backoff: 15m
      error-threshold: 3
      leader-ttl: 15s
    recorder:
      max-concurrent: 2
      queue-capacity: 10
      work-dir: "${pudans.work-dir}/live-recordings"
      segment-seconds: 30          # 只允许 10 或 30
      merge:
        interval: 1h               # 0s 关闭周期合并，仍受磁盘水位保护
        max-local-bytes: 6GiB
        max-local-segments: 360
        max-pending-parts: 2
      max-duration: 6h
      read-timeout: 15s
      stream-idle-timeout: 30s
      max-retries: 5
      retry-backoff: 1s
      max-retry-backoff: 30s
      retry-jitter: 0.2
      upload-concurrency: 1        # 成品上传串行，避免对象存储被打满
      upload-retries: 3
      finalize-timeout: 5m         # API 同步等待上限，超时后台继续
      min-free-disk-bytes: 8GiB
      stop-timeout: 30s
      recovery-lock-ttl: 10m
    retention:
      days: 7
      interval: 1h
      batch-size: 100
    alert:
      dedupe: 10m
      stream-interrupt-threshold: 60s
      rate-limit-threshold: 3
```

配置类 `LiveProperties` 放 `cn.tolim.pudans.infra.properties`，由现有 `@ConfigurationPropertiesScan(basePackages = "cn.tolim.pudans.infra.properties")` 扫描；用 `@ConfigurationProperties(prefix = "pudans.live")` + `@Validated`，字段用 `Duration`/`DataSize`/`int`，嵌套 final 字段。敏感 `cookie` 只从环境变量注入，不写默认值、不写日志、不出现在 Actuator `/env` 的明文（必要时加 `@JsonIgnore` 或脱敏）。

- `application.yaml` 只放非敏感默认值；Cookie 通过 `PUDANS_LIVE_DOUYIN_COOKIE` 注入，默认不发。
- 配置变更需同步 `docs` 或部署说明；新增配置项都要在 `LiveProperties` 有对应字段，禁止散落 `@Value`。
- `segment-seconds` 只接受 10/30；其他值启动校验失败，不允许运行期改写正在录制的参数。
- `merge.interval` 允许 0（关闭周期合并）或 60–21600 秒（1 分钟到 6 小时）；默认 1 小时。
- `max-duration` 到点正常收尾（`COMPLETED`），防止长时间占用录制槽位。
- `retention.days<=0` 禁用按时间清理，但启动孤儿目录清理仍执行。
- `min-free-disk-bytes` 必须大于单个成品预估大小；小于 1GiB 启动校验失败，避免配置成“必然磁盘满”。
- 内置 `storage-policy-default` 是 `type: local` 且 `defaultPolicy: true`；直播录制不会把它当作可用默认。启用自动录制前，管理员必须创建 S3/KS3/OSS/COS/OBS 策略并置 `status.ready=true`，按需设为默认；否则 `from-url` 在 `autoRecord=true` 时返回 `LIVE_STORAGE_POLICY_MISSING`/`INVALID`，不会落本地兜底。

## 12. 指标、事件与日志

### 12.1 指标

| 指标 | 类型 | 标签 | 说明 |
| --- | --- | --- | --- |
| `live.resolve.success` | Counter | `platform` | 解析成功次数 |
| `live.resolve.fail` | Counter | `platform, code` | 解析失败次数 |
| `live.resolve.rate_limited` | Counter | `platform` | 限流次数 |
| `live.resolve.cache.hit` | Counter | `platform` | 缓存命中 |
| `live.resolve.redis.degraded` | Counter | 无 | Redis 故障降级 |
| `live.monitor.tick.duration` | Timer | 无 | tick 耗时 |
| `live.sources.active` | Gauge | 无 | 启用源数 |
| `live.recordings.active` | Gauge | 无 | 活动录制数 |
| `live.recordings.queued` | Gauge | 无 | 排队录制数 |
| `live.recordings.failed` | Gauge | 无 | 终态失败数（启动加载+状态变化维护） |
| `live.recording.bytes.total` | Counter | `platform` | 写入本地分片的字节 |
| `live.recording.segment.write.fail` | Counter | `platform, code` | 分片写入失败 |
| `live.recording.merge.duration` | Timer | `platform` | 一次成品合并耗时 |
| `live.recording.merge.fail` | Counter | `platform, code` | 合并失败 |
| `live.recording.part.upload.fail` | Counter | `platform, code` | 成品上传失败 |
| `live.recording.local.bytes` | Gauge | 无 | 本地分片+未上传成品占用字节 |
| `live.storage.policy.invalid` | Counter | `platform` | 存储策略缺失/非法/LOCAL |
| `live.storage.backlog` | Gauge | 无 | 待上传成品数 |
| `live.cleanup.failed` | Counter | 无 | 清理失败 |

- 用 `MeterRegistry` 构造，参考 `AiMetrics` 的 `safe(...)` 包裹，避免指标注册异常影响业务。
- 标签只加低基数维度；不加 `roomId`/`recordingName`。
- `live.recordings.failed` 不每次 scrape 查库；用 `AtomicLong` 在状态迁移时增减，启动时统计一次。
- `live.recording.local.bytes` 与 `live.storage.backlog` 由 `LivePartMerger`/`LivePartUploader` 在本地文件变化时更新，单位字节/个数，不在 scrape 时遍历磁盘。

### 12.2 事件

| 事件 | payload |
| --- | --- |
| `live.source.state.changed` | `sourceName/roomId/from/to/reason/at` |
| `live.recording.started` | `recordingName/sourceName/roomId/at` |
| `live.recording.completed` | `recordingName/durationMillis/totalBytes/segmentCount/partCount` |
| `live.recording.failed` | `recordingName/errorCode/segmentCount/partCount/localBytes/durationMillis` |
| `live.recording.merge.failed` | `recordingName/partIndex/errorCode` |
| `live.recording.part.uploaded` | `recordingName/partIndex/size/durationMillis` |
| `live.recording.recovered` | `recordingName/state/recoveredParts` |
| `live.storage.policy.invalid` | `sourceName/storagePolicy/errorCode` |
| `live.storage.backlog` | `recordingName/pendingParts/localBytes` |
| `live.stream.interrupted` | `recordingName/urlHost?/lastPacketAt`（只写 host，不写完整 URL） |
| `live.stream.recovered` | `recordingName/at` |
| `live.resolve.rate_limited` | `roomId/cooldownUntil/streak` |
| `live.cleanup.failed` | `recordingName/storageFile/errorCode` |

`traceFlowId` 优先用请求的 trace，没有则新生成；不把 Cookie/流地址/对象键写进 payload。

### 12.3 日志

- 关键字段：`sourceName/recordingName/roomId/partIndex/errorCode`；不打印完整流 URL（含签名参数），必要时只打 host。
- 解析失败只打状态码、HTML 长度、提取到的 push 数量；不打 HTML 正文。
- 录制循环不打每包日志；每 10 个分片打一次进度，合并/上传/重试/失败各打一条，避免 30s 一条刷屏。
- 日志级别：正常状态迁移 `INFO`；重试/磁盘水位 `WARN`；失败 `ERROR`；限流 `WARN`。

## 13. 安全设计

### 13.1 链接与 SSRF

- 只接受 `https://live.douyin.com/{roomId}`；用解析出的 roomId 重建 URL，绝不直接请求用户输入 URL。
- WebClient `followRedirect(false)`；固定 host；不允许 `userinfo`；不允许非 443 端口。
- 解析器不执行远程 JS；Jackson 只反序列化数据。
- 请求响应体上限 8MB；超限按 `LIVE_RESOLVE_FAILED`，不把大响应读入内存。

### 13.2 流地址校验（FFmpeg 协议注入）

- 远程载荷里的流地址不能直接喂给 FFmpeg：FFmpeg 支持 `file:`、`concat:`、`subfile:` 等协议，可能读取本地文件。
- `LiveStreamUrlValidator` 校验：
  - scheme 只允许 `http`/`https`；
  - 拒绝 `userinfo`；
  - 拒绝 `localhost`、回环、链路本地、私有网段（`127.0.0.0/8`、`10/8`、`172.16/12`、`192.168/16`、`169.254/16`、`::1`、`fc00::/7`、`fe80::/10`）；
  - 端口只允许 80/443/8080/8443（可按实际 CDN 调整）；
  - 拒绝空白、换行、`file:`/`concat:`/`subfile:` 前缀。
- 校验在 `LiveStreamOpener` 中执行；不合法直接 `LIVE_STREAM_FAILED`，不尝试。打开 `FFmpegFrameGrabber` 时同时设置 `protocol_whitelist=http,https,tcp,tls,crypto`，把协议限制下沉到 libav，不只依赖 URL 校验。
- 如果要更严格，可对解析出的 host 做 DNS 解析并校验公网 IP；这是一次阻塞调用，放 `boundedElastic`，并缓存结果 5 分钟。

### 13.3 Cookie 与凭证

- 直播间页面默认不需要 Cookie 即可解析；`pudans.live.resolve.cookie` 默认空，空时请求不带 `Cookie` 头。
- Cookie 只从环境变量 `PUDANS_LIVE_DOUYIN_COOKIE` 读取；不落库、不写日志、不进事件 payload。仅在运维确认需要时配置，且不把“换 Cookie”作为默认排障建议。
- 日志中如必须出现，用 `maskCookie` 只保留前 4 位+长度。
- 对象存储凭证仍由现有 `StoragePolicy` 管理，直播模块不接触；日志和事件不输出对象键。

### 13.4 路径与文件

- 本地目录：`${pudans.live.recorder.work-dir}/{recordingName}/`；`recordingName` 必须匹配 `[a-z0-9-]{1,120}`；文件名固定 `segment-%05d.ts`、`part-%05d.ts`。
- 创建前 `Path.normalize()` + `startsWith(workDir)` 校验；禁止 `..`、绝对路径、符号链接逃逸；删除前用 `toRealPath(NOFOLLOW_LINKS)` 再校验。
- 分片和本地成品权限继承进程 umask；分片合并成功后删除，成品上传成功后删除；失败保留供重试。
- 本地只是缓存：对象存储上传成功才算归档完成；不允许把本地路径登记为最终 `storageFile`。
- 启动清理只删除“属于终态录制或不属于任何活动录制”的目录，避免误删正在写入的分片。
- 磁盘水位由 `max-local-bytes/max-local-segments/max-pending-parts/min-free-disk-bytes` 共同保护，任何一项触发都先合并/上传，仍不足则停录而不是继续写盘。

### 13.5 RBAC 与审计

- 创建只走 `livesources/from-url`；通用 `POST /livesources` 不授权。
- 删除录制只走 `liverecordings/batch`；通用 `DELETE /liverecordings/{name}` 不授权，避免绕过活动录制检查。
- 所有写操作由 `AuditWebFilter` 自动记录；定制端点补真实资源名/结果。
- 下载只签发短期预签名 URL，不在响应/审计中记录 URL 本身；审计记录 `recordingName/partIndex/ttlSeconds`。
- 前端按钮与菜单模板一致；后端仍以 RBAC 为准，不能只靠隐藏按钮。

## 14. 失败矩阵

| 场景 | 检测 | 处理 | 错误码 | 用户可见 |
| --- | --- | --- | --- | --- |
| 链接非法 | 解析器 | 拒绝 | `LIVE_ROOM_LINK_INVALID` | 表单错误 |
| 房间不存在 | 404 | 不创建/不重试 | `LIVE_ROOM_NOT_FOUND` | 提示 |
| 解析失败 | 超时/5xx/结构变化 | 退避，连续 3 次 `ERROR` | `LIVE_RESOLVE_FAILED` | 源状态错误 |
| 限流 | 403/429/验证页 | 冷却 10m 起翻倍 | `LIVE_RATE_LIMITED` | 显示冷却截止 |
| 离线 | 解析成功无流 | 正常 `OFFLINE` | 无 | 离线 |
| 流中断 | `grabPacket` null/异常 | 重解析→换地址/退避 | `LIVE_STREAM_FAILED` | 录制失败详情 |
| 地址过期 | 新地址不同 | 关闭分片→合并旧地址成品→新地址续录 | 无 | 录制继续 |
| 分片写入失败 | JavaCV 写文件异常 | 关闭当前分片；磁盘满按 `LIVE_DISK_FULL`，否则 `LIVE_STORAGE_FAILED` | 对应码 | 失败原因 |
| 合并失败 | 复制/改名/fsync 异常 | 保留分片，`FAILED`，可 UPLOAD 重试 | `LIVE_MERGE_FAILED` | 可重试 |
| 磁盘不足 | 开段前水位检查 | 先强制合并/上传；仍不足停录 `FAILED` | `LIVE_DISK_FULL` | 失败原因 |
| 上传积压 | 待上传成品数/本地占用超限 | 暂停拉流，保留成品，恢复后继续 | `LIVE_STORAGE_BACKLOG` | 可恢复 |
| 无默认策略 | 创建/上传前 | 手动拒绝；自动置源 `ERROR` | `LIVE_STORAGE_POLICY_MISSING` | 提示配置 |
| 策略为 LOCAL/不可用/驱动不支持 | 创建/上传前 | 拒绝，不本地兜底 | `LIVE_STORAGE_POLICY_INVALID` | 提示换对象存储 |
| 上传失败 | Storage 异常 | 1s→2s→4s 重试 3 次→`FAILED`，保留本地 | `LIVE_STORAGE_FAILED` | 可重试 |
| 停止超时 | 30s 未关闭 native | 强制释放，后台继续合并/上传 | `LIVE_STOP_TIMEOUT` | 失败但可恢复 |
| 进程崩溃 | 启动恢复 | 扫描本地清单，补合并/补上传 | `LIVE_RECORDER_LOST`（无法恢复时） | 可重试 |
| 上传中进程崩溃 | 启动恢复 | 丢弃半截 multipart 会话，重新上传本地成品 | `LIVE_STORAGE_FAILED`（重传仍失败） | 可重试 |
| Redis 故障 | 连接异常 | 直连解析；监控继续 | 无（降级指标） | 可能变慢 |
| 对象删除失败 | finalizer | 重试 10 次后保持记录并告警 | `LIVE_CLEANUP_FAILED` | 管理端告警 |
| 本地删除失败 | finalizer | 同上，避免磁盘残留 | `LIVE_CLEANUP_FAILED` | 管理端告警 |
| 容器缺 native | Phase 0/启动 | 录制不可用 | `LIVE_RECORDER_LOST`/启动失败 | 部署告警 |

## 15. 前端设计

### 15.1 页面与路由

- 路由：`web/src/routes/_authenticated/_workspace/(console)/live.tsx`
  ```tsx
  export const Route = createFileRoute("/_authenticated/_workspace/(console)/live")({
    component: LiveView,
  });
  ```
- 页面：`web/src/modules/workspace/console/live/index.tsx`
- 导出：在 `web/src/modules/workspace/console/index.ts` 增加 `export { LiveView } from "./live";`。
- 菜单模板：`extensions/workspace/console/workspace-console-menu-template-live.yaml`：
  `ui-menu: console.live`、`ui-component: live`、`ui-workspace: console`、`ui-path: /live`、`ui-label: 直播`、`ui-icon: lucide:radio`、`ui-group: 通用`、`ui-order: 35`、`ui-permissions: ["live:view"]`。
- 参考页面：`web/src/modules/workspace/console/storage-policies/index.tsx`（表格、对话框、状态徽章、错误提示的现有模式）。

### 15.2 页面结构

```text
/live
├─ 汇总卡片：总源数 / 启用 / 在线 / 录制中 / 离线 / 错误 / 限流
├─ 直播源表
│   ├─ 列：别名、roomId、标题、主播、状态、最后解析、当前录制、操作
│   ├─ 操作：启用/停用、立即解析、开始录制、删除
│   └─ “从链接创建”Dialog：链接、别名、自动录制、分片时长(10/30)、合并间隔(关闭/1h)、存储策略
├─ 录制表
│   ├─ 筛选：直播源、状态、时间范围
│   ├─ 列：源、房间、状态、开始/结束、时长、大小、分片数、成品数、本地占用、操作
│   └─ 操作：停止、下载、重试、删除；详情展开成品列表
└─ 错误/限流提示：cooldownUntil、lastResolveCode
```

### 15.3 交互细节

- TanStack Query：
  - `summary` `refetchInterval: 10000`；
  - 源列表 `refetchInterval: 5000`（有活动录制时 3000）；
  - 活动录制 `refetchInterval: 3000`；无活动任务时停止轮询。
- 创建对话框先调 `POST /livesources/resolve` 预览标题/主播/是否在线；再调 `from-url` 创建。离线仍允许创建（提示“当前离线，创建后会持续监控”）。
- 存储策略下拉只列 `status.ready=true` 且 `type != local` 的策略；没有可选策略时禁用“自动录制”并提示先配置对象存储。
- 分片时长只提供 10/30 两个选项；合并间隔只提供“关闭 / 1 小时 / 2 小时 / 6 小时”，默认 1 小时。
- 限流错误显示 `cooldownUntil` 本地时间，禁用“立即重试”直到冷却结束；不提供绕过。
- 删除源/录制用 `AlertDialog` 二次确认；活动录制删除按钮禁用，提示先停止。
- 下载：点击成品 → 请求 `download-url`（`partIndex`）→ `window.open(url)`；URL 过期后重新请求。
- 详情中的成品列表显示 `index/state/时长/大小/分片数`，`FAILED` 可重试，`UPLOADED` 可下载；不显示本地路径/对象键。
- 状态徽章颜色：`RECORDING/LIVE` 绿色，`OFFLINE` 灰色，`ERROR/FAILED` 红色，`QUEUED/FINALIZING` 黄色，`DISABLED/CANCELED` 中性。
- 组件只用 shadcn/ui，新增组件走 `npx shadcn@latest add ...`；不引入第二套 UI 库。
- 不做 mock/占位数据；空态显示真实空列表。

### 15.4 角色与按钮模板

- `extensions/role/api/role-api-template-live-source.yaml`：
  - view：`livesources` get/list、`livesources/summary` get、`livesources/health` get、`liverecordings` get/list、`liverecordings/query` list、`liverecordings/active` list。
  - manage：在 view 基础上，`livesources/from-url` create、`livesources/resolve` create、`livesources/record` create、`livesources` update/delete、`liverecordings/stop` create、`liverecordings/download-url` create、`liverecordings/retry` create、`liverecordings/batch` delete。
- `extensions/role/button/role-button-template-live.yaml`：`ui-type: button`、`ui-menu: console.live`、`ui-operation`、`ui-label`、`ui-order`、`ui-permissions`，分别对应创建、解析、录制、停止、下载、删除、重试。
- 权限字符串建议 `live:view`/`live:manage`，与菜单 `ui-permissions` 对齐；具体以现有 role 模板的 `ui-permissions` 命名为准。
- 新增/修改角色、菜单、按钮必须按 `.agents/rules/extensions.md` 的目录与命名放置。

## 16. 测试方案

### 16.1 单元测试

| 测试类 | 覆盖 |
| --- | --- |
| `LiveRoomLinkParserTest` | 合法链接、尾斜杠/query/443、http、其他 host、userinfo、非 443 端口、多段路径、非数字、空串、非法 URI |
| `PacePayloadExtractorTest` | 多个 push、最后一个生效、引号/转义/嵌套、`c:` 前缀、无 push、JSON 数组取最后元素、非法 JSON |
| `DouyinRoomPayloadMapperTest` | `room.status==2`、缺失 status 用流兜底、无流离线、HLS/FLV 顺序、null 过滤、主播字段缺失 |
| `LiveStreamUrlValidatorTest` | 只允许 http/https、拒绝 `file:`/`concat:`、拒绝私有 IP/回环、端口限制 |
| `LiveResolveCacheTest` | 缓存命中/TTL、离线 TTL、锁竞争等待、锁释放 Lua、Redis 故障降级、坏缓存删除 |
| `LiveCooldownTest` | 10m 起、翻倍、1h 封顶、成功清零、手动不绕过冷却 |
| `LiveSourceStateMachineTest` | 所有迁移、连续失败阈值、离线/在线、启用/禁用、录制开始/结束 |
| `LiveRecorderAdmissionTest` | 立即准入、排队、队满拒绝、释放后出队 |
| `LiveSegmentBoundaryTest` | 只接受 10/30、关键帧+时长切段、首包不切、无关键帧不切、参数/地址变化强制切 |
| `LivePartMergerTest` | 按索引顺序合并、字节与逐段拼接一致、tmp+fsync+原子改名、成功后删分片、失败保留分片、崩溃残留 tmp 清理 |
| `LiveStoragePolicyResolverTest` | 默认策略为 LOCAL 拒绝、策略不存在/未就绪拒绝、对象存储接受、动态切换、自动录制缺策略 |
| `LivePartUploaderTest` | 小成品走 upload 不 spool、大成品走 initiateUpload/putPart/completeUpload 且按文件 range、失败 abort+保留本地、重试 3 次、驱动不支持 multipart 拒绝 |
| `LiveDiskGuardTest` | max-local-bytes/max-local-segments/max-pending-parts/min-free-disk 触发合并、仍超限停录、`merge.interval=0` 仍受保护 |
| `LiveSourceReconcilerTest` | 加 finalizer、删除移出 registry、等待活动录制、超时强停、移除 finalizer |
| `LiveRecordingReconcilerTest` | 逐 part 先删对象、UPLOADING 先 abort、本地清理、缺失对象视为成功、失败 requeue、10 次后保持 finalizer 并告警、活动录制先停再删 |
| `StorageServicePurgeObjectFirstTest` | 先删对象再删记录、驱动失败保留记录、对象不存在幂等、不改变原 purge 语义 |
| `LiveRecordingRecoveryTest` | 孤儿分片补清单、MERGING 重建、未上传成品补传、全 UPLOADED 完成、无数据 CANCELED、重复恢复幂等 |
| `LiveSourceEndpointTest` | from-url 合法/重复/离线/限流/解析失败、LOCAL 策略拒绝、参数校验 |
| `LiveRecordingEndpointTest` | query 参数、分页上限、stop 状态机、download-url 按 part/超 100 要求指定、batch 返回 ACCEPTED、重试模式 |
| `LiveMetricsTest` | SimpleMeterRegistry 计数/Gauge、无高基数标签 |
| `JavaCvNativeLoadTest` | `new FFmpegFrameGrabber("...")` 不抛 native 加载异常（不联网） |

### 16.2 测试数据

- 解析测试用人工构造的 HTML/JSON 片段，只验证提取逻辑；不把真实房间数据写进仓库。
- 录制/上传测试用临时目录和 `@TempDir`；不生成大文件，可用几 KB 的合法 TS 片段或 mock 驱动。
- Redis 测试用 Mockito mock `ReactiveStringRedisTemplate`；不依赖本地 Redis。
- 不做 `@Disabled`、不写占位断言；行为变更同步改测试。

### 16.3 命令

```bash
./gradlew :appkernel:test
./gradlew :api-docs:validateOpenApiDocs
cd web && npm run lint
cd web && npm run format:check
cd web && npm run build
```

聚焦测试：

```bash
./gradlew :appkernel:test --tests 'cn.tolim.pudans.core.live.*'
```

### 16.4 手工验收（真实房间）

1. 不配置 Cookie，用真实抖音直播间链接创建源，确认 roomId/标题/主播正确；日志无 Cookie。
2. 开播时 60s 内进入 `LIVE/RECORDING`；设置 `segment-seconds=10`，录 2 分钟，确认本地生成约 12 个 `segment-*.ts`，`segments.jsonl` 行数一致。
3. 把 `merge.interval` 临时设为 60s，确认 1 分钟后生成 `part-00001.ts` 并上传对象存储；上传成功后本地分片和成品被删除，`status.parts[0].state=UPLOADED`。
4. 通过 `download-url` 下载成品，确认预签名直连、TS 可播放、时长/大小与 part 元数据一致。
5. 录制中确认本地目录只剩当前正在写的分片和未上传成品，没有“已上传但还在本地”的成品。
6. 把 `max-local-bytes` 调到很小，确认触发提前合并/上传；把对象存储策略改成不可用，确认停止拉流并标 `LIVE_STORAGE_BACKLOG`，不继续写满磁盘。
7. 录制中手动停止，确认当前分片关闭、剩余分片合并、最后一个成品上传、状态 `COMPLETED`，本地目录清空。
8. 录制中断网/切流，确认重解析/退避，不无限重试旧地址；地址变化时旧地址成品先合并。
9. 删除录制，确认对象存储对象先消失、本地目录再消失、最后记录消失；审计可查。
10. 把默认策略设为 LOCAL，确认创建自动录制源被拒绝（`LIVE_STORAGE_POLICY_INVALID`），不落本地兜底。
11. 录制中 kill -9 应用，重启后确认恢复扫描补齐合并/上传，最终 `COMPLETED` 或明确 `FAILED`，无悬挂 `RECORDING`。
12. 检查 Actuator `/actuator/metrics/live.resolve.success`、`live.recording.part.upload.fail`、`live.recording.local.bytes` 等指标。

## 17. 落地计划

### Phase 0：JavaCV native 与流复制验证（0.5–1 人日，先做）

- 只加依赖（版本目录 + `appkernel/build.gradle`），写一个不联网的 `JavaCvNativeLoadTest`。
- 在 `eclipse-temurin:25-jre` 容器里跑 `./gradlew :appkernel:test --tests '*JavaCvNativeLoadTest*'`；缺库则补 `libgomp1`。
- 用一段公开测试流验证 `grabPacket/recordPacket` 流复制、10/30s 切段、`FileChannel.transferTo` 合并、`release()`；本地 TS 文件测试只在测试夹具中临时允许 `file` 协议，生产白名单保持 `http,https,tcp,tls,crypto`。
- 退出标准：native 能加载；录制 1 分钟文件可播放；合并后的成品可播放；进程 RSS 不持续增长。
- 若失败：改用系统 `ffmpeg` CLI（Docker 安装 ffmpeg），由 `ProcessBuilder` 启动 `-c copy -f segment`，Java 侧只做进程监管；接口层不变，但需额外进程回收/日志/超时设计。

### Phase 1：解析与源基础（2–3 人日）

- `contract/core/extension/live` 枚举与两个 CRD；`SchemeInitializer` 注册与索引。
- `contract/live` 的 `LiveRoomInfo/LiveStreamUrl/LiveErrorCode` 与 HTTP 请求/响应 DTO。
- `LiveRoomLinkParser`、`LiveWebClientConfiguration`、`PacePayloadExtractor`、`DouyinLiveRoomResolver`。
- `LiveResolveCache`（Redis 缓存/锁/冷却）、`LiveResolveService`、`LiveMetrics` 初始计数。
- `LiveSourceEndpoint` 的 `resolve/from-url`；RBAC 先给最小权限；单测。
- 退出标准：不配 Cookie 的真实链接可解析出标题/主播/流地址；非法链接/限流/失败错误码正确；缓存命中指标可见。

### Phase 2：直播源与监控（2–3 人日）

- `LiveSourceService/Validator/Registry/StateMachine/Reconciler`。
- `LiveMonitorWorker`、leader 租约、`nextCheckAt` 调度、状态事件。
- `LiveSourceSummaryService`、`health/summary` 端点；前端源列表/汇总/创建对话框。
- 退出标准：启用源按间隔检测；在线/离线/错误状态正确；禁用停止检测；删除先停录；前端可操作。

### Phase 3：录制、分片合并与对象存储（6–8 人日，最大块）

- 前置：先创建并验证对象存储策略（内置默认是 `local`，会被 `LiveStoragePolicyResolver` 拒绝）；用真实策略跑通上传/预签名后再开自动录制。
- `LiveRecorderService/Registry/Admission/Task/SegmentWriter/StreamOpener`。
- `LiveStoragePolicyResolver`、`LivePartMerger`、`LivePartUploader`、`LiveDiskGuard`。
- `LiveRecording` 创建、状态写回、停止、重解析/退避、失败处理、启动恢复。
- `StorageService.purgeObjectFirst`、`StorageService.initiateUpload/putPart/completeUpload` 接入。
- 退出标准：真实直播录 10 分钟，每 10/30s 一个本地分片；按 1 小时（或测试 60s）合并成品并上传对象存储；合并成功后分片删除、上传成功后本地成品删除；断流能重连/换地址；并发上限生效；RSS 稳定。

### Phase 4：录制管理与清理（2–3 人日）

- `LiveRecordingQueryService`、`query/active/stop/download-url/retry/batch` 端点。
- `LiveRecordingReconciler`、`LiveRecordingCleanupTask`、保留策略。
- 前端录制表、筛选、按 part 下载、删除、重试、成品详情。
- 退出标准：列表/详情/按 part 下载/删除/保留清理全通；删除先对象后记录；本地无残留；审计可查。

### Phase 5：加固与验收（1–2 人日）

- 指标/告警补全，Grafana/Actuator 核对。
- 压测：10 个源、2 路并发录制、每小时成品上传；观察内存/磁盘/线程/对象存储失败。
- 安全复核：SSRF、FFmpeg 协议注入、Cookie 日志、路径穿越、RBAC、预签名 URL 时效。
- 文档：部署说明（对象存储策略/native/磁盘水位）、运维手册（限流/合并/上传失败/清理）、OpenAPI。
- 退出标准：第 16.4 节手工验收全过；`./gradlew test`、前端 lint/format/build 通过。

总工期约 13–19 人日（不含抖音风控对抗和对象存储分片直传适配的不确定性）。

## 18. 关键决策与风险

| 决策 | 选择 | 备选 | 理由 |
| --- | --- | --- | --- |
| 资源数量 | 2 个 CRD，成品放 status，分片只在本地清单 | 3 个 CRD / 关系表 | 复用 Extension，少 controller/RBAC/路由；分片数量大，不适合写 CRD |
| 源唯一性 | 名称 `douyin-{roomId}` | 应用层查重 + 唯一索引 | 主键天然防并发；禁用也不重复 |
| 解析执行 | 状态机 + Jackson | GraalJS / Jsoup | 不执行远程脚本，依赖小，攻击面小 |
| Cookie | 默认不发送，仅环境变量可选 | 强制配置 Cookie | 直播间公开页可解析；少一个易失效的部署依赖 |
| 录制方式 | JavaCV 流复制 | 转码 / 外部 ffmpeg | 不耗 CPU、不损画质；外部进程作为 Phase 0 备选 |
| 分片策略 | 10s 或 30s，关键帧对齐 | 300s 大段 | 崩溃最多丢 10–30s；本地缓存压力小 |
| 合并策略 | 默认 1 小时合并一个成品，可配置/关闭 | 永不合并 / 每段一个成品 | 成品数量可控、下载体验好；关闭仍受磁盘水位保护 |
| 存储路径 | 分片只本地，成品只对象存储 | 本地作为最终存储 | 满足“对象存储接通、可动态选择”，避免容器磁盘成为数据盘 |
| 存储策略 | 每次创建/上传校验 `type != LOCAL` 且 ready | 信任默认策略 | `defaultPolicy()` 可能返回 LOCAL，必须显式拒绝 |
| 上传方式 | 小成品 upload，大成品 initiateUpload/putPart/completeUpload | 统一 upload + spool | 避免整小时成品 spool 造成 2 倍磁盘峰值 |
| 删除语义 | 源先停录再删；录制先删对象、再删本地、最后删记录 | 拒绝删除 / 记录先删 | 符合需求，避免 200 但资源卡住；对象不泄漏 |
| 离线语义 | 正常状态，不报错 | 当解析失败 | 下播是常态，避免误告警 |
| 录制下载 | 预签名优先，应用签名回退 | 全部应用代理 | 满足 C10，兼容 `proxyDownload` 策略 |
| 多实例 | leader 租约 + 录制心跳 + 恢复锁 | 只支持单实例 | 成本低，避免重复录制/重复恢复 |
| 状态写回 | 迁移立即、心跳/进度 10s 节流 | 每包写库 | 避免数据库被打爆 |

### 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 抖音页面结构/风控变化 | 解析失败、限流 | 解析器容错、错误码、冷却、告警；不把 Cookie 当唯一解；Phase 1 用真实页面验证 |
| JavaCV native 打包 | 镜像启动/录制失败 | Phase 0 容器验证；补 `libgomp1`；备选系统 ffmpeg |
| 镜像体积 | 部署变慢 | 只加 ffmpeg/javacpp classifier，不用 `javacv-platform` |
| 磁盘峰值 | 录制失败 | 分片本地+成品上传后即删；水位 6GiB/8GiB；大成品不 spool；积压停录 |
| 合并 I/O 拖慢拉流 | 丢包/断流 | 合并独立 `live-merge` 线程，不在录制线程复制；一次只合并一个 part |
| 对象存储驱动不支持 multipart | 大成品无法上传 | 创建时校验驱动能力；不支持则拒绝该策略，提示换 S3/OSS/COS 等 |
| 默认策略被改成 LOCAL | 成品落本地 | 创建/上传双校验 `type != LOCAL`；变更策略后录制失败并告警 |
| 对象存储策略不支持预签名 | 下载走代理 | `sourceUrl` 为空回退 `downloadUrl`；文档提示开启 `proxyDownload=false` |
| 单实例内存状态 | 重启丢任务 | 启动恢复扫描本地清单 + `LIVE_RECORDER_LOST`；录制心跳/恢复锁 |
| 长录制本地积压 | 磁盘耗尽 | 1 小时强制合并、上传成功即删、待上传上限 2、min-free 8GiB |
| HLS 延迟/卡顿 | 录制体验 | 默认 HLS_FIRST，可配置 FLV_FIRST；按实测调整 |
| FFmpeg 流复制兼容性 | 某些流无法播放 | 只支持 TS/H.264/AAC；失败码可观测；不做转码 |
| 1 小时成品过大 | 上传慢/失败重试成本高 | 分片直传、失败只重传该 part；可把 `merge.interval` 调到 10–30 分钟 |

### 依赖与许可证

- `javacv`/`ffmpeg` 的 native 包体积和许可证需在 Phase 0 由项目法务/运维确认；不引入 `javacv-platform`。
- 不引入 Jsoup/GraalJS/Playwright；不新增通知库。
- 对象存储能力复用现有 `StoragePolicy`/驱动，不新增 SDK 依赖。

## 19. 待确认问题

1. 抖音 `room.status` 的在线值是否稳定为 `2`；若页面字段变化，是否接受“非空流地址”兜底？建议保留兜底并监控解析失败率。
2. 生产默认对象存储策略选哪一个（S3/KS3/OSS/COS/OBS）？内置 `storage-policy-default` 是 `local`，必须另建对象存储策略；是否已开启 `status.ready=true` 且驱动支持原生 multipart？这决定 1 小时成品能否分片直传。
3. 默认保留天数 7 天是否符合合规/容量要求；`retention.days<=0` 是否允许生产使用。
4. 生产是否严格单实例；若多实例，leader 租约是否允许 Redis 故障时暂停自动录制（建议允许，宁可不录也不重复录）。
5. 默认存储策略是否开启 `proxyDownload`；若开启，C10 只能走应用代理，需确认带宽。
6. 是否需要“手动填流地址”的 break-glass 入口；当前按需求不做，避免绕过解析。
7. 删除直播源是否要级联删除历史录制；当前不级联，保留资产。
8. 合并间隔默认 1 小时是否合适；超长直播是否要改成 30 分钟，降低单个成品大小和失败重传成本。
9. 本地水位默认 6GiB/8GiB 是否匹配容器磁盘；如果容器磁盘小于 20GiB，需要下调并加磁盘告警。
10. 单个成品超过 100 个时要求显式 `partIndex` 下载，是否接受；或改为分页下载列表。

## 20. 需求追踪矩阵

| ID | 设计位置 | 验收要点 |
| --- | --- | --- |
| R1 | 6.1 | 只接受 `live.douyin.com`，提取 roomId，拒绝 SSRF 变体 |
| R2 | 6.2 | 无 Cookie 返回 title/roomId/anchor/有序流地址；解析在 boundedElastic |
| R3 | 6.3 | Redis 45s/20s 缓存；single-flight；命中指标 |
| R4 | 6.4 | 失败/离线/限流错误码；冷却 10m 起翻倍；冷却期不自动解析 |
| R5 | 6.5 | 断流强制解析一次；新地址切段续录；旧地址不无限重试 |
| R6 | 6.6/12.1 | 四个解析指标接入 Actuator |
| S1 | 7.1 | 只填链接创建；离线也创建；限流/失败不创建；自动录制校验对象存储 |
| S2 | 4.2/7.2 | 字段齐全（segment 10/30、merge、storagePolicy）；通用 CRUD；前端只暴露安全字段 |
| S3 | 7.3 | `douyin-{roomId}` 唯一；重复 409 |
| S4 | 5.1/7.4 | 七状态迁移；状态变更事件 |
| S5 | 7.5 | 启用进 registry；停用移出并停录 |
| S6 | 7.6 | 删除先停录；60s 超时；审计；历史录制保留 |
| C1 | 8.1 | 在线+自动录制创建任务；记录 roomId/开始/格式；录制前解析对象存储策略 |
| C2 | 8.3 | 每任务独占 grabber；流复制；boundedElastic |
| C3 | 8.4 | 只接受 10/30s；关键帧+时长切段；分片只写本地清单，不逐片上传 |
| C4 | 8.5 | 断流重解析；新地址切段续录；旧地址有限重试 |
| C5 | 8.5 | 1s→30s 指数退避+抖动；上限 5 次 |
| C6 | 8.6 | 手动停止；关闭当前分片；等合并/上传；`finally` release |
| C7 | 8.7 | 小时合并成品；成品只上传对象存储；大文件 multipart 不 spool；成功后删分片/本地成品 |
| C8 | 8.8 | 解析/拉流/合并/存储/策略/磁盘不同错误码；失败保留 parts+本地；可重试 |
| C9 | 8.9 | 源/时间/状态筛选；分页；RBAC；展示 partCount/localBytes |
| C10 | 8.10 | 按 part 预签名下载；超 100 要求显式 partIndex；TTL 受限；不暴露对象键 |
| C11 | 8.11 | 先删对象、再删本地、最后删记录；批量；审计；活动录制拒绝 |
| C12 | 8.12 | 定时按保留天数清理；失败不阻塞；幂等；启动清孤儿目录 |
| C13 | 8.2 | maxConcurrent=2；队列 10；超限拒绝；指标 |
| M1 | 9.1 | 可配置间隔检测；未开播不录 |
| M2 | 9.2 | 在线/离线/录制/最后解析/最后流时间 |
| M3 | 9.3 | 活动/排队/失败数；lastPacketAt；分片/成品计数 |
| M4 | 9.4 | 断流/失败事件；复用 Webhook；不新增通知库 |
| M5 | 9.5 | 连续限流告警；提示降低解析频率/检查出口 IP，不提示换 Cookie |
| M6 | 9.6 | 列表/详情/汇总；`contract.live`；RBAC |
| M7 | 12.1 | active/failed/rate_limited、part.upload.fail、local.bytes 等 Actuator 指标 |

## 21. 文件清单

### 21.1 后端新增

```text
appkernel/src/main/java/cn/tolim/pudans/contract/core/extension/live/
  LiveSource.java
  LiveRecording.java
  LivePlatform.java
  LiveSourceState.java
  LiveRecordingState.java
  LiveRecordingFormat.java
  PartState.java

appkernel/src/main/java/cn/tolim/pudans/contract/live/
  LiveRoomInfo.java
  LiveStreamUrl.java
  LiveStreamProtocol.java
  LiveErrorCode.java
  LiveSourceStateChangedEvent.java

appkernel/src/main/java/cn/tolim/pudans/contract/live/
  LiveSourceCreateRequest.java
  LiveSourceSummary.java
  LiveSourceHealth.java
  LiveRecordingQuery.java
  LiveRecordingPage.java
  LiveRecordingView.java
  LiveRecordingPartView.java
  PartDownload.java
  LiveRecordingDownload.java
  BatchDeleteRequest.java
  BatchDeleteItem.java
  LiveRecordingRetry.java

appkernel/src/main/java/cn/tolim/pudans/core/live/resolve/
  LiveRoomLinkParser.java
  ParsedLiveRoom.java
  LiveWebClientConfiguration.java
  PacePayloadExtractor.java
  DouyinRoomPayload.java
  DouyinRoomPayloadMapper.java
  DouyinLiveRoomResolver.java
  LiveResolveCache.java
  LiveResolveService.java
  LiveStreamUrlValidator.java

appkernel/src/main/java/cn/tolim/pudans/core/live/source/
  LiveSourceService.java
  LiveSourceValidator.java
  LiveSourceRegistry.java
  LiveSourceReconciler.java
  LiveSourceStateMachine.java
  LiveSourceStatusService.java
  LiveSourceSummaryService.java

appkernel/src/main/java/cn/tolim/pudans/core/live/endpoint/
  LiveSourceEndpoint.java
  LiveRecordingEndpoint.java

appkernel/src/main/java/cn/tolim/pudans/core/live/monitor/
  LiveMonitorWorker.java
  LiveLeaderLease.java
  LiveMetrics.java

appkernel/src/main/java/cn/tolim/pudans/core/live/alert/
  LiveAlertService.java

appkernel/src/main/java/cn/tolim/pudans/core/live/recorder/
  LiveRecorderService.java
  LiveRecorderRegistry.java
  LiveRecorderAdmission.java
  LiveRecorderTask.java
  LiveSegmentWriter.java
  LiveSegment.java
  LiveSegmentManifest.java
  LiveStreamOpener.java
  LivePartMerger.java
  LivePartUploader.java
  LiveStoragePolicyResolver.java
  LiveDiskGuard.java
  LiveRecordingReconciler.java
  LiveRecordingRecovery.java
  LiveRecordingCleanupTask.java

appkernel/src/main/java/cn/tolim/pudans/core/live/recording/
  LiveRecordingService.java
  LiveRecordingStatusService.java
  LiveRecordingQueryService.java
```

### 21.2 后端修改

```text
appkernel/src/main/java/cn/tolim/pudans/infra/setting/SchemeInitializer.java   // 注册两个 CRD 与索引
appkernel/src/main/java/cn/tolim/pudans/core/storage/StorageService.java       // 新增 purgeObjectFirst
appkernel/src/main/java/cn/tolim/pudans/infra/properties/LiveProperties.java   // 新增配置属性（当前 infra/properties 下）
appkernel/src/main/resources/application.yaml                                  // 新增 pudans.live 默认配置
gradle/libs.versions.toml                                                       // javacv/ffmpeg 版本
appkernel/build.gradle                                                          // 依赖与 native classifier
```

`StorageService` 复用 `initiateUpload/putPart/completeUpload/abortUpload`，`StorageUploadService` 预计不改；若需暴露 `chunkSize` 判断能力，仅加只读访问器，不复制上传实现。

### 21.3 前端新增/修改

```text
web/src/routes/_authenticated/_workspace/(console)/live.tsx   // 新增
web/src/modules/workspace/console/live/index.tsx              // 新增
web/src/modules/workspace/console/index.ts                    // 增加 export
```

### 21.4 扩展资源

```text
appkernel/src/main/resources/extensions/workspace/console/workspace-console-menu-template-live.yaml
appkernel/src/main/resources/extensions/role/api/role-api-template-live-source.yaml
appkernel/src/main/resources/extensions/role/button/role-button-template-live.yaml
```

### 21.5 测试

```text
appkernel/src/test/java/cn/tolim/pudans/core/live/...
appkernel/src/test/java/cn/tolim/pudans/contract/live/...
appkernel/src/test/java/cn/tolim/pudans/core/storage/StorageServicePurgeObjectFirstTest.java
```

### 21.6 文档

```text
docs/live-monitoring-plan.md          // 本文
docs/live-operations.md               // Phase 5 运维手册（对象存储策略/合并/上传失败/磁盘水位/清理）
```

## 22. 评审检查清单

- [ ] `SchemeInitializer` 已注册两个 CRD；索引字段与查询一致。
- [ ] `from-url` 不请求用户原始 URL；只请求规范化的 `live.douyin.com/{roomId}`。
- [ ] 解析器不执行 JS；不引入 Jsoup/GraalJS/Playwright；默认不发送 Cookie。
- [ ] `FFmpegFrameGrabber/Recorder` 只在录制线程创建；每个任务独占；`finally` release。
- [ ] `recordPacket` 前判断切段；`av_packet_unref` 在 `finally`。
- [ ] 分片只接受 10s/30s，按关键帧+时长切；只写本地 `segments.jsonl`，不逐片上传。
- [ ] 合并按索引顺序、tmp+fsync+原子改名，成功后删分片；失败保留分片。
- [ ] 成品只上传对象存储；策略校验 `status.ready=true` 且 `type != LOCAL`。
- [ ] 大成品走 `initiateUpload/putPart/completeUpload` 按文件 range 上传；不 spool 整文件、不聚合 `byte[]`。
- [ ] 上传成功后删本地成品；本地仅缓存分片和未上传成品。
- [ ] 磁盘水位（6GiB/360 段/2 个待传/8GiB 空闲）生效；`merge.interval=0` 仍受保护。
- [ ] 录制状态不每包写库；心跳/进度 10s 节流。
- [ ] 删除顺序：对象 → 本地 → 记录；finalizer 可重入、可重试、可告警。
- [ ] 活动录制时删除被拒绝或先停止；不产生 200 但资源卡住。
- [ ] 启动恢复扫描本地清单，补齐合并/上传，无悬挂 `RECORDING`。
- [ ] RBAC 只授权安全子路径；通用 POST/DELETE 不授权。
- [ ] 冲突/错误码用 `ApiException`；不把上游 HTML/Cookie/流地址/对象键写进 reason。
- [ ] 指标标签低基数；事件 payload 不含流地址/Cookie/对象键。
- [ ] 前端无 mock；空态真实；存储策略下拉只列 ready 且非 local；轮询在无活动任务时停止。
- [ ] 测试覆盖解析（无 Cookie）、状态机、准入、10/30 切段、合并、上传、finalizer、恢复、端点；无 `@Disabled`。
- [ ] Phase 0 native 在 `eclipse-temurin:25-jre` 容器验证通过。

## 23. 术语

| 术语 | 含义 |
| --- | --- |
| CRD | 本项目对 Extension 资源的称呼，实际存储于 `extensions` 表 |
| roomId | 抖音直播间数字 ID，来自 `live.douyin.com/{roomId}` |
| 流复制 | FFmpeg 不重新编码，直接复制输入音视频包到 TS |
| 分片 | 按关键帧+10/30s 切出的本地 TS 文件，只作缓存，不是交付物 |
| 成品 | 多个本地分片按顺序合并出的 TS 文件，是唯一上传对象存储的交付物 |
| 合并 | 把一段时间内的分片按索引顺序拼接为一个成品，默认每小时一次 |
| 对象存储策略 | `StoragePolicy`，成品必须落在 ready 且 type 非 LOCAL 的策略上 |
| finalizer | Extension 删除前的清理钩子；清空后 GC 才物理删除 |
| single-flight | 同一 roomId 同时只允许一个解析请求打上游 |
| 冷却 | 被限流后一段时间内不自动解析 |
