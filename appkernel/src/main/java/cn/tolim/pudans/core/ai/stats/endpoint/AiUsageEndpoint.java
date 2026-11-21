package cn.tolim.pudans.core.ai.stats.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.stats.AiUsageErrorCodes;
import cn.tolim.pudans.core.ai.stats.AiUsage;
import cn.tolim.pudans.core.ai.stats.AiUsage.ClearQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.ClearResult;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextGrid;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextUsage;
import cn.tolim.pudans.core.ai.stats.AiUsage.GridQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.Heatmap;
import cn.tolim.pudans.core.ai.stats.AiUsage.HeatmapQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.Overview;
import cn.tolim.pudans.core.ai.stats.AiUsage.OverviewQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.RequestDetail;
import cn.tolim.pudans.core.ai.stats.AiUsage.RequestPage;
import cn.tolim.pudans.core.ai.stats.AiUsage.RequestQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionDetail;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionPage;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionStats;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionStatsQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.Speed;
import cn.tolim.pudans.core.ai.stats.AiUsage.SpeedQuery;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver;
import cn.tolim.pudans.core.ai.stats.UsageQueryParameters;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiUsageEndpoint implements CustomEndpoint {

    private static final String TAG = "AiUsageV1alpha1";

    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private static final String CSV_DISPOSITION = "attachment; filename=\"ai-usage-requests.csv\"";

    private static final String CLEAR_OPERATION = "AI_USAGE_CLEAR";

    private static final String CLEAR_RESOURCE_TYPE = "AiUsage";

    private final AiUsage aiUsage;

    private final UsageAccessResolver accessResolver;

    private final AuditLog auditLog;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aistats/-/usage/overview", this::overview, builder -> builder
                        .operationId("getAiUsageOverview")
                        .description("Aggregated AI usage overview with daily series, rankings and quotas.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("team"))
                        .parameter(query("agent"))
                        .parameter(query("provider"))
                        .parameter(query("model"))
                        .parameter(query("bucket"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(Overview.class)))
                .GET("/aistats/-/usage/heatmap", this::heatmap, builder -> builder
                        .operationId("getAiUsageHeatmap")
                        .description("Daily usage buckets for the 53-week activity heatmap.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("metric"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(Heatmap.class)))
                .GET("/aistats/-/usage/speed", this::speed, builder -> builder
                        .operationId("getAiUsageSpeed")
                        .description("TTFT and TPS latency aggregates grouped by model or provider.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("groupBy"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(Speed.class)))
                .GET("/aistats/-/usage/requests", this::requestPage, builder -> builder
                        .operationId("listAiUsageRequests")
                        .description("Paginated AI chat request usage details.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("status"))
                        .parameter(query("provider"))
                        .parameter(query("model"))
                        .parameter(query("account"))
                        .parameter(query("agent"))
                        .parameter(query("agentSource"))
                        .parameter(query("mode"))
                        .parameter(query("routeGroup"))
                        .parameter(query("q"))
                        .parameter(query("purpose"))
                        .parameter(query("via"))
                        .parameter(query("callerKey"))
                        .parameter(query("retried"))
                        .parameter(query("beforeId"))
                        .parameter(query("limit"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(RequestPage.class)))
                .GET("/aistats/-/usage/requests/-/export", this::requestExport, builder -> builder
                        .operationId("exportAiUsageRequests")
                        .description("Export filtered AI chat request usage as CSV.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("status"))
                        .parameter(query("provider"))
                        .parameter(query("model"))
                        .parameter(query("account"))
                        .parameter(query("agent"))
                        .parameter(query("agentSource"))
                        .parameter(query("mode"))
                        .parameter(query("routeGroup"))
                        .parameter(query("q"))
                        .parameter(query("purpose"))
                        .parameter(query("via"))
                        .parameter(query("callerKey"))
                        .parameter(query("retried"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().description("Request usage CSV export.")))
                .GET("/aistats/-/usage/requests/{requestId}", this::requestDetail, builder -> builder
                        .operationId("getAiUsageRequest")
                        .description("AI chat request usage detail with route and retrieval traces.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("requestId").required(true))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(RequestDetail.class)))
                .GET("/aistats/-/usage/sessions", this::sessionPage, builder -> builder
                        .operationId("listAiUsageSessions")
                        .description("Paginated AI conversation usage aggregation.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("agent"))
                        .parameter(query("q"))
                        .parameter(query("provider"))
                        .parameter(query("model"))
                        .parameter(query("status"))
                        .parameter(query("routeGroup"))
                        .parameter(query("beforeId"))
                        .parameter(query("limit"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(SessionPage.class)))
                .GET("/aistats/-/usage/sessions/stats", this::sessionStats, builder -> builder
                        .operationId("getAiUsageSessionStats")
                        .description("AI conversation volume, activity and distribution aggregates.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("agent"))
                        .parameter(query("model"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(SessionStats.class)))
                .GET("/aistats/-/usage/sessions/{conversationId}", this::sessionDetail, builder -> builder
                        .operationId("getAiUsageSession")
                        .description("AI conversation usage detail with message counts and recent messages.")
                        .tag(TAG)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.PATH)
                                .name("conversationId")
                                .required(true))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(SessionDetail.class)))
                .GET("/aistats/-/usage/context", this::context, builder -> builder
                        .operationId("getAiUsageContext")
                        .description("AI context composition by agent and model.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("agent"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(ContextUsage.class)))
                .GET("/aistats/-/usage/context/grid", this::contextGrid, builder -> builder
                        .operationId("getAiUsageContextGrid")
                        .description("Per-request context composition grid for the 400-cell view.")
                        .tag(TAG)
                        .parameter(query("from"))
                        .parameter(query("to"))
                        .parameter(query("agent"))
                        .parameter(query("model"))
                        .parameter(query("limit"))
                        .parameter(scope("读取范围：self 或 all（仅管理员）。"))
                        .response(responseBuilder().implementation(ContextGrid.class)))
                .POST("/aistats/-/usage/-/clear", this::clear, builder -> builder
                        .operationId("clearAiUsage")
                        .description("Delete AI chat request usage before a date. Administrator only.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(ClearBody.class))
                        .response(responseBuilder().implementation(ClearResult.class)))
                .build();
    }

    private Mono<ServerResponse> overview(ServerRequest request) {
        var query = overviewQuery(request);
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.overview(query, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> heatmap(ServerRequest request) {
        var query = new HeatmapQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "metric"));
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.heatmap(query, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> speed(ServerRequest request) {
        var query = new SpeedQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "groupBy"));
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.speed(query, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> requestPage(ServerRequest request) {
        var query = requestQuery(request);
        var beforeId = UsageQueryParameters.beforeId(request);
        var limit = UsageQueryParameters.limit(request);
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.requests(query, beforeId, limit, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> requestExport(ServerRequest request) {
        var query = requestQuery(request);
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> ServerResponse.ok()
                        .contentType(CSV_MEDIA_TYPE)
                        .header(HttpHeaders.CONTENT_DISPOSITION, CSV_DISPOSITION)
                        .body(aiUsage.export(query, viewer), String.class));
    }

    private Mono<ServerResponse> requestDetail(ServerRequest request) {
        var requestId = request.pathVariable("requestId");
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.request(requestId, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> sessionPage(ServerRequest request) {
        var query = sessionQuery(request);
        var beforeId = UsageQueryParameters.beforeId(request);
        var limit = UsageQueryParameters.limit(request);
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.sessions(query, beforeId, limit, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> sessionStats(ServerRequest request) {
        var query = new SessionStatsQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "agent"),
                UsageQueryParameters.text(request, "model"));
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.sessionStats(query, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> sessionDetail(ServerRequest request) {
        var conversationId = conversationId(request);
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.session(conversationId, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> context(ServerRequest request) {
        var query = contextQuery(request);
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.context(query, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> contextGrid(ServerRequest request) {
        var query = new GridQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "agent"),
                UsageQueryParameters.text(request, "model"),
                UsageQueryParameters.intValue(
                        request,
                        "limit",
                        AiUsage.DEFAULT_GRID_LIMIT,
                        1,
                        AiUsage.MAX_GRID_LIMIT,
                        "limit 必须在 1 到 " + AiUsage.MAX_GRID_LIMIT + " 之间。",
                        "limit 必须是整数。"));
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiUsage.contextGrid(query, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> clear(ServerRequest request) {
        return request.bodyToMono(ClearBody.class)
                .defaultIfEmpty(ClearBody.EMPTY)
                .zipWith(accessResolver.viewer(request))
                .flatMap(tuple -> {
                    var body = tuple.getT1();
                    var viewer = tuple.getT2();
                    var query = new ClearQuery(body.beforeDate(), body.provider(), body.model());
                    return aiUsage
                            .clear(query, viewer)
                            .doOnNext(result -> recordClear(request, viewer, query, result))
                            .flatMap(result -> ServerResponse.ok().bodyValue(result));
                });
    }

    private void recordClear(
            ServerRequest request, UsageAccessResolver.Viewer viewer, ClearQuery query, ClearResult result) {
        var context = new LinkedHashMap<String, Object>();
        context.put("operatorId", viewer.userId());
        context.put("before", query.before() == null ? null : query.before().toString());
        context.put("provider", query.provider());
        context.put("model", query.model());
        context.put("requestsDeleted", result.requestsDeleted());
        context.put("modelRequestsDeleted", result.modelRequestsDeleted());
        auditLog.record(
                request.exchange(),
                CLEAR_OPERATION,
                CLEAR_RESOURCE_TYPE,
                "usage",
                "SUCCESS",
                0L,
                context);
    }

    private static OverviewQuery overviewQuery(ServerRequest request) {
        return new OverviewQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "team"),
                UsageQueryParameters.text(request, "agent"),
                UsageQueryParameters.text(request, "provider"),
                UsageQueryParameters.text(request, "model"),
                UsageQueryParameters.text(request, "bucket"));
    }

    private static RequestQuery requestQuery(ServerRequest request) {
        return new RequestQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "status"),
                UsageQueryParameters.text(request, "provider"),
                UsageQueryParameters.text(request, "model"),
                UsageQueryParameters.text(request, "account"),
                UsageQueryParameters.text(request, "agent"),
                UsageQueryParameters.text(request, "agentSource"),
                UsageQueryParameters.text(request, "mode"),
                UsageQueryParameters.text(request, "routeGroup"),
                UsageQueryParameters.text(request, "q"),
                UsageQueryParameters.text(request, "purpose"),
                UsageQueryParameters.text(request, "via"),
                UsageQueryParameters.text(request, "callerKey"),
                UsageQueryParameters.bool(request, "retried"));
    }

    private static SessionQuery sessionQuery(ServerRequest request) {
        return new SessionQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "agent"),
                UsageQueryParameters.text(request, "q"),
                UsageQueryParameters.text(request, "provider"),
                UsageQueryParameters.text(request, "model"),
                UsageQueryParameters.text(request, "status"),
                UsageQueryParameters.text(request, "routeGroup"));
    }

    private static ContextQuery contextQuery(ServerRequest request) {
        return new ContextQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "agent"));
    }

    private static long conversationId(ServerRequest request) {
        try {
            return Long.parseLong(request.pathVariable("conversationId"));
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.CONVERSATION_INVALID, "conversationId 必须是整数。");
        }
    }

    private static org.springdoc.core.fn.builders.parameter.Builder query(String name) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(false);
    }

    private static org.springdoc.core.fn.builders.parameter.Builder scope(String description) {
        return parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("scope")
                .required(false)
                .description(description);
    }

    public record ClearBody(
            @Nullable String before, @Nullable String provider, @Nullable String model) {

        private static final ClearBody EMPTY = new ClearBody(null, null, null);

        @Nullable LocalDate beforeDate() {
            if (!StringUtils.hasText(before)) {
                return null;
            }
            try {
                return LocalDate.parse(before.trim());
            } catch (DateTimeParseException error) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, AiUsageErrorCodes.DATE_INVALID, "before 必须是 ISO 日期。");
            }
        }
    }
}