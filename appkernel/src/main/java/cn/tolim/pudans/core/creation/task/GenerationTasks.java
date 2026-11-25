package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskAttemptView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskPage;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskResultView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStartRequest;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStatusView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskSummary;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.core.creation.provider.GenerationModelCatalog;
import cn.tolim.pudans.core.creation.provider.GenerationModelProfile;
import cn.tolim.pudans.core.creation.provider.MediaGenerationGateway;
import cn.tolim.pudans.core.creation.provider.MediaGenerationGateways;
import cn.tolim.pudans.core.storage.FileRecords;
import cn.tolim.pudans.core.storage.StorageFileQuery;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.GenerationTaskStore;
import cn.tolim.pudans.infra.utils.Digests;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationTasks {

    private static final Set<GenerationTask.Status> CANCELLABLE = Set.of(
            GenerationTask.Status.QUEUED,
            GenerationTask.Status.RETRY_QUEUED,
            GenerationTask.Status.DISPATCHING,
            GenerationTask.Status.PROVIDER_RUNNING);

    private static final int RESULT_PAGE_SIZE = 100;

    private static final int RESULT_MAX_PAGES = 50;

    private static final List<GenerationTask.Status> PROCESSING_EXPECTED = List.of(
            GenerationTask.Status.QUEUED,
            GenerationTask.Status.RETRY_QUEUED,
            GenerationTask.Status.DISPATCHING,
            GenerationTask.Status.PROVIDER_RUNNING,
            GenerationTask.Status.DOWNLOADING,
            GenerationTask.Status.ARCHIVING);

    private static final List<GenerationTask.Status> DISPATCHABLE = List.of(
            GenerationTask.Status.QUEUED, GenerationTask.Status.RETRY_QUEUED);

    private final GenerationTaskStore store;

    private final AiTaskQueue queue;

    private final ObjectProvider<GenerationModelCatalog> catalogs;

    private final MediaGenerationGateways gateways;

    private final GenerationTaskEvents events;

    private final GenerationTaskCancellations cancellations;

    private final FileRecords fileRecords;

    private Clock clock = Clock.systemUTC();

    public record StartResult(GenerationTaskSummary task, boolean created) {}

    // 创建任务前先由模型目录校验模型能力与 settings schema。
    public Mono<StartResult> start(
            RequestIdentity identity, GenerationTaskStartRequest request, String idempotencyKey) {
        var idempotencyError = validateIdempotency(idempotencyKey);
        if (idempotencyError != null) {
            return Mono.error(idempotencyError);
        }
        if (request == null) {
            return Mono.error(requestRequired());
        }
        var scopedKey = scopedIdempotencyKey(identity, idempotencyKey);
        return store.findByIdempotencyKey(scopedKey)
                .flatMap(existing -> Objects.equals(existing.ownerId(), identity.ownerId())
                                && Objects.equals(existing.workspaceId(), identity.workspaceId())
                        ? Mono.just(new StartResult(summary(existing), false))
                        : Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "generation-idempotency-key-conflict",
                                "幂等键已被其他归属使用。")))
                .switchIfEmpty(Mono.defer(() -> {
                    var validation = validateStart(request);
                    if (validation != null) {
                        return Mono.error(validation);
                    }
                    var mediaType = GenerationTask.MediaType.fromCode(request.mediaType());
                    var operation = GenerationTask.Operation.fromCode(request.operation());
                    var catalog = catalogs.getIfAvailable();
                    if (catalog == null) {
                        return Mono.error(catalogMissing());
                    }
                    return catalog.resolve(request.modelId(), identity.workspaceId())
                            .switchIfEmpty(Mono.error(modelMissing()))
                            .flatMap(profile -> {
                                if (!profile.mediaTypes().contains(mediaType)
                                        || !profile.operations().contains(operation)) {
                                    return Mono.error(new ApiException(
                                            HttpStatus.BAD_REQUEST,
                                            "generation-model-capability-unsupported",
                                            "当前模型不支持该媒体类型或生成操作。"));
                                }
                                var gateway = gateways.find(profile.provider()).orElse(null);
                                if (gateway == null) {
                                    return Mono.error(providerMissing());
                                }
                                return catalog
                                        .validate(
                                                profile,
                                                mediaType,
                                                operation,
                                                request.prompt(),
                                                settings(request),
                                                inputAssetIds(request))
                                        .then(ensureParent(identity, request.parentTaskId()))
                                        .then(Mono.defer(() -> insert(
                                                identity,
                                                request,
                                                scopedKey,
                                                mediaType,
                                                operation)));
                            });
                }));
    }

    public Mono<GenerationTaskPage> mine(
            RequestIdentity identity, Instant before, int limit) {
        var boundedLimit = Math.max(1, Math.min(limit, 100));
        return store.listMine(identity.ownerId(), identity.workspaceId(), before, boundedLimit)
                .map(GenerationTasks::summary)
                .collectList()
                .map(items -> {
                    var nextCursor = items.size() < boundedLimit
                            ? null
                            : items.get(items.size() - 1).createdAt().toString();
                    return new GenerationTaskPage(items, nextCursor);
                });
    }

    public Mono<GenerationTaskStatusView> status(RequestIdentity identity, UUID taskId) {
        return requireOwned(identity, taskId)
                .flatMap(task -> store.listAttempts(taskId)
                        .map(GenerationTasks::attemptView)
                        .collectList()
                        .map(attempts -> view(task, attempts)));
    }

    public Mono<GenerationTaskResultView> result(RequestIdentity identity, UUID taskId) {
        return requireOwned(identity, taskId).flatMap(task -> {
            if (task.status() != GenerationTask.Status.SUCCEEDED) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "generation-task-not-finished",
                        "生成任务尚未成功完成。"));
            }
            return resultView(task);
        });
    }

    // 取消先写 Redis 标记并尝试供应商取消，数据库状态是最终事实。
    public Mono<GenerationTaskSummary> cancel(RequestIdentity identity, UUID taskId) {
        return requireOwned(identity, taskId).flatMap(task -> {
            if (task.status() == GenerationTask.Status.CANCELLED) {
                return Mono.just(summary(task));
            }
            if (!CANCELLABLE.contains(task.status())) {
                return Mono.error(notCancellable());
            }
            var marks = cancellations.markTask(task.id());
            if (task.runId() != null) {
                marks = marks.then(cancellations.markRun(task.runId()));
            }
            return marks
                    .then(cancelProvider(task))
                    .flatMap(outcome -> store.cancel(
                                    task.id(),
                                    CANCELLABLE,
                                    outcome.errorCode(),
                                    outcome.errorMessage(),
                                    now())
                            .flatMap(cancelled -> events
                                    .publish(cancelled, GenerationTaskEvents.TASK_CANCELLED, cancelledData(cancelled))
                                    .thenReturn(summary(cancelled)))
                            .switchIfEmpty(Mono.defer(() -> requireOwned(identity, taskId)
                                    .flatMap(current -> current.status() == GenerationTask.Status.CANCELLED
                                            ? Mono.just(summary(current))
                                            : Mono.error(notCancellable())))));
        });
    }

    // 已发送请求的重试必须先与供应商对账，禁止重复提交和重复计费。
    public Mono<GenerationTaskSummary> retry(RequestIdentity identity, UUID taskId) {
        return requireOwned(identity, taskId).flatMap(task -> {
            if (task.status() != GenerationTask.Status.FAILED) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "generation-task-not-retryable",
                        "只有失败任务可以重试。"));
            }
            return store.countAttempts(taskId).flatMap(count -> {
                if (count >= GenerationTaskKeys.MAX_ATTEMPTS) {
                    return Mono.error(new ApiException(
                            HttpStatus.CONFLICT,
                            "generation-task-attempts-exhausted",
                            "任务尝试次数已达上限。"));
                }
                return routeFor(task.modelId(), task.workspaceId()).flatMap(route -> {
                    if (!task.providerRequestSent()) {
                        return enqueueRetry(task, false, null);
                    }
                    return latestAttempt(taskId)
                            .flatMap(attempt -> route.gateway()
                                    .find(reference(task, route.profile(), attempt.orElse(null)))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .timeout(GenerationTaskTimeouts.PROVIDER_STATUS)
                                    .switchIfEmpty(Mono.error(providerStatusUnavailable()))
                                    .flatMap(state -> retryAcceptedTask(task, state))
                                    .onErrorResume(error -> error instanceof ApiException
                                            ? Mono.error(error)
                                            : Mono.error(providerStatusUnavailable())));
                });
            });
        });
    }

    // 读取任务并校验 owner 与 workspace，跨归属统一返回不存在。
    public Mono<GenerationTask> requireOwned(RequestIdentity identity, UUID taskId) {
        return store.findById(taskId)
                .filter(task -> Objects.equals(task.ownerId(), identity.ownerId())
                        && Objects.equals(task.workspaceId(), identity.workspaceId()))
                .switchIfEmpty(Mono.error(taskNotFound()));
    }

    Mono<GenerationTask> find(UUID taskId) {
        return store.findById(taskId);
    }

    Mono<Integer> nextAttemptNumber(UUID taskId) {
        return store.countAttempts(taskId).map(count -> Math.toIntExact(count + 1));
    }

    Mono<GenerationTaskStore.Processing> beginProcessing(
            GenerationTask task, GenerationTask.Attempt attempt) {
        return store.beginProcessing(task.id(), PROCESSING_EXPECTED, DISPATCHABLE, attempt);
    }

    Mono<GenerationTask> markProviderRequestStarted(UUID taskId) {
        return store.markProviderRequestStarted(taskId);
    }

    Mono<GenerationTask> confirmProviderRunning(UUID taskId, String providerTaskId) {
        return store.confirmProviderRunning(taskId, providerTaskId);
    }

    Mono<GenerationTask> transition(
            UUID taskId, Set<GenerationTask.Status> expected, GenerationTask.Status target) {
        return store.transition(taskId, expected, target);
    }

    Mono<GenerationTask> succeed(UUID taskId, UUID attemptId, List<String> resultAssetIds) {
        return store.succeed(taskId, attemptId, resultAssetIds, now());
    }

    Mono<GenerationTask> fail(
            UUID taskId,
            UUID attemptId,
            Set<GenerationTask.Status> expected,
            String errorCode,
            String errorMessage,
            boolean providerRequestSent,
            String providerTaskId) {
        return store.fail(
                taskId,
                attemptId,
                expected,
                errorCode,
                errorMessage,
                providerRequestSent,
                providerTaskId,
                now());
    }

    Mono<GenerationTask> cancelByProvider(
            UUID taskId, String errorCode, String errorMessage) {
        return store.cancel(
                        taskId,
                        Set.of(
                                GenerationTask.Status.DISPATCHING,
                                GenerationTask.Status.PROVIDER_RUNNING,
                                GenerationTask.Status.DOWNLOADING,
                                GenerationTask.Status.ARCHIVING),
                        errorCode,
                        errorMessage,
                        now())
                .switchIfEmpty(store.findById(taskId));
    }

    Mono<GenerationTask.Attempt> markAttemptSubmitted(
            UUID attemptId, String providerTaskId, String providerRequestId, UUID channelId) {
        return store.markAttemptSubmitted(attemptId, providerTaskId, providerRequestId, channelId);
    }

    Mono<GenerationTask.Attempt> markAttemptRunning(UUID attemptId) {
        return store.markAttemptRunning(attemptId);
    }

    Mono<GenerationTask.Attempt> markAttemptCancelled(UUID attemptId) {
        return store.markAttemptCancelled(attemptId, now());
    }

    Mono<Optional<GenerationTask.Attempt>> latestAttempt(UUID taskId) {
        return store.listAttempts(taskId)
                .collectList()
                .map(attempts -> attempts.isEmpty()
                        ? Optional.empty()
                        : Optional.of(attempts.get(attempts.size() - 1)));
    }

    Mono<List<StorageFile>> archivedFiles(GenerationTask task) {
        return archivedFiles(task, 1, new ArrayList<>());
    }

    static String attribute(StorageFile file, String name) {
        var attributes = file.getSpec() == null ? null : file.getSpec().getAttributes();
        return attributes == null ? null : attributes.get(name);
    }

    Mono<Boolean> cancelled(GenerationTask task) {
        return store.findById(task.id())
                .map(current -> current.status() == GenerationTask.Status.CANCELLED)
                .defaultIfEmpty(false)
                .flatMap(dbCancelled -> dbCancelled
                        ? Mono.just(true)
                        : cancellations.cancelled(task));
    }

    Mono<Route> routeFor(UUID modelId, String workspaceId) {
        var catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            return Mono.error(catalogMissing());
        }
        return catalog.resolve(modelId, workspaceId)
                .switchIfEmpty(Mono.error(modelMissing()))
                .flatMap(profile -> {
                    var gateway = gateways.find(profile.provider()).orElse(null);
                    return gateway == null
                            ? Mono.error(providerMissing())
                            : Mono.just(new Route(profile, gateway));
                });
    }

    record Route(GenerationModelProfile profile, MediaGenerationGateway gateway) {}

    private Mono<StartResult> insert(
            RequestIdentity identity,
            GenerationTaskStartRequest request,
            String scopedKey,
            GenerationTask.MediaType mediaType,
            GenerationTask.Operation operation) {
        var task = new GenerationTask(
                UUID.randomUUID(),
                identity.ownerId(),
                identity.workspaceId(),
                request.sessionId(),
                request.runId(),
                request.parentTaskId(),
                request.parentAssetId(),
                mediaType,
                operation,
                request.modelId(),
                request.prompt(),
                settings(request),
                inputAssetIds(request),
                GenerationTask.Status.QUEUED,
                false,
                null,
                List.of(),
                null,
                null,
                request.quotaReservationId(),
                scopedKey,
                now(),
                null,
                null);
        return store.insertIfAbsent(task).flatMap(insertion -> {
            if (!insertion.created()) {
                var existing = insertion.task();
                if (!Objects.equals(existing.ownerId(), identity.ownerId())
                        || !Objects.equals(existing.workspaceId(), identity.workspaceId())) {
                    return Mono.error(new ApiException(
                            HttpStatus.CONFLICT,
                            "generation-idempotency-key-conflict",
                            "幂等键已被其他归属使用。"));
                }
                return Mono.just(new StartResult(summary(existing), false));
            }
            var created = insertion.task();
            var data = new LinkedHashMap<String, Object>();
            data.put("taskId", created.id());
            data.put("status", created.status().getCode());
            data.put("mediaType", created.mediaType().getCode());
            data.put("operation", created.operation().getCode());
            return events.publish(created, GenerationTaskEvents.TASK_QUEUED, data)
                    .then(queue.enqueue(created.id(), "created"))
                    .thenReturn(new StartResult(summary(created), true))
                    .onErrorResume(error -> failQueued(created, error));
        });
    }

    private Mono<StartResult> failQueued(GenerationTask task, Throwable error) {
        log.warn("生成任务入队失败：task={}", task.id(), error);
        return store.fail(
                        task.id(),
                        null,
                        Set.of(GenerationTask.Status.QUEUED),
                        "generation-queue-unavailable",
                        "任务队列暂时不可用。",
                        false,
                        null,
                        now())
                .flatMap(failed -> events.publish(
                        failed, GenerationTaskEvents.TASK_FAILED, failedData(failed)))
                .then(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "generation-queue-unavailable",
                        "任务队列暂时不可用。")));
    }

    private Mono<GenerationTaskSummary> enqueueRetry(
            GenerationTask task, boolean providerRequestSent, String providerTaskId) {
        return store.retry(task.id(), providerRequestSent, providerTaskId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "generation-task-not-retryable",
                        "任务状态已变化，不能重试。")))
                .flatMap(queued -> {
                    var data = new LinkedHashMap<String, Object>();
                    data.put("taskId", queued.id());
                    data.put("status", queued.status().getCode());
                    data.put("retry", true);
                    data.put("resumed", providerRequestSent);
                    return events.publish(queued, GenerationTaskEvents.TASK_QUEUED, data)
                            .then(queue.enqueue(queued.id(), "retry"))
                            .thenReturn(summary(queued))
                            .onErrorResume(error -> store.fail(
                                            queued.id(),
                                            null,
                                            Set.of(GenerationTask.Status.RETRY_QUEUED),
                                            "generation-queue-unavailable",
                                            "任务队列暂时不可用。",
                                            providerRequestSent,
                                            providerTaskId,
                                            now())
                                    .then(Mono.error(new ApiException(
                                            HttpStatus.SERVICE_UNAVAILABLE,
                                            "generation-queue-unavailable",
                                            "任务队列暂时不可用。"))));
                });
    }

    private Mono<GenerationTaskSummary> retryAcceptedTask(
            GenerationTask task, MediaGenerationGateway.State state) {
        var phase = state.phase();
        if (phase == null) {
            return Mono.error(providerStatusUnavailable());
        }
        return switch (phase) {
            case SUCCEEDED -> enqueueRetry(
                    task,
                    true,
                    StringUtils.hasText(state.providerTaskId())
                            ? state.providerTaskId()
                            : task.providerTaskId());
            case FAILED, CANCELLED -> enqueueRetry(task, false, null);
            case UNKNOWN -> Mono.error(new ApiException(
                    HttpStatus.CONFLICT,
                    "generation-provider-task-unknown",
                    "无法确认供应商任务状态，不能安全重试。"));
            case PENDING, RUNNING -> Mono.error(new ApiException(
                    HttpStatus.CONFLICT,
                    "generation-provider-task-active",
                    "供应商仍有活动任务，不能重复提交。"));
        };
    }

    private Mono<CancelOutcome> cancelProvider(GenerationTask task) {
        if (!task.providerRequestSent()) {
            return Mono.just(CancelOutcome.NONE);
        }
        return routeFor(task.modelId(), task.workspaceId())
                .flatMap(route -> latestAttempt(task.id())
                        .flatMap(attempt -> route.gateway()
                                .cancel(reference(task, route.profile(), attempt.orElse(null)))
                                .subscribeOn(Schedulers.boundedElastic())
                                .timeout(GenerationTaskTimeouts.PROVIDER_CANCEL)
                                .thenReturn(CancelOutcome.NONE)))
                .onErrorResume(error -> {
                    var code = error instanceof ApiException api
                            ? api.getCode()
                            : "generation-provider-cancel-failed";
                    return Mono.just(new CancelOutcome(code, "供应商取消失败，任务已本地取消。"));
                });
    }

    MediaGenerationGateway.Reference reference(
            GenerationTask task,
            GenerationModelProfile profile,
            GenerationTask.Attempt attempt) {
        return new MediaGenerationGateway.Reference(
                attempt == null ? task.id().toString() : attempt.id().toString(),
                task.providerTaskId(),
                attempt == null ? null : attempt.providerRequestId(),
                attempt == null || attempt.channelId() == null
                        ? profile.channelId()
                        : attempt.channelId(),
                task.modelId(),
                profile.providerModel(),
                task.mediaType());
    }

    private Mono<Void> ensureParent(RequestIdentity identity, UUID parentTaskId) {
        if (parentTaskId == null) {
            return Mono.empty();
        }
        return store.findById(parentTaskId)
                .filter(parent -> Objects.equals(parent.ownerId(), identity.ownerId())
                        && Objects.equals(parent.workspaceId(), identity.workspaceId()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        "generation-parent-task-not-found",
                        "父生成任务不存在。")))
                .then();
    }

    private Mono<GenerationTaskResultView> resultView(GenerationTask task) {
        return archivedFiles(task).map(files -> {
            var archived = files.stream()
                    .map(this::archivedResult)
                    .toList();
            var registrationState = !task.resultAssetIds().isEmpty()
                    ? "registered"
                    : archived.isEmpty() ? "not-archived" : "pending-p4";
            var message = switch (registrationState) {
                case "registered" -> "生成结果已登记为逻辑资产。";
                case "pending-p4" -> "结果已归档为 StorageFile，逻辑资产登记由 P4 完成。";
                default -> "任务成功但没有可用的归档结果。";
            };
            return new GenerationTaskResultView(
                    task.id(),
                    task.status().getCode(),
                    task.resultAssetIds(),
                    archived,
                    registrationState,
                    message,
                    task.finishedAt());
        });
    }

    private Mono<List<StorageFile>> archivedFiles(
            GenerationTask task, int page, List<StorageFile> collected) {
        var query = new StorageFileQuery(
                null,
                task.ownerId(),
                "creation",
                StorageFileState.ACTIVE,
                null,
                page,
                RESULT_PAGE_SIZE);
        return fileRecords.list(query).flatMap(result -> {
            result.getItems().stream()
                    .filter(file -> task.id().toString().equals(attribute(file, "generationTaskId")))
                    .forEach(collected::add);
            if (page >= result.getTotalPages() || page >= RESULT_MAX_PAGES) {
                return Mono.just(List.copyOf(collected));
            }
            return archivedFiles(task, page + 1, collected);
        });
    }

    private GenerationTaskResultView.ArchivedResult archivedResult(StorageFile file) {
        var spec = file.getSpec();
        return new GenerationTaskResultView.ArchivedResult(
                file.getMetadata().getName(),
                spec.getOriginalName(),
                spec.getContentType(),
                spec.getSize(),
                spec.getAttributes() == null ? null : spec.getAttributes().get("mediaType"));
    }

    private static ApiException validateIdempotency(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "generation-idempotency-key-missing",
                    "缺少 Idempotency-Key 请求头。");
        }
        if (idempotencyKey.length() > 255) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "generation-idempotency-key-invalid",
                    "幂等键长度不能超过 255。");
        }
        return null;
    }

    private static ApiException validateStart(GenerationTaskStartRequest request) {
        if (request.modelId() == null) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST, "generation-model-required", "生成任务必须选择模型。");
        }
        if (!StringUtils.hasText(request.mediaType())) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST, "generation-media-type-required", "媒体类型不能为空。");
        }
        if (!StringUtils.hasText(request.operation())) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST, "generation-operation-required", "生成操作不能为空。");
        }
        try {
            GenerationTask.MediaType.fromCode(request.mediaType());
        } catch (IllegalArgumentException error) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST, "generation-media-type-invalid", "媒体类型不受支持。");
        }
        try {
            GenerationTask.Operation.fromCode(request.operation());
        } catch (IllegalArgumentException error) {
            return new ApiException(
                    HttpStatus.BAD_REQUEST, "generation-operation-invalid", "生成操作不受支持。");
        }
        return null;
    }

    private static ApiException requestRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "generation-task-request-required",
                "生成任务请求不能为空。");
    }

    private static Map<String, Object> settings(GenerationTaskStartRequest request) {
        return request.settings() == null ? Map.of() : request.settings();
    }

    private static List<String> inputAssetIds(GenerationTaskStartRequest request) {
        return request.inputAssetIds() == null ? List.of() : request.inputAssetIds();
    }

    private static String scopedIdempotencyKey(RequestIdentity identity, String idempotencyKey) {
        return Digests.sha256Hex(
                identity.ownerId() + '\0' + identity.workspaceId() + '\0' + idempotencyKey);
    }

    static GenerationTaskSummary summary(GenerationTask task) {
        return new GenerationTaskSummary(
                task.id(),
                task.mediaType().getCode(),
                task.operation().getCode(),
                task.modelId(),
                task.prompt(),
                task.status(),
                task.providerRequestSent(),
                task.resultAssetIds(),
                task.errorCode(),
                task.errorMessage(),
                task.createdAt(),
                task.startedAt(),
                task.finishedAt());
    }

    private static GenerationTaskStatusView view(
            GenerationTask task, List<GenerationTaskAttemptView> attempts) {
        return new GenerationTaskStatusView(
                summary(task),
                task.sessionId(),
                task.runId(),
                task.parentTaskId(),
                task.parentAssetId(),
                task.settings(),
                task.inputAssetIds(),
                attempts);
    }

    private static GenerationTaskAttemptView attemptView(GenerationTask.Attempt attempt) {
        return new GenerationTaskAttemptView(
                attempt.id(),
                attempt.attemptNo(),
                attempt.channelId(),
                attempt.modelId(),
                attempt.providerTaskId(),
                attempt.providerRequestId(),
                attempt.status(),
                attempt.errorCode(),
                attempt.errorMessage(),
                attempt.startedAt(),
                attempt.finishedAt());
    }

    private static Map<String, Object> cancelledData(GenerationTask task) {
        var data = new LinkedHashMap<String, Object>();
        data.put("taskId", task.id());
        data.put("status", task.status().getCode());
        if (task.errorCode() != null) {
            data.put("errorCode", task.errorCode());
        }
        return data;
    }

    private static Map<String, Object> failedData(GenerationTask task) {
        var data = new LinkedHashMap<String, Object>();
        data.put("taskId", task.id());
        data.put("status", task.status().getCode());
        data.put("errorCode", task.errorCode());
        return data;
    }

    private Instant now() {
        return clock.instant();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private record CancelOutcome(String errorCode, String errorMessage) {

        static final CancelOutcome NONE = new CancelOutcome(null, null);
    }

    private static ApiException catalogMissing() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "generation-model-catalog-not-configured",
                "生成模型目录尚未配置。");
    }

    private static ApiException modelMissing() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "generation-model-not-configured",
                "所选生成模型尚未配置或不可用。");
    }

    private static ApiException providerMissing() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "generation-provider-not-configured",
                "所选生成模型没有可用的供应商实现。");
    }

    private static ApiException providerStatusUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "generation-provider-status-unavailable",
                "暂时无法查询供应商任务状态。");
    }

    private static ApiException notCancellable() {
        return new ApiException(
                HttpStatus.CONFLICT, "generation-task-not-cancellable", "当前任务状态不能取消。");
    }

    private static ApiException taskNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "generation-task-not-found", "生成任务不存在。");
    }
}
