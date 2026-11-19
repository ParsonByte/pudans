package cn.tolim.pudans.core.ai.stats.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.stats.AiStatsService;
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

    private final AiStatsService statsService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aistats/-/routing", this::routing, builder -> builder
                        .operationId("getAiRoutingStats")
                        .description("Aggregated routing and billing statistics.")
                        .tag(TAG)
                        .parameter(daysParameter())
                        .response(responseBuilder().implementation(AiStatsService.RoutingStats.class)))
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
                        .response(responseBuilder().implementation(AiStatsService.RoutingLive.class)))
                .GET("/aistats/-/points", this::points, builder -> builder
                        .operationId("getAiPointStats")
                        .description("Daily point consumption by model.")
                        .tag(TAG)
                        .parameter(daysParameter())
                        .response(responseBuilder().implementation(AiStatsService.PointStats.class)))
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
                        .description("Model request counts, points and media units by model and capability.")
                        .tag(TAG)
                        .parameter(daysParameter())
                        .response(responseBuilder().implementation(AiStatsService.ModelStats.class)))
                .build();
    }

    private Mono<ServerResponse> routing(ServerRequest request) {
        return statsService.routing(days(request))
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> routingLive(ServerRequest request) {
        return statsService
                .routingLive(days(request), limit(request))
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> points(ServerRequest request) {
        return statsService.points(days(request))
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> memory(ServerRequest request) {
        return statsService.memory().flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> knowledgeBase(ServerRequest request) {
        return statsService.knowledgeBase().flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }

    private Mono<ServerResponse> models(ServerRequest request) {
        return statsService.models(days(request))
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

    private static org.springdoc.core.fn.builders.parameter.Builder daysParameter() {
        return parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("days")
                .required(false)
                .description("Number of days (1-90, default 14).");
    }
}
