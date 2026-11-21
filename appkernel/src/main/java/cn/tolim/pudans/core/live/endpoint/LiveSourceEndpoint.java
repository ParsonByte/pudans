package cn.tolim.pudans.core.live.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
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

    private Mono<ServerResponse> preview(ServerRequest request) {
        return request.bodyToMono(LiveSourceCreateRequest.class)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_INVALID_ARGUMENT.exception()))
                .flatMap(body -> sources.preview(body.roomUrl()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> resolve(ServerRequest request) {
        var name = request.pathVariable("name");
        return sources
                .resolve(name, true)
                .map(source -> sourceHealth.of(source))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> record(ServerRequest request) {
        var name = request.pathVariable("name");
        return sources
                .recordOnce(name)
                .flatMap(recording -> ServerResponse.ok().bodyValue(recording));
    }

    private Mono<ServerResponse> summary(ServerRequest request) {
        return sourceSummaries
                .summary()
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> health(ServerRequest request) {
        var name = request.pathVariable("name");
        return sourceHealth
                .health(name)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<Void> recordCreateAudit(ServerRequest request, LiveSource source) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("roomId", source.getSpec().getRoomId());
        snapshot.put("alias", source.getSpec().getAlias());
        snapshot.put("autoRecord", source.getSpec().isAutoRecord());
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        "CREATE_LIVE_SOURCE",
                        "LiveSource",
                        source.getMetadata().getName(),
                        "SUCCESS",
                        snapshot,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录直播源创建审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }
}
