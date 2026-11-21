package cn.tolim.pudans.core.ai.memory.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiMemoryEndpoint implements CustomEndpoint {

    private static final String TAG = "MemoryV1alpha1";

    private final MemoryViews memoryService;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/memories", this::list, builder -> builder
                        .operationId("listMemories")
                        .description("List the current user's active L1 memories.")
                        .tag(TAG)
                        .parameter(teamParameter())
                        .parameter(agentParameter())
                        .parameter(typeParameter())
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(L1MemoryRecord.class)))
                .GET("/memories/-/search", this::search, builder -> builder
                        .operationId("searchMemories")
                        .description("Hybrid search over the current user's memories.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("q").required(true))
                        .parameter(teamParameter())
                        .parameter(agentParameter())
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(MemoryInjection.Ref.class)))
                .GET("/memories/-/scenes", this::scenes, builder -> builder
                        .operationId("listMemoryScenes")
                        .description("List L2 scenes visible to the current team and agent.")
                        .tag(TAG)
                        .parameter(teamParameter())
                        .parameter(agentParameter())
                        .response(responseBuilder().implementationArray(L2SceneBlock.class)))
                .GET("/memories/-/profile", this::profile, builder -> builder
                        .operationId("getMemoryProfile")
                        .description("Get the L3 persona for the current team and agent.")
                        .tag(TAG)
                        .parameter(teamParameter())
                        .parameter(agentParameter())
                        .response(responseBuilder().implementation(L3Persona.class)))
                .GET("/memories/{name}", this::detail, builder -> builder
                        .operationId("getMemory")
                        .description("Get one L1 memory by 'L1:<id>' or numeric id.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(teamParameter())
                        .parameter(agentParameter())
                        .response(responseBuilder().implementation(L1MemoryRecord.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return userId(request).flatMap(userId -> memoryService
                .list(
                        userId,
                        query(request, "team"),
                        query(request, "agent"),
                        type(request),
                        longQuery(request, "beforeId", Long.MAX_VALUE),
                        intQuery(request, "limit", 20, 1, 100))
                .flatMap(memories -> ServerResponse.ok().bodyValue(memories)));
    }

    private Mono<ServerResponse> search(ServerRequest request) {
        var query = request.queryParam("q").orElse(null);
        if (!StringUtils.hasText(query)) {
            return Mono.error(new ServerWebInputException("q 不能为空。"));
        }
        return userId(request).flatMap(userId -> memoryService
                .search(
                        userId,
                        query(request, "team"),
                        query(request, "agent"),
                        query,
                        intQuery(request, "limit", 10, 1, 50))
                .flatMap(results -> ServerResponse.ok().bodyValue(results)));
    }

    private Mono<ServerResponse> scenes(ServerRequest request) {
        return userId(request).flatMap(userId -> memoryService
                .scenes(userId, query(request, "team"), query(request, "agent"))
                .collectList()
                .flatMap(scenes -> ServerResponse.ok().bodyValue(scenes)));
    }

    private Mono<ServerResponse> profile(ServerRequest request) {
        return userId(request).flatMap(userId -> memoryService
                .profile(userId, query(request, "team"), query(request, "agent"))
                .flatMap(persona -> ServerResponse.ok().bodyValue(persona))
                .switchIfEmpty(ServerResponse.notFound().build()));
    }

    private Mono<ServerResponse> detail(ServerRequest request) {
        return userId(request).flatMap(userId -> memoryService
                .detail(
                        userId,
                        request.pathVariable("name"),
                        query(request, "team"),
                        query(request, "agent"))
                .flatMap(memory -> ServerResponse.ok().bodyValue(memory)));
    }

    private Mono<String> userId(ServerRequest request) {
        return requests.userId(request);
    }

    private static String query(ServerRequest request, String name) {
        return request.queryParam(name).filter(StringUtils::hasText).orElse(null);
    }

    private static MemoryType type(ServerRequest request) {
        var value = query(request, "type");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MemoryType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ServerWebInputException("type 不合法。");
        }
    }

    private static long longQuery(ServerRequest request, String name, long defaultValue) {
        var value = query(request, name);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new ServerWebInputException(name + " 必须是整数。");
        }
    }

    private static int intQuery(
            ServerRequest request, String name, int defaultValue, int min, int max) {
        var value = query(request, name);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
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

    private static org.springdoc.core.fn.builders.parameter.Builder teamParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("team").required(false);
    }

    private static org.springdoc.core.fn.builders.parameter.Builder agentParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("agent").required(false);
    }

    private static org.springdoc.core.fn.builders.parameter.Builder typeParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("type").required(false);
    }
}
