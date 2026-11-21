package cn.tolim.pudans.core.live.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.extension.live.LiveRecording;
import cn.tolim.pudans.contract.core.extension.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveErrorCode;
import cn.tolim.pudans.contract.live.LiveSourceCreateRequest;
import cn.tolim.pudans.contract.live.LiveSourceHealth;
import cn.tolim.pudans.contract.live.LiveSourceSummary;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.live.source.LiveSourceHealthService;
import cn.tolim.pudans.core.live.source.LiveSourceService;
import cn.tolim.pudans.core.live.source.LiveSourceSummaryService;
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

    private final LiveSourceService sourceService;

    private final LiveSourceSummaryService summaryService;

    private final LiveSourceHealthService healthService;

    private final AuditService auditService;

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
                .flatMap(body -> sourceService
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
                .flatMap(body -> sourceService.preview(body.roomUrl()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> resolve(ServerRequest request) {
        var name = request.pathVariable("name");
        return sourceService
                .resolve(name, true)
                .map(source -> healthService.of(source))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> record(ServerRequest request) {
        var name = request.pathVariable("name");
        return sourceService
                .recordOnce(name)
                .flatMap(recording -> ServerResponse.ok().bodyValue(recording));
    }

    private Mono<ServerResponse> summary(ServerRequest request) {
        return summaryService
                .summary()
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> health(ServerRequest request) {
        var name = request.pathVariable("name");
        return healthService
                .health(name)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<Void> recordCreateAudit(ServerRequest request, LiveSource source) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("roomId", source.getSpec().getRoomId());
        snapshot.put("alias", source.getSpec().getAlias());
        snapshot.put("autoRecord", source.getSpec().isAutoRecord());
        return auditService
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
