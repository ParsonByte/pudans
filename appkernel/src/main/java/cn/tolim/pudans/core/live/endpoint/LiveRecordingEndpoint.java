package cn.tolim.pudans.core.live.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.index.query.Queries.in;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.BatchDeleteItem;
import cn.tolim.pudans.contract.live.dto.BatchDeleteRequest;
import cn.tolim.pudans.contract.live.dto.LiveRecordingDownload;
import cn.tolim.pudans.contract.live.dto.LiveRecordingDownloadRequest;
import cn.tolim.pudans.contract.live.dto.LiveRecordingPage;
import cn.tolim.pudans.contract.live.dto.LiveRecordingQuery;
import cn.tolim.pudans.contract.live.dto.LiveRecordingRetry;
import cn.tolim.pudans.contract.live.dto.LiveRecordingView;
import cn.tolim.pudans.contract.live.dto.PartDownload;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.live.recording.RecordingQueries;
import cn.tolim.pudans.core.live.recording.Recordings;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRecordingEndpoint implements CustomEndpoint {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_DOWNLOAD_PARTS = 100;

    private final Recordings recordingService;

    private final RecordingQueries recordingQueries;

    private final ReactiveExtensionClient client;

    private final StorageTransfer storageTransfer;

    private final AuditLog auditLog;

    // 注册录制查询、停止、下载、重试与批量删除路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "LiveRecordingV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/liverecordings/query", this::query, builder -> builder.operationId("queryLiveRecordings")
                        .description("Query live recordings by source, state and time range.")
                        .tag(tag)
                        .response(responseBuilder().implementation(LiveRecordingPage.class)))
                .GET("/liverecordings/active", this::active, builder -> builder.operationId("listActiveLiveRecordings")
                        .description("List queued, recording and finalizing recordings.")
                        .tag(tag)
                        .response(responseBuilder().implementation(LiveRecordingView.class)))
                .POST("/liverecordings/{name}/stop", this::stop, builder -> builder.operationId("stopLiveRecording")
                        .description("Request a graceful stop for a live recording.")
                        .tag(tag)
                        .response(responseBuilder().implementation(LiveRecordingView.class)))
                .POST("/liverecordings/{name}/download-url", this::downloadUrl, builder -> builder.operationId("createLiveRecordingDownloadUrl")
                        .description("Create short-lived download URLs for uploaded recording parts.")
                        .tag(tag)
                        .response(responseBuilder().implementation(LiveRecordingDownload.class)))
                .POST("/liverecordings/{name}/retry", this::retry, builder -> builder.operationId("retryLiveRecording")
                        .description("Retry a failed recording upload or stream.")
                        .tag(tag)
                        .response(responseBuilder().implementation(LiveRecordingRetry.class)))
                .DELETE("/liverecordings/batch", this::batchDelete, builder -> builder.operationId("batchDeleteLiveRecordings")
                        .description("Delete multiple live recordings and their stored objects.")
                        .tag(tag)
                        .response(responseBuilder().implementation(BatchDeleteItem.class)))
                .build();
    }

    // 解析过滤条件并返回分页录制视图
    private Mono<ServerResponse> query(ServerRequest request) {
        return Mono.fromCallable(() -> parseQuery(request))
                .flatMap(recordingQueries::query)
                .map(result -> new LiveRecordingPage(
                        result.getItems().stream().map(LiveRecordingEndpoint::toView).toList(),
                        result.getTotal(),
                        result.getPage(),
                        result.getSize()))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    // 列出排队、录制中与收尾中的录制
    private Mono<ServerResponse> active(ServerRequest request) {
        var options = ListOptions.builder()
                .andQuery(in("status.state", List.of(
                        LiveRecordingState.QUEUED, LiveRecordingState.RECORDING, LiveRecordingState.FINALIZING)))
                .build();
        return client.listAll(LiveRecording.class, options, defaultSort())
                .map(LiveRecordingEndpoint::toView)
                .collectList()
                .flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    // 停止指定录制，成功后记录审计
    private Mono<ServerResponse> stop(ServerRequest request) {
        return recordingService
                .stop(request.pathVariable("name"), "api")
                .flatMap(recording -> recordStopAudit(request, recording).thenReturn(recording))
                .map(LiveRecordingEndpoint::toView)
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    // 按请求体选择续传或重新拉流重试
    private Mono<ServerResponse> retry(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(LiveRecordingRetry.class)
                .defaultIfEmpty(new LiveRecordingRetry(null, null))
                .flatMap(body -> recordingService.retry(name, body.mode(), "api"))
                .flatMap(result -> recordRetryAudit(request, result).thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 为已上传成品生成短期下载地址
    private Mono<ServerResponse> downloadUrl(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(LiveRecordingDownloadRequest.class)
                .defaultIfEmpty(new LiveRecordingDownloadRequest(null, null))
                .flatMap(body -> recordingService.find(name).flatMap(recording -> buildDownload(recording, body)))
                .flatMap(download -> ServerResponse.ok().bodyValue(download));
    }

    // 批量删除录制，逐条返回处理结果
    private Mono<ServerResponse> batchDelete(ServerRequest request) {
        return request.bodyToMono(BatchDeleteRequest.class)
                .defaultIfEmpty(new BatchDeleteRequest(List.of()))
                .flatMap(body -> {
                    var names = body.names();
                    if (names == null || names.isEmpty()) {
                        return Mono.error(LiveErrorCode.LIVE_INVALID_ARGUMENT.exception());
                    }
                    if (names.size() > MAX_PAGE_SIZE) {
                        return Mono.error(LiveErrorCode.LIVE_INVALID_ARGUMENT.exception());
                    }
                    return Flux.fromIterable(names)
                            .concatMap(this::deleteOne)
                            .collectList()
                            .flatMap(items -> recordBatchDeleteAudit(request, items).thenReturn(items))
                            .flatMap(items -> ServerResponse.ok().bodyValue(items));
                });
    }

    // 删除单条录制，活动状态与不存在都转为结果项
    private Mono<BatchDeleteItem> deleteOne(String name) {
        return client.fetch(LiveRecording.class, name)
                .flatMap(recording -> {
                    var state = recording.getStatus().getState();
                    if (state != null && state.isActive()) {
                        return Mono.just(new BatchDeleteItem(
                                name,
                                "REJECTED",
                                LiveErrorCode.LIVE_RECORDING_ACTIVE.name(),
                                LiveErrorCode.LIVE_RECORDING_ACTIVE.message()));
                    }
                    return client.delete(recording)
                            .thenReturn(new BatchDeleteItem(name, "ACCEPTED", null, null));
                })
                .switchIfEmpty(Mono.just(new BatchDeleteItem(
                        name,
                        "NOT_FOUND",
                        LiveErrorCode.LIVE_RECORDING_NOT_FOUND.name(),
                        LiveErrorCode.LIVE_RECORDING_NOT_FOUND.message())))
                .onErrorResume(error -> Mono.just(new BatchDeleteItem(
                        name, "REJECTED", "LIVE_DELETE_FAILED", "删除录制失败。")));
    }

    // 校验录制状态与成品后构建下载描述
    private Mono<LiveRecordingDownload> buildDownload(
            LiveRecording recording, LiveRecordingDownloadRequest request) {
        var state = recording.getStatus().getState();
        if (state != LiveRecordingState.COMPLETED
                && state != LiveRecordingState.FAILED
                && state != LiveRecordingState.FINALIZING) {
            return Mono.error(LiveErrorCode.LIVE_RECORDING_NOT_READY.exception());
        }
        var uploaded = recording.getStatus().getParts().stream()
                .filter(part -> part.getState() == PartState.UPLOADED
                        && StringUtils.hasText(part.getStorageFile()))
                .sorted((left, right) -> Integer.compare(left.getIndex(), right.getIndex()))
                .toList();
        if (uploaded.isEmpty()) {
            return Mono.error(LiveErrorCode.LIVE_RECORDING_NOT_READY.exception());
        }
        var partIndex = request.partIndex();
        if (partIndex != null) {
            uploaded = uploaded.stream()
                    .filter(part -> part.getIndex() == partIndex)
                    .toList();
            if (uploaded.isEmpty()) {
                return Mono.error(LiveErrorCode.LIVE_RECORDING_NOT_READY.exception());
            }
        } else if (uploaded.size() > MAX_DOWNLOAD_PARTS) {
            return Mono.error(LiveErrorCode.LIVE_INVALID_ARGUMENT.exception());
        }
        var ttl = normalizeTtl(request.ttlSeconds());
        var parts = Flux.fromIterable(uploaded)
                .concatMap(part -> toDownload(part, ttl))
                .collectList();
        return parts.map(items -> new LiveRecordingDownload(
                recording.getMetadata().getName(), items));
    }

    // 优先生成直传地址，失败再回退到代理下载
    private Mono<PartDownload> toDownload(LiveRecording.Part part, Duration ttl) {
        var options = new StorageSourceOptions(true, part.getFilename(), ttl);
        return storageTransfer
                .sourceUrl(part.getStorageFile(), options)
                .map(source -> toDirectDownload(part, ttl, source))
                .switchIfEmpty(Mono.defer(() -> storageTransfer
                        .downloadUrl(part.getStorageFile(), ttl)
                        .map(url -> new PartDownload(
                                part.getIndex(),
                                part.getFilename(),
                                url.url(),
                                url.expiresAt(),
                                true,
                                part.getSize()))));
    }

    // 直传地址缺少过期时间时按 TTL 补全
    private PartDownload toDirectDownload(LiveRecording.Part part, Duration ttl, StorageSource source) {
        var expiresAt = source.expiresAt() == null ? Instant.now().plus(ttl) : source.expiresAt();
        return new PartDownload(
                part.getIndex(),
                part.getFilename(),
                source.url().toString(),
                expiresAt,
                source.proxyRequired(),
                part.getSize());
    }

    // 下载有效期限制在 60 到 900 秒
    private Duration normalizeTtl(Integer ttlSeconds) {
        var seconds = ttlSeconds == null ? 300 : ttlSeconds;
        seconds = Math.max(60, Math.min(900, seconds));
        return Duration.ofSeconds(seconds);
    }

    // 解析分页、状态与时间范围并校验参数
    private LiveRecordingQuery parseQuery(ServerRequest request) {
        var page = parseInt(request.queryParam("page").orElse(null), 1);
        var size = parseInt(request.queryParam("size").orElse(null), DEFAULT_PAGE_SIZE);
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        page = Math.max(1, page);
        var states = new ArrayList<LiveRecordingState>();
        request.queryParams().getOrDefault("states", List.of()).forEach(value -> {
            if (StringUtils.hasText(value)) {
                for (var item : value.split(",")) {
                    if (StringUtils.hasText(item)) {
                        try {
                            states.add(LiveRecordingState.valueOf(item.trim()));
                        } catch (IllegalArgumentException error) {
                            throw LiveErrorCode.LIVE_INVALID_ARGUMENT.exception();
                        }
                    }
                }
            }
        });
        return new LiveRecordingQuery(
                request.queryParam("sourceName").orElse(null),
                states,
                parseInstant(request.queryParam("from").orElse(null)),
                parseInstant(request.queryParam("to").orElse(null)),
                page,
                size);
    }

    // 记录停止录制审计，失败不影响停止结果
    private Mono<Void> recordStopAudit(ServerRequest request, LiveRecording recording) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("state", recording.getStatus().getState());
        snapshot.put("stopRequestedAt", recording.getStatus().getStopRequestedAt());
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        "STOP_LIVE_RECORDING",
                        "LiveRecording",
                        recording.getMetadata().getName(),
                        "SUCCESS",
                        snapshot,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录停止录制审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    // 记录重试录制审计，失败不影响重试结果
    private Mono<Void> recordRetryAudit(ServerRequest request, LiveRecordingRetry result) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("recording", result.recording());
        snapshot.put("mode", result.mode());
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        "RETRY_LIVE_RECORDING",
                        "LiveRecording",
                        StringUtils.hasText(result.recording()) ? result.recording() : request.pathVariable("name"),
                        "SUCCESS",
                        snapshot,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录重试录制审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    // 为每个删除结果写入审计，失败不阻断删除
    private Mono<Void> recordBatchDeleteAudit(ServerRequest request, List<BatchDeleteItem> items) {
        return Flux.fromIterable(items)
                .concatMap(item -> {
                    var snapshot = new LinkedHashMap<String, Object>();
                    snapshot.put("result", item.result());
                    if (item.code() != null) {
                        snapshot.put("code", item.code());
                    }
                    return auditLog
                            .recordChild(
                                    request.exchange(),
                                    null,
                                    "DELETE_LIVE_RECORDING",
                                    "LiveRecording",
                                    item.name(),
                                    item.result(),
                                    snapshot,
                                    null)
                            .onErrorResume(error -> {
                                log.warn("记录删除录制审计失败：{}", error.getMessage());
                                return Mono.empty();
                            });
                })
                .then();
    }

    // 数字参数非法时统一抛参数错误
    private static int parseInt(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw LiveErrorCode.LIVE_INVALID_ARGUMENT.exception();
        }
    }

    // 时间参数按 ISO 格式解析，非法即拒绝
    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException error) {
            throw LiveErrorCode.LIVE_INVALID_ARGUMENT.exception();
        }
    }

    // 合并期望与状态生成对外录制视图
    private static LiveRecordingView toView(LiveRecording recording) {
        var spec = recording.getSpec();
        var status = recording.getStatus();
        var startedAt = status.getStartedAt() == null ? spec.getStartedAt() : status.getStartedAt();
        var format = spec.getFormat() == null ? null : spec.getFormat().name();
        return new LiveRecordingView(
                recording.getMetadata().getName(),
                spec.getSourceName(),
                spec.getPlatform(),
                spec.getRoomId(),
                spec.getRoomTitle(),
                spec.getAnchorName(),
                format,
                status.getState(),
                status.getErrorCode(),
                startedAt,
                status.getEndedAt(),
                status.getDurationMillis(),
                status.getTotalBytes(),
                status.getSegmentCount(),
                status.getPartCount(),
                status.getLocalBytes(),
                status.getLastWriteAt(),
                status.getLastPacketAt(),
                spec.getStoragePolicy(),
                spec.getRetryOf(),
                spec.isAuto());
    }
}
