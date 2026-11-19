package cn.tolim.pudans.core.ai.gateway.session;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
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
public class AiGatewaySessionEndpoint implements CustomEndpoint {

    private static final String TAG = "AiGatewaySessionV1alpha1";

    private final AiGatewaySessionRepository repository;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aigatewaysessions", this::list, builder -> builder
                        .operationId("listAiGatewaySessions")
                        .description("List gateway session bindings for troubleshooting.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .response(responseBuilder().implementationArray(GatewaySession.class)))
                .GET("/aigatewaysessions/{name}", this::get, builder -> builder
                        .operationId("getAiGatewaySession")
                        .description("Get a gateway session binding.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(GatewaySession.class)))
                .DELETE("/aigatewaysessions/{name}", this::delete, builder -> builder
                        .operationId("deleteAiGatewaySession")
                        .description("Delete a gateway session binding without deleting memories.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder()))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var limit = parse(request.queryParam("limit").orElse("20"), 1, 200, "limit");
        var beforeId = parse(request.queryParam("beforeId").orElse(String.valueOf(Long.MAX_VALUE)),
                1, Long.MAX_VALUE, "beforeId");
        return repository
                .list(beforeId, limit)
                .collectList()
                .flatMap(sessions -> ServerResponse.ok().bodyValue(sessions));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return repository
                .findById(parse(request.pathVariable("name"), 1, Long.MAX_VALUE, "name"))
                .flatMap(session -> ServerResponse.ok().bodyValue(session))
                .switchIfEmpty(Mono.error(new ServerWebInputException("网关会话不存在。")));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return repository
                .deleteById(parse(request.pathVariable("name"), 1, Long.MAX_VALUE, "name"))
                .flatMap(deleted -> deleted ? ServerResponse.noContent().build() : ServerResponse.notFound().build());
    }

    private static int parse(String value, int min, int max, String name) {
        try {
            var parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new ServerWebInputException(name + " 超出允许范围。");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ServerWebInputException(name + " 必须是整数。");
        }
    }

    private static long parse(String value, long min, long max, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) {
                throw new ServerWebInputException(name + " 超出允许范围。");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ServerWebInputException(name + " 必须是整数。");
        }
    }
}
