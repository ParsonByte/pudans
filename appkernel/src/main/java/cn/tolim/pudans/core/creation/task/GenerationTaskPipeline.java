package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.core.creation.provider.MediaGenerationGateway;
import cn.tolim.pudans.core.storage.StorageRoutes;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationTaskPipeline {

    private static final Duration SUBMIT_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration STORAGE_TIMEOUT = Duration.ofMinutes(30);

    private static final Duration PROVIDER_POLL_INTERVAL = Duration.ofSeconds(2);

    private static final int PROVIDER_POLL_LIMIT = 1_800;

    private static final int PROVIDER_LOOKUP_LIMIT = 30;

    private static final int MAX_ERROR_CODE_LENGTH = 128;

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2_000;

    private static final Set<GenerationTask.Status> ACTIVE_STATUSES = Set.of(
            GenerationTask.Status.QUEUED,
            GenerationTask.Status.RETRY_QUEUED,
            GenerationTask.Status.DISPATCHING,
            GenerationTask.Status.PROVIDER_RUNNING,
            GenerationTask.Status.DOWNLOADING,
            GenerationTask.Status.ARCHIVING);

    private final GenerationTasks tasks;

    private final StorageRoutes storageRoutes;

    private final StorageTransfer storageTransfer;

    private final ObjectProvider<GenerationResultAssets> resultAssets;

    private final GenerationTaskEvents events;

    private final GenerationTaskAudit audit;

    private Clock clock = Clock.systemUTC();

    // 处理单条队列消息，业务状态始终以数据库为准。
    public Mono<GenerationTask> process(UUID taskId) {
        return tasks.find(taskId).flatMap(this::process);
    }

    // 尝试次数耗尽后终止任务并发送失败事件。
    public Mono<GenerationTask> exhaust(GenerationTask task) {
        if (task.terminal()) {
            return Mono.just(task);
        }
        return failTask(
                task,
                null,
                "generation-attempts-exhausted",
                "任务尝试次数已达上限。",
                task.providerRequestSent(),
                task.providerTaskId());
    }

    private Mono<GenerationTask> process(GenerationTask task) {
        if (task.terminal()) {
            return Mono.just(task);
        }
        return prepare(task).flatMap(context -> {
            if (context.task().terminal()) {
                return Mono.just(context.task());
            }
            return tasks.cancelled(context.task()).flatMap(cancelled -> cancelled
                    ? cancelTask(context.task(), context.attempt(), false)
                    : continueProcessing(context));
        });
    }

    private Mono<GenerationTask> continueProcessing(TaskContext context) {
        if (!context.task().providerRequestSent()) {
            if (context.task().status() != GenerationTask.Status.DISPATCHING) {
                return failTask(
                        context.task(),
                        context.attempt(),
                        "generation-task-state-invalid",
                        "生成任务缺少安全的供应商提交状态。",
                        false,
                        context.task().providerTaskId());
            }
            return submit(context);
        }
        return tasks.routeFor(context.task().modelId(), context.task().workspaceId())
                .flatMap(route -> reconcile(context, route))
                .onErrorResume(error -> failFromError(
                        context.task(),
                        context.attempt(),
                        error,
                        context.task().providerRequestSent(),
                        context.task().providerTaskId()));
    }

    private Mono<TaskContext> prepare(GenerationTask task) {
        if (task.terminal()) {
            return Mono.just(new TaskContext(task, null, Optional.empty()));
        }
        return switch (task.status()) {
            case QUEUED, RETRY_QUEUED -> newAttempt(task);
            case DISPATCHING -> tasks.latestAttempt(task.id()).flatMap(latest -> latest
                    .filter(attempt -> attempt.status().active())
                    .map(attempt -> Mono.just(new TaskContext(task, attempt, latest)))
                    .orElseGet(() -> newAttempt(task)));
            case PROVIDER_RUNNING, DOWNLOADING, ARCHIVING -> tasks.latestAttempt(task.id())
                    .flatMap(latest -> latest
                            .map(attempt -> Mono.just(new TaskContext(task, attempt, latest)))
                            .orElseGet(() -> failTask(
                                            task,
                                            null,
                                            "generation-attempt-missing",
                                            "生成任务缺少可恢复的尝试记录。",
                                            task.providerRequestSent(),
                                            task.providerTaskId())
                                    .map(failed -> new TaskContext(failed, null, Optional.empty()))));
            case SUCCEEDED, FAILED, CANCELLED -> Mono.just(task)
                    .map(current -> new TaskContext(current, null, Optional.empty()));
        };
    }

    private Mono<TaskContext> newAttempt(GenerationTask task) {
        return tasks.latestAttempt(task.id()).flatMap(previous -> tasks
                .nextAttemptNumber(task.id())
                .flatMap(attemptNo -> {
                    var attempt = new GenerationTask.Attempt(
                            UUID.randomUUID(),
                            task.id(),
                            attemptNo,
                            null,
                            task.modelId(),
                            null,
                            null,
                            GenerationTask.AttemptStatus.REQUESTING,
                            null,
                            null,
                            now(),
                            null);
                    return tasks
                            .beginProcessing(task, attempt)
                            .map(processing -> new TaskContext(
                                    processing.task(), processing.attempt(), previous))
                            .switchIfEmpty(Mono.defer(() -> tasks
                                    .find(task.id())
                                    .flatMap(current -> current.terminal()
                                            ? Mono.just(new TaskContext(current, null, previous))
                                            : Mono.error(new IllegalStateException(
                                                    "generation task changed while starting an attempt")))));
                }));
    }

    private Mono<GenerationTask> submit(TaskContext context) {
        return tasks
                .routeFor(context.task().modelId(), context.task().workspaceId())
                .map(route -> new RouteResolution(route, null))
                .onErrorResume(error -> failFromError(
                                context.task(),
                                context.attempt(),
                                error,
                                false,
                                context.task().providerTaskId())
                        .map(failed -> new RouteResolution(null, failed)))
                .flatMap(resolution -> resolution.route() == null
                        ? Mono.just(resolution.failure())
                        : submitWithRoute(context, resolution.route()));
    }

    private Mono<GenerationTask> submitWithRoute(
            TaskContext context, GenerationTasks.Route route) {
        return tasks
                .markProviderRequestStarted(context.task().id())
                .switchIfEmpty(Mono.defer(() -> tasks
                        .find(context.task().id())
                        .flatMap(current -> current.terminal()
                                ? Mono.just(current)
                                : Mono.error(new IllegalStateException(
                                        "generation task changed before provider submission")))))
                .flatMap(started -> {
                    if (started.terminal()) {
                        return Mono.just(started);
                    }
                    var request = request(started, context.attempt(), route);
                    return route
                            .gateway()
                            .submit(request)
                            .subscribeOn(Schedulers.boundedElastic())
                            .timeout(SUBMIT_TIMEOUT)
                            .map(submission -> new SubmissionResult(submission, null))
                            .onErrorResume(error -> submissionFailure(started, context.attempt(), error)
                                    .map(failed -> new SubmissionResult(null, failed)))
                            .flatMap(result -> result.failure() != null
                                    ? Mono.just(result.failure())
                                    : accept(
                                            started,
                                            context.attempt(),
                                            route,
                                            result.submission()));
                });
    }

    private MediaGenerationGateway.Request request(
            GenerationTask task, GenerationTask.Attempt attempt, GenerationTasks.Route route) {
        var profile = route.profile();
        return new MediaGenerationGateway.Request(
                attempt.id().toString(),
                task.id(),
                attempt.id(),
                profile.channelId(),
                task.modelId(),
                profile.providerModel(),
                task.mediaType(),
                task.operation(),
                task.prompt(),
                task.settings(),
                task.inputAssetIds(),
                task.parentAssetId());
    }

    private Mono<GenerationTask> accept(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            GenerationTasks.Route route,
            MediaGenerationGateway.Submission submission) {
        if (submission == null) {
            return failTask(
                    task,
                    attempt,
                    "generation-provider-submission-invalid",
                    "供应商没有返回有效的提交结果。",
                    true,
                    null);
        }
        var providerTaskId = providerTaskId(submission);
        var providerRequestId = submission.providerRequestId() == null
                ? stateValue(submission, MediaGenerationGateway.State::providerRequestId)
                : submission.providerRequestId();
        var channelId = submission.channelId() == null ? route.profile().channelId() : submission.channelId();
        return tasks
                .markAttemptSubmitted(attempt.id(), providerTaskId, providerRequestId, channelId)
                .then(tasks.cancelled(task))
                .flatMap(cancelled -> cancelled
                        ? cancelTask(
                                withProviderReference(task, providerTaskId),
                                attempt,
                                true)
                        : applySubmissionState(
                                withProviderReference(task, providerTaskId),
                                attempt,
                                route,
                                submission.state()));
    }

    private Mono<GenerationTask> applySubmissionState(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            GenerationTasks.Route route,
            MediaGenerationGateway.State state) {
        var providerTaskId = state == null ? task.providerTaskId() : state.providerTaskId();
        return tasks
                .confirmProviderRunning(task.id(), providerTaskId)
                .switchIfEmpty(Mono.defer(() -> tasks.find(task.id())))
                .flatMap(running -> running.terminal()
                        ? Mono.just(running)
                        : applyProviderState(
                                running, attempt, route, state, PROVIDER_POLL_LIMIT, false));
    }

    private Mono<GenerationTask> reconcile(TaskContext context, GenerationTasks.Route route) {
        var referenceAttempt = context.referenceAttempt().orElse(context.attempt());
        var reference = tasks.reference(
                context.task(), route.profile(), referenceAttempt);
        return tasks.cancelled(context.task()).flatMap(cancelled -> cancelled
                ? cancelTask(context.task(), context.attempt(), true)
                : lookup(route, reference, PROVIDER_LOOKUP_LIMIT)
                        .flatMap(state -> applyProviderState(
                                context.task(),
                                context.attempt(),
                                route,
                                state,
                                PROVIDER_POLL_LIMIT,
                                false))
                        .onErrorResume(error -> failFromError(
                                context.task(),
                                context.attempt(),
                                error,
                                true,
                                context.task().providerTaskId())));
    }

    private Mono<MediaGenerationGateway.State> lookup(
            GenerationTasks.Route route,
            MediaGenerationGateway.Reference reference,
            int remainingAttempts) {
        return route
                .gateway()
                .find(reference)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(GenerationTaskTimeouts.PROVIDER_STATUS)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "generation-provider-status-unavailable",
                        "供应商没有返回任务状态。")))
                .onErrorResume(error -> remainingAttempts <= 0
                        ? Mono.error(error)
                        : Mono.delay(PROVIDER_POLL_INTERVAL).then(
                                lookup(route, reference, remainingAttempts - 1)));
    }

    private Mono<GenerationTask> applyProviderState(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            GenerationTasks.Route route,
            MediaGenerationGateway.State state,
            int remainingPolls,
            boolean progressSent) {
        if (state == null || state.phase() == null) {
            return failTask(
                    task,
                    attempt,
                    "generation-provider-status-invalid",
                    "供应商返回了无法识别的任务状态。",
                    true,
                    task.providerTaskId());
        }
        var providerTaskId = StringUtils.hasText(state.providerTaskId())
                ? state.providerTaskId()
                : task.providerTaskId();
        return switch (state.phase()) {
            case SUCCEEDED -> ensureProviderRunning(task, attempt, providerTaskId)
                    .flatMap(running -> running.terminal()
                            ? Mono.just(running)
                            : archive(running, attempt, state));
            case FAILED -> failTask(
                    withProviderReference(task, providerTaskId),
                    attempt,
                    state.errorCode() == null ? "generation-provider-failed" : state.errorCode(),
                    state.errorMessage() == null ? "供应商任务执行失败。" : state.errorMessage(),
                    true,
                    providerTaskId);
            case CANCELLED -> cancelTask(
                    withProviderReference(task, providerTaskId), attempt, false);
            case UNKNOWN -> failTask(
                    withProviderReference(task, providerTaskId),
                    attempt,
                    "generation-provider-task-unknown",
                    "无法确认供应商任务状态，不能安全继续。",
                    true,
                    providerTaskId);
            case PENDING, RUNNING -> ensureProviderRunning(task, attempt, providerTaskId)
                    .flatMap(running -> running.terminal()
                            ? Mono.just(running)
                            : pollProvider(
                                    running,
                                    attempt,
                                    route,
                                    providerTaskId,
                                    remainingPolls,
                                    progressSent));
        };
    }

    private Mono<GenerationTask> pollProvider(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            GenerationTasks.Route route,
            String providerTaskId,
            int remainingPolls,
            boolean progressSent) {
        if (remainingPolls <= 0) {
            return failTask(
                    task,
                    attempt,
                    "generation-provider-timeout",
                    "等待供应商生成结果超时。",
                    true,
                    providerTaskId);
        }
        var currentTask = withProviderReference(task, providerTaskId);
        var progress = progressSent
                ? Mono.empty()
                : tasks.markAttemptRunning(attempt.id())
                        .then(events.publish(
                                currentTask,
                                GenerationTaskEvents.TASK_PROGRESS,
                                progressData(currentTask, attempt)))
                        .then();
        return progress
                .then(tasks.cancelled(currentTask))
                .flatMap(cancelled -> cancelled
                        ? cancelTask(currentTask, attempt, true)
                        : Mono.delay(PROVIDER_POLL_INTERVAL)
                                .then(Mono.defer(() -> tasks.find(task.id())))
                                .flatMap(latest -> latest.terminal()
                                        ? Mono.just(latest)
                                        : lookup(
                                                        route,
                                                        tasks.reference(
                                                                latest, route.profile(), attempt),
                                                        PROVIDER_LOOKUP_LIMIT)
                                                .flatMap(state -> applyProviderState(
                                                        latest,
                                                        attempt,
                                                        route,
                                                        state,
                                                        remainingPolls - 1,
                                                        true))
                                                .onErrorResume(error -> failFromError(
                                                        latest,
                                                        attempt,
                                                        error,
                                                        true,
                                                        latest.providerTaskId()))));
    }

    private Mono<GenerationTask> ensureProviderRunning(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            String providerTaskId) {
        var running = task.status() == GenerationTask.Status.DISPATCHING
                ? tasks.confirmProviderRunning(task.id(), providerTaskId)
                        .switchIfEmpty(Mono.defer(() -> tasks.find(task.id())))
                : Mono.just(withProviderReference(task, providerTaskId));
        return running.flatMap(current -> {
            if (current.terminal()) {
                return Mono.just(current);
            }
            if (current.status() == GenerationTask.Status.DOWNLOADING
                    || current.status() == GenerationTask.Status.ARCHIVING
                    || attempt.status().active()) {
                return Mono.just(current);
            }
            return tasks.markAttemptRunning(attempt.id()).thenReturn(current);
        });
    }

    private Mono<GenerationTask> archive(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            MediaGenerationGateway.State state) {
        var result = state.result();
        var providerAssets = result == null ? List.<MediaGenerationGateway.Asset>of() : result.assets();
        if (providerAssets.isEmpty()) {
            return failTask(
                    task,
                    attempt,
                    "generation-provider-result-missing",
                    "供应商任务成功但没有返回可归档的结果。",
                    true,
                    state.providerTaskId());
        }
        for (var asset : providerAssets) {
            if (asset == null
                    || !StringUtils.hasText(asset.providerAssetId())
                    || asset.body() == null) {
                return failTask(
                        task,
                        attempt,
                        "generation-provider-result-invalid",
                        "供应商结果缺少稳定标识或内容。",
                        true,
                        state.providerTaskId());
            }
        }
        return tasks
                .archivedFiles(task)
                .flatMap(existing -> storageRoutes
                        .resolve(StorageRoute.Purpose.CREATION_RESULT)
                        .flatMap(policy -> enterDownloading(task)
                                .flatMap(downloading -> enterArchiving(downloading)
                                        .flatMap(archiving -> archiveAssets(
                                                        archiving, policy, providerAssets, existing)
                                                .flatMap(files -> register(archiving, files)
                                                        .flatMap(assetIds -> tasks
                                                                .find(archiving.id())
                                                                .defaultIfEmpty(archiving)
                                                                .flatMap(current -> succeed(
                                                                        current,
                                                                        attempt,
                                                                        files,
                                                                        assetIds))))))))
                .onErrorResume(error -> failFromError(
                        task, attempt, error, true, state.providerTaskId()));
    }

    private Mono<GenerationTask> enterDownloading(GenerationTask task) {
        if (task.status() == GenerationTask.Status.DOWNLOADING
                || task.status() == GenerationTask.Status.ARCHIVING) {
            return Mono.just(task);
        }
        if (task.status() != GenerationTask.Status.PROVIDER_RUNNING) {
            return Mono.error(new IllegalStateException(
                    "generation task is not ready to enter downloading"));
        }
        return requireUpdate(
                tasks.transition(
                        task.id(),
                        Set.of(GenerationTask.Status.PROVIDER_RUNNING),
                        GenerationTask.Status.DOWNLOADING),
                task);
    }

    private Mono<GenerationTask> enterArchiving(GenerationTask task) {
        if (task.status() == GenerationTask.Status.ARCHIVING) {
            return Mono.just(task);
        }
        if (task.status() != GenerationTask.Status.DOWNLOADING) {
            return Mono.error(new IllegalStateException(
                    "generation task is not ready to archive results"));
        }
        return requireUpdate(
                tasks.transition(
                        task.id(),
                        Set.of(GenerationTask.Status.DOWNLOADING),
                        GenerationTask.Status.ARCHIVING),
                task);
    }

    private Mono<List<StorageFile>> archiveAssets(
            GenerationTask task,
            StoragePolicy policy,
            List<MediaGenerationGateway.Asset> assets,
            List<StorageFile> existing) {
        var indexed = IntStream.range(0, assets.size())
                .mapToObj(index -> new IndexedAsset(index, assets.get(index)))
                .toList();
        return Flux.fromIterable(indexed)
                .concatMap(asset -> archiveAsset(task, policy, asset, existing))
                .collectList();
    }

    private Mono<StorageFile> archiveAsset(
            GenerationTask task,
            StoragePolicy policy,
            IndexedAsset indexed,
            List<StorageFile> existing) {
        var asset = indexed.asset();
        var archived = existing.stream()
                .filter(file -> asset.providerAssetId().equals(
                        GenerationTasks.attribute(file, "providerAssetId")))
                .findFirst();
        if (archived.isPresent()) {
            return Mono.just(archived.get());
        }
        var contentType = normalizedContentType(asset.contentType(), task.mediaType());
        var originalName = originalName(task, asset, indexed.index(), contentType);
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("generationTaskId", task.id().toString());
        attributes.put("mediaType", task.mediaType().getCode());
        attributes.put("providerAssetId", asset.providerAssetId());
        if (StringUtils.hasText(task.providerTaskId())) {
            attributes.put("providerTaskId", task.providerTaskId());
        }
        if (task.runId() != null) {
            attributes.put("runId", task.runId().toString());
        }
        var request = new StorageWriteRequest(
                policyName(policy),
                task.ownerId(),
                "creation",
                originPath(task, asset, indexed.index()),
                originalName,
                contentType,
                Math.max(0, asset.size()),
                Map.copyOf(attributes),
                asset.body(),
                false);
        return storageTransfer
                .upload(request)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(STORAGE_TIMEOUT);
    }

    private Mono<List<String>> register(GenerationTask task, List<StorageFile> files) {
        var registrar = resultAssets.getIfAvailable();
        if (registrar == null) {
            return Mono.just(List.of());
        }
        var identity = new RequestIdentity(task.ownerId(), task.workspaceId());
        return registrar
                .register(identity, task, files)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "generation-result-assets-not-registered",
                        "生成结果未能登记为逻辑资产。")))
                .flatMap(ids -> ids == null || ids.isEmpty()
                        ? Mono.error(new ApiException(
                                HttpStatus.BAD_GATEWAY,
                                "generation-result-assets-not-registered",
                                "生成结果未能登记为逻辑资产。"))
                        : Mono.just(List.copyOf(ids)));
    }

    private Mono<GenerationTask> succeed(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            List<StorageFile> files,
            List<String> resultAssetIds) {
        return tasks
                .succeed(task.id(), attempt.id(), resultAssetIds)
                .switchIfEmpty(Mono.defer(() -> tasks.find(task.id())))
                .flatMap(completed -> {
                    if (completed.status() != GenerationTask.Status.SUCCEEDED) {
                        return Mono.just(completed);
                    }
                    var data = new LinkedHashMap<String, Object>();
                    data.put("taskId", completed.id());
                    data.put("status", completed.status().getCode());
                    data.put("resultAssetIds", completed.resultAssetIds());
                    data.put("storageFileCount", files.size());
                    data.put("registrationState", resultAssetIds.isEmpty() ? "pending-p4" : "registered");
                    audit.completed(completed, files.size(), !resultAssetIds.isEmpty());
                    return events
                            .publish(completed, GenerationTaskEvents.TASK_RESULT, data)
                            .thenReturn(completed);
                });
    }

    private Mono<GenerationTask> cancelTask(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            boolean callProvider) {
        if (task.status() == GenerationTask.Status.CANCELLED) {
            return Mono.just(task);
        }
        var providerCancel = providerCancel(task, attempt, callProvider);
        return providerCancel
                .flatMap(errorCode -> (attempt != null && attempt.status().active()
                                ? tasks.markAttemptCancelled(attempt.id()).then()
                                : Mono.<Void>empty())
                        .then(tasks.cancelByProvider(
                                task.id(),
                                errorCode.orElse(null),
                                errorCode.isPresent() ? "供应商取消失败，任务已本地取消。" : null)))
                .switchIfEmpty(Mono.defer(() -> tasks.find(task.id())))
                .flatMap(cancelled -> {
                    if (cancelled.status() != GenerationTask.Status.CANCELLED
                            || task.status() == GenerationTask.Status.CANCELLED) {
                        return Mono.just(cancelled);
                    }
                    return events
                            .publish(
                                    cancelled,
                                    GenerationTaskEvents.TASK_CANCELLED,
                                    cancelledData(cancelled))
                            .thenReturn(cancelled);
                });
    }

    private Mono<Optional<String>> providerCancel(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            boolean callProvider) {
        if (!callProvider || !task.providerRequestSent()) {
            return Mono.just(Optional.empty());
        }
        return tasks.routeFor(task.modelId(), task.workspaceId())
                .flatMap(route -> route
                        .gateway()
                        .cancel(tasks.reference(task, route.profile(), attempt))
                        .subscribeOn(Schedulers.boundedElastic())
                        .timeout(GenerationTaskTimeouts.PROVIDER_CANCEL))
                .thenReturn(Optional.<String>empty())
                .onErrorResume(error -> {
                    log.warn("生成任务供应商取消失败：task={}", task.id(), error);
                    return Mono.just(Optional.of("generation-provider-cancel-failed"));
                });
    }

    private Mono<GenerationTask> submissionFailure(
            GenerationTask task, GenerationTask.Attempt attempt, Throwable error) {
        if (error instanceof MediaGenerationGateway.Failure failure) {
            return failTask(
                    task,
                    attempt,
                    failure.code(),
                    errorMessage(error),
                    failure.requestSent(),
                    task.providerTaskId());
        }
        return failFromError(task, attempt, error, true, task.providerTaskId());
    }

    private Mono<GenerationTask> failFromError(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            Throwable error,
            boolean providerRequestSent,
            String providerTaskId) {
        if (error instanceof ApiException api) {
            return failTask(
                    task,
                    attempt,
                    api.getCode(),
                    api.getReason(),
                    providerRequestSent,
                    providerTaskId);
        }
        if (error instanceof MediaGenerationGateway.Failure failure) {
            return failTask(
                    task,
                    attempt,
                    failure.code(),
                    errorMessage(error),
                    failure.requestSent(),
                    providerTaskId);
        }
        return failTask(
                task,
                attempt,
                "generation-task-failed",
                errorMessage(error),
                providerRequestSent,
                providerTaskId);
    }

    private Mono<GenerationTask> failTask(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            String errorCode,
            String errorMessage,
            boolean providerRequestSent,
            String providerTaskId) {
        if (task.terminal()) {
            return Mono.just(task);
        }
        var code = errorCode(errorCode);
        var message = errorMessage(errorMessage);
        return tasks.fail(
                        task.id(),
                        attempt == null ? null : attempt.id(),
                        ACTIVE_STATUSES,
                        code,
                        message,
                        providerRequestSent,
                        providerTaskId)
                .flatMap(failed -> {
                    if (failed.status() != GenerationTask.Status.FAILED) {
                        return Mono.just(failed);
                    }
                    audit.providerFailure(failed, code);
                    return events
                            .publish(
                                    failed,
                                    GenerationTaskEvents.TASK_FAILED,
                                    failedData(failed))
                            .thenReturn(failed);
                })
                .switchIfEmpty(Mono.defer(() -> tasks.find(task.id())))
                .defaultIfEmpty(task);
    }

    private Mono<GenerationTask> requireUpdate(
            Mono<GenerationTask> update, GenerationTask fallback) {
        return update
                .switchIfEmpty(Mono.defer(() -> tasks.find(fallback.id())))
                .flatMap(current -> current.terminal()
                        ? Mono.just(current)
                        : current.status() == fallback.status()
                                ? Mono.error(new IllegalStateException(
                                        "generation task status update was rejected"))
                                : Mono.just(current))
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "generation task disappeared during status update")));
    }

    private static GenerationTask withProviderReference(
            GenerationTask task, String providerTaskId) {
        if (Objects.equals(task.providerTaskId(), providerTaskId)) {
            return task;
        }
        return new GenerationTask(
                task.id(),
                task.ownerId(),
                task.workspaceId(),
                task.sessionId(),
                task.runId(),
                task.parentTaskId(),
                task.parentAssetId(),
                task.mediaType(),
                task.operation(),
                task.modelId(),
                task.prompt(),
                task.settings(),
                task.inputAssetIds(),
                task.status(),
                task.providerRequestSent(),
                providerTaskId,
                task.resultAssetIds(),
                task.errorCode(),
                task.errorMessage(),
                task.quotaReservationId(),
                task.idempotencyKey(),
                task.createdAt(),
                task.startedAt(),
                task.finishedAt());
    }

    private static String providerTaskId(MediaGenerationGateway.Submission submission) {
        if (StringUtils.hasText(submission.providerTaskId())) {
            return submission.providerTaskId();
        }
        return stateValue(submission, MediaGenerationGateway.State::providerTaskId);
    }

    private static String stateValue(
            MediaGenerationGateway.Submission submission,
            java.util.function.Function<MediaGenerationGateway.State, String> getter) {
        return submission.state() == null ? null : getter.apply(submission.state());
    }

    private static Map<String, Object> progressData(
            GenerationTask task, GenerationTask.Attempt attempt) {
        var data = new LinkedHashMap<String, Object>();
        data.put("taskId", task.id());
        data.put("status", task.status().getCode());
        data.put("attemptNo", attempt.attemptNo());
        data.put("providerTaskId", task.providerTaskId());
        return data;
    }

    private static Map<String, Object> failedData(GenerationTask task) {
        var data = new LinkedHashMap<String, Object>();
        data.put("taskId", task.id());
        data.put("status", task.status().getCode());
        data.put("errorCode", task.errorCode());
        data.put("errorMessage", task.errorMessage());
        return data;
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

    private static String policyName(StoragePolicy policy) {
        if (policy == null
                || policy.getMetadata() == null
                || !StringUtils.hasText(policy.getMetadata().getName())) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "generation-result-storage-policy-invalid",
                    "生成结果存储策略无效。");
        }
        return policy.getMetadata().getName();
    }

    private static String normalizedContentType(
            String requested, GenerationTask.MediaType mediaType) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        return mediaType == GenerationTask.MediaType.VIDEO ? "video/mp4" : "image/png";
    }

    private static String originalName(
            GenerationTask task,
            MediaGenerationGateway.Asset asset,
            int index,
            String contentType) {
        var original = safeFileName(asset.originalName());
        var fallback = task.mediaType().getCode() + "-" + index + "-"
                + safeSegment(asset.providerAssetId(), 48);
        var name = StringUtils.hasText(original) ? original : fallback;
        if (!name.contains(".")) {
            name = name + "." + extension(contentType);
        }
        return name;
    }

    private static String safeFileName(String value) {
        var name = StringUtils.getFilename(value);
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.replaceAll("[\\p{Cntrl}/\\\\]+", "_");
    }

    private static String originPath(
            GenerationTask task, MediaGenerationGateway.Asset asset, int index) {
        return "generation/" + task.id() + "/" + index + "-"
                + safeSegment(asset.providerAssetId(), 96);
    }

    private static String safeSegment(String value, int maxLength) {
        var normalized = value == null
                ? "asset"
                : value.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (!StringUtils.hasText(normalized)) {
            normalized = "asset";
        }
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static String extension(String contentType) {
        var type = contentType == null ? "" : contentType.toLowerCase();
        var separator = type.indexOf('/');
        var extension = separator < 0 ? type : type.substring(separator + 1);
        var parameter = extension.indexOf(';');
        if (parameter >= 0) {
            extension = extension.substring(0, parameter);
        }
        extension = extension.replaceAll("[^a-z0-9]+", "");
        if ("jpeg".equals(extension)) {
            return "jpg";
        }
        return StringUtils.hasText(extension) ? extension : "bin";
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof ApiException api && StringUtils.hasText(api.getReason())) {
            return api.getReason();
        }
        return StringUtils.hasText(error.getMessage())
                ? error.getMessage()
                : "生成任务执行失败。";
    }

    private static String errorCode(String code) {
        var normalized = StringUtils.hasText(code) ? code.trim() : "generation-task-failed";
        return normalized.length() <= MAX_ERROR_CODE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_CODE_LENGTH);
    }

    private static String errorMessage(String message) {
        var normalized = StringUtils.hasText(message) ? message.trim() : "生成任务执行失败。";
        return normalized.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private Instant now() {
        return clock.instant();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private record TaskContext(
            GenerationTask task,
            GenerationTask.Attempt attempt,
            Optional<GenerationTask.Attempt> referenceAttempt) {}

    private record IndexedAsset(int index, MediaGenerationGateway.Asset asset) {}

    private record RouteResolution(GenerationTasks.Route route, GenerationTask failure) {}

    private record SubmissionResult(
            MediaGenerationGateway.Submission submission, GenerationTask failure) {}
}
