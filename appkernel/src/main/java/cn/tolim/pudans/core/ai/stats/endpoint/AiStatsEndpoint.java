package cn.tolim.pudans.core.ai.stats.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.stats.AiStats;
import cn.tolim.pudans.core.ai.stats.AiStats.RoutingHistoryDetail;
import cn.tolim.pudans.core.ai.stats.AiStats.RoutingHistoryPage;
import cn.tolim.pudans.core.ai.stats.AiStats.RoutingHistoryQuery;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver;
import cn.tolim.pudans.core.ai.stats.UsageQueryParameters;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.KnowledgeBaseStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.MemoryStatsRow;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiStatsEndpoint implements CustomEndpoint {

    private static final String TAG = "AiStatsV1alpha1";

    private final AiStats aiStats;

    private final UsageAccessResolver accessResolver;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aistats/-/routing", this::routing, builder -> builder
                        .operationId("getAiRoutingStats")
                        .description("Aggregated routing and billing statistics.")
                        .tag(TAG)
                        .parameter(daysParameter())
                        .response(responseBuilder().implementation(AiStats.RoutingStats.class)))
                .GET("/aistats/-/routing/live", this::routingLive, builder -> builder
                        .operationId("getAiRoutingLive")
                        .description("Live routing overview with recent gateway requests.")
                        .tag(TAG)
                        .parameter(daysParameter())
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("limit")
                                .required(false)
                                .description("Recent request limit (1-200, default 20)."))
                        .response(responseBuilder().implementation(AiStats.RoutingLive.class)))
                .GET("/aistats/-/routing/history", this::routingHistory, builder -> builder
                        .operationId("listAiRoutingHistory")
                        .description("Paginated routing history with route traces.")
                        .tag(TAG)
                        .parameter(queryParameter("from"))
                        .parameter(queryParameter("to"))
                        .parameter(queryParameter("status"))
                        .parameter(queryParameter("provider"))
                        .parameter(queryParameter("model"))
                        .parameter(queryParameter("agent"))
                        .parameter(queryParameter("group"))
                        .parameter(queryParameter("beforeId"))
                        .parameter(queryParameter("limit"))
                        .parameter(scopeParameter())
                        .response(responseBuilder().implementation(RoutingHistoryPage.class)))
                .GET("/aistats/-/routing/history/{requestId}", this::routingHistoryDetail, builder -> builder
                        .operationId("getAiRoutingHistory")
                        .description("Routing history detail with full route and retrieval traces.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("requestId").required(true))
                        .parameter(scopeParameter())
                        .response(responseBuilder().implementation(RoutingHistoryDetail.class)))
                .GET("/aistats/-/units", this::units, builder -> builder
                        .operationId("getAiUnitStats")
                        .description("Daily usage units by model.")
                        .tag(TAG)
                        .parameter(daysParameter())
                        .response(responseBuilder().implementation(AiStats.PointStats.class)))
                .GET("/aistats/-/points", this::units, builder -> builder
                        .operationId("getAiPointStats")
                        .description("Deprecated: use /aistats/-/units instead.")
                        .deprecated(true)
                        .tag(TAG)
                        .parameter(daysParameter())
                        .response(responseBuilder().implementation(AiStats.PointStats.class)))
                .GET("/aistats/-/memory", this::memory, builder -> builder
                        .operationId("getAiMemoryStats")
                        .description("Memory record counts by layer.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(MemoryStatsRow.class)))
                .GET("/aistats/-/kb", this::knowledgeBase, builder -> builder
                        .operationId("getAiKnowledgeBaseStats")
                        .description("Knowledge base document, chunk and query counts.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(KnowledgeBaseStatsRow.class)))
                .GET("/aistats/-/models", this::models, builder -> builder
                        .operationId("getAiModelStats")
                        .description("Model request counts, usage units and media units by model and capability.")
                        .tag(TAG)
                        .parameter(daysParameter())
                        .response(responseBuilder().implementation(AiStats.ModelStats.class)))
                .build();
    }

    private Mono<ServerResponse> routing(ServerRequest request) {
        return aiStats.routing(days(request))
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> routingLive(ServerRequest request) {
        return aiStats
                .routingLive(days(request), limit(request))
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> routingHistory(ServerRequest request) {
        var query = new RoutingHistoryQuery(
                UsageQueryParameters.date(request, "from"),
                UsageQueryParameters.date(request, "to"),
                UsageQueryParameters.text(request, "status"),
                UsageQueryParameters.text(request, "provider"),
                UsageQueryParameters.text(request, "model"),
                UsageQueryParameters.text(request, "agent"),
                UsageQueryParameters.text(request, "group"));
        var beforeId = UsageQueryParameters.beforeId(request);
        var limit = UsageQueryParameters.limit(request);
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiStats.routingHistory(query, beforeId, limit, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> routingHistoryDetail(ServerRequest request) {
        var requestId = request.pathVariable("requestId");
        return accessResolver
                .viewer(request)
                .flatMap(viewer -> aiStats.routingHistoryDetail(requestId, viewer))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> units(ServerRequest request) {
        return aiStats.units(days(request))
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> memory(ServerRequest request) {
        return aiStats.memory().flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> knowledgeBase(ServerRequest request) {
        return aiStats.knowledgeBase().flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> models(ServerRequest request) {
        return aiStats.models(days(request))
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private static int days(ServerRequest request) {
        var value = request.queryParam("days").orElse("14");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("days 必须是整数。");
        }
    }

    private static int limit(ServerRequest request) {
        var value = request.queryParam("limit").orElse("20");
        try {
            var parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 200) {
                throw new ServerWebInputException("limit 必须在 1 到 200 之间。");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("limit 必须是整数。");
        }
    }

    private static org.springdoc.core.fn.builders.parameter.Builder queryParameter(String name) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(false);
    }

    private static org.springdoc.core.fn.builders.parameter.Builder scopeParameter() {
        return parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("scope")
                .required(false)
                .description("读取范围：self 或 all（仅管理员）。");
    }

    private static org.springdoc.core.fn.builders.parameter.Builder daysParameter() {
        return parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("days")
                .required(false)
                .description("Number of days (1-90, default 14).");
    }
}
