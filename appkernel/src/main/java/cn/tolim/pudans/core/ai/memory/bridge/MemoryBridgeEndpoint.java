package cn.tolim.pudans.core.ai.memory.bridge;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.gateway.AiGatewayHeaders;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryBridgeEndpoint implements CustomEndpoint {

    private static final String TAG = "MemoryBridgeV1alpha1";

    private static final String CODE_BODY_INVALID = "ai-memory-bridge-request-invalid";

    private static final String CODE_SESSION_MISSING = "ai-gateway-session-missing";

    private final MemoryBridge memoryBridge;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/memorybridge/-/atomic/search", this::atomicSearch, builder -> builder
                        .operationId("searchAtomicMemory")
                        .description("Read-only memory bridge: search L1 atomic memories "
                                + "for the current agent session.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(SearchRequest.class))
                        .response(responseBuilder().implementationArray(MemoryInjection.Ref.class)))
                .POST("/memorybridge/-/conversation/search", this::conversationSearch, builder -> builder
                        .operationId("searchConversationMemory")
                        .description("Read-only memory bridge: search L0 conversation messages "
                                + "for the current session.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(SearchRequest.class))
                        .response(responseBuilder().implementationArray(Message.class)))
                .POST("/memorybridge/-/scenario/ls", this::scenarios, builder -> builder
                        .operationId("listMemoryScenarios")
                        .description("Read-only memory bridge: list L2 scenes for the current session scope.")
                        .tag(TAG)
                        .response(responseBuilder().implementationArray(L2SceneBlock.class)))
                .POST("/memorybridge/-/scenario/read", this::readScenario, builder -> builder
                        .operationId("readMemoryScenario")
                        .description("Read-only memory bridge: read one L2 scene and its visible L1 memories.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(ScenarioRequest.class))
                        .response(responseBuilder().implementation(MemoryBridge.SceneDetail.class)))
                .build();
    }

    private Mono<ServerResponse> atomicSearch(ServerRequest request) {
        return identity(request)
                .zipWith(searchRequest(request))
                .flatMap(tuple -> memoryBridge
                        .atomicSearch(
                                tuple.getT1().userId(),
                                tuple.getT1().agentSource(),
                                tuple.getT1().sessionKey(),
                                tuple.getT2().query(),
                                limit(tuple.getT2().limit()))
                        .flatMap(refs -> ServerResponse.ok().bodyValue(refs)));
    }

    private Mono<ServerResponse> conversationSearch(ServerRequest request) {
        return identity(request)
                .zipWith(searchRequest(request))
                .flatMap(tuple -> memoryBridge
                        .conversationSearch(
                                tuple.getT1().userId(),
                                tuple.getT1().agentSource(),
                                tuple.getT1().sessionKey(),
                                tuple.getT2().query(),
                                limit(tuple.getT2().limit()))
                        .collectList()
                        .flatMap(messages -> ServerResponse.ok().bodyValue(messages)));
    }

    private Mono<ServerResponse> scenarios(ServerRequest request) {
        return identity(request)
                .flatMap(identity -> memoryBridge
                        .scenarios(identity.userId(), identity.agentSource(), identity.sessionKey())
                        .collectList()
                        .flatMap(scenes -> ServerResponse.ok().bodyValue(scenes)));
    }

    private Mono<ServerResponse> readScenario(ServerRequest request) {
        return identity(request)
                .zipWith(request.bodyToMono(ScenarioRequest.class)
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "请求体不能为空。"))))
                .flatMap(tuple -> {
                    if (!StringUtils.hasText(tuple.getT2().sceneKey())) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "sceneKey 不能为空。"));
                    }
                    return memoryBridge
                            .readScenario(
                                    tuple.getT1().userId(),
                                    tuple.getT1().agentSource(),
                                    tuple.getT1().sessionKey(),
                                    tuple.getT2().sceneKey())
                            .flatMap(detail -> ServerResponse.ok().bodyValue(detail));
                });
    }

    private Mono<SearchRequest> searchRequest(ServerRequest request) {
        return request.bodyToMono(SearchRequest.class)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "请求体不能为空。")))
                .flatMap(body -> StringUtils.hasText(body.query())
                        ? Mono.just(body)
                        : Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "query 不能为空。")));
    }

    private Mono<Identity> identity(ServerRequest request) {
        var sessionKey = AiGatewayHeaders.providedSessionKey(request);
        if (!StringUtils.hasText(sessionKey)) {
            return Mono.error(new ApiException(
                    HttpStatus.UNAUTHORIZED, CODE_SESSION_MISSING, "无法识别 Agent 会话。"));
        }
        var agentSource = AiGatewayHeaders.agentSource(request, AiProvider.Protocol.OPENAI_CHAT);
        return requests.userId(request, "ai-gateway-auth-required", "缺少有效的认证信息。")
                .map(userId -> new Identity(userId, agentSource, sessionKey));
    }
    private static int limit(Integer value) {
        if (value == null) {
            return 10;
        }
        return Math.max(1, Math.min(50, value));
    }

    private record Identity(String userId, String agentSource, String sessionKey) {}

    public record SearchRequest(String query, Integer limit) {}

    public record ScenarioRequest(String sceneKey) {}
}
