package cn.tolim.pudans.core.live.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveSourceCreateRequest;
import cn.tolim.pudans.contract.live.dto.LiveSourceHealth;
import cn.tolim.pudans.contract.live.dto.LiveSourceSummary;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.live.source.SourceHealth;
import cn.tolim.pudans.core.live.source.Sources;
import cn.tolim.pudans.core.live.source.SourceSummaries;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

// 直播源自定义端点：从链接创建、解析预览与手动解析、立即录制、汇总与健康
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSourceEndpoint implements CustomEndpoint {

    private static final String TAG = "LiveSourceV1alpha1";

    private final Sources sources;

    private final SourceSummaries sourceSummaries;

    private final SourceHealth sourceHealth;

    private final AuditLog auditLog;

    // 注册创建、解析、录制、删除、汇总与健康路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST(
                        "/livesources/from-url",
                        this::fromUrl,
                        builder -> builder.operationId("createLiveSourceFromUrl")
                                .description("从直播间链接创建直播源")
                                .tag(TAG)
                                .response(responseBuilder().implementation(LiveSource.class)))
                .POST(
                        "/livesources/resolve",
                        this::preview,
                        builder -> builder.operationId("resolveLiveRoom")
                                .description("解析直播间链接并返回预览结果")
                                .tag(TAG)
                                .response(responseBuilder().implementation(LiveSourceHealth.class)))
                .POST(
                        "/livesources/{name}/resolve",
                        this::resolve,
                        builder -> builder.operationId("resolveLiveSource")
                                .description("解析指定直播源并写回状态")
                                .tag(TAG)
                                .response(responseBuilder().implementation(LiveSourceHealth.class)))
                .POST(
                        "/livesources/{name}/record",
                        this::record,
                        builder -> builder.operationId("recordLiveSource")
                                .description("立即录制指定直播源")
                                .tag(TAG)
                                .response(responseBuilder().implementation(LiveRecording.class)))
                .DELETE(
                        "/livesources/{name}",
                        this::delete,
                        builder -> builder.operationId("deleteLiveSource")
                                .description("删除指定直播源并记录审计")
                                .tag(TAG)
                                .response(responseBuilder().responseCode("204").description("直播源已删除")))
                .GET(
                        "/livesources/summary",
                        this::summary,
                        builder -> builder.operationId("getLiveSourceSummary")
                                .description("获取直播源状态汇总")
                                .tag(TAG)
                                .response(responseBuilder().implementation(LiveSourceSummary.class)))
                .GET(
                        "/livesources/{name}/health",
                        this::health,
                        builder -> builder.operationId("getLiveSourceHealth")
                                .description("获取指定直播源健康快照")
                                .tag(TAG)
                                .response(responseBuilder().implementation(LiveSourceHealth.class)))
                .build();
    }

    // 从直播间链接创建直播源并记录审计
    private Mono<ServerResponse> fromUrl(ServerRequest request) {
        return request.bodyToMono(LiveSourceCreateRequest.class)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_INVALID_ARGUMENT.exception()))
                .flatMap(body -> sources
                        .create(
                                body.roomUrl(),
                                body.alias(),
                                body.autoRecord(),
                                body.segmentSeconds(),
                                body.mergeIntervalSeconds(),
                                body.storagePolicy())
                        .flatMap(source -> recordCreateAudit(request, source).thenReturn(source)))
                .flatMap(source -> ServerResponse.status(HttpStatus.CREATED).bodyValue(source));
    }

    // 解析链接返回预览结果，不落库
    private Mono<ServerResponse> preview(ServerRequest request) {
        return request.bodyToMono(LiveSourceCreateRequest.class)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_INVALID_ARGUMENT.exception()))
                .flatMap(body -> sources.preview(body.roomUrl()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 解析指定直播源并写回最新状态
    private Mono<ServerResponse> resolve(ServerRequest request) {
        var name = request.pathVariable("name");
        return sources
                .resolve(name, true)
                .map(source -> sourceHealth.of(source))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 对指定直播源立即发起一次录制
    private Mono<ServerResponse> record(ServerRequest request) {
        var name = request.pathVariable("name");
        return sources
                .recordOnce(name)
                .flatMap(recording -> ServerResponse.ok().bodyValue(recording));
    }

    // 删除指定直播源并记录审计
    private Mono<ServerResponse> delete(ServerRequest request) {
        var name = request.pathVariable("name");
        return sources
                .delete(name)
                .flatMap(source -> recordDeleteAudit(request, source).thenReturn(source))
                .then(ServerResponse.noContent().build());
    }

    // 返回直播源状态与内存计数汇总
    private Mono<ServerResponse> summary(ServerRequest request) {
        return sourceSummaries
                .summary()
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 返回指定直播源的健康快照
    private Mono<ServerResponse> health(ServerRequest request) {
        var name = request.pathVariable("name");
        return sourceHealth
                .health(name)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 记录直播源创建审计，失败不影响创建结果
    private Mono<Void> recordCreateAudit(ServerRequest request, LiveSource source) {
        return recordAudit(request, "CREATE_LIVE_SOURCE", source, sourceSnapshot(source));
    }

    // 记录直播源删除审计，失败不影响删除结果
    private Mono<Void> recordDeleteAudit(ServerRequest request, LiveSource source) {
        var snapshot = sourceSnapshot(source);
        var status = source.getStatus();
        snapshot.put("state", status == null || status.getState() == null ? null : status.getState().name());
        return recordAudit(request, "DELETE_LIVE_SOURCE", source, snapshot);
    }

    // 提交一条直播源子步骤审计，失败仅告警
    private Mono<Void> recordAudit(
            ServerRequest request, String operation, LiveSource source, Map<String, Object> snapshot) {
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        operation,
                        "LiveSource",
                        source.getMetadata().getName(),
                        "SUCCESS",
                        snapshot,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录直播源审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    // 组装创建与删除共用的直播源快照
    private static Map<String, Object> sourceSnapshot(LiveSource source) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("roomId", source.getSpec().getRoomId());
        snapshot.put("alias", source.getSpec().getAlias());
        snapshot.put("autoRecord", source.getSpec().isAutoRecord());
        return snapshot;
    }
}
