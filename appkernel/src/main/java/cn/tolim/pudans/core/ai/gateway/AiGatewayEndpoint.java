package cn.tolim.pudans.core.ai.gateway;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.AiPlan;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolCodecRegistry;
import cn.tolim.pudans.core.ai.gateway.protocol.AiResponseEncoders;
import cn.tolim.pudans.core.ai.gateway.protocol.AiStreamEncoder;
import cn.tolim.pudans.core.ai.gateway.protocol.GeminiGatewayCodec;
import cn.tolim.pudans.core.ai.plan.PlanQuotas;
import cn.tolim.pudans.core.ai.plan.PlanUsage;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiGatewayEndpoint implements CustomEndpoint {

    private static final String TAG = "AiGatewayV1alpha1";

    private static final String CODE_AUTH_REQUIRED = "ai-gateway-auth-required";

    private final AiProtocolCodecRegistry codecRegistry;

    private final AiGateway aiGateway;

    private final AiResponseEncoders responseEncoders;

    private final GeminiGatewayCodec geminiCodec;

    private final GatewayHealth gatewayHealth;

    private final AiEndpointRequests requests;

    private final PlanUsage planUsage;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aigateway/-/openai/v1/chat/completions",
                        request -> handle(request, AiProvider.Protocol.OPENAI_CHAT),
                        builder -> builder.operationId("openAiChatCompletions")
                                .description("OpenAI Chat compatible agent gateway.")
                                .tag(TAG)
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder()))
                .POST("/aigateway/-/anthropic/v1/messages", request -> handle(request, AiProvider.Protocol.ANTHROPIC),
                        builder -> builder.operationId("anthropicMessages")
                                .description("Anthropic Messages compatible agent gateway.")
                                .tag(TAG)
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder()))
                .POST("/aigateway/-/gemini/v1beta/models/{model}:generateContent",
                        request -> handleGemini(request, false),
                        builder -> builder.operationId("geminiGenerateContent")
                                .description("Gemini generateContent compatible agent gateway.")
                                .tag(TAG)
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder()))
                .POST("/aigateway/-/gemini/v1beta/models/{model}:streamGenerateContent",
                        request -> handleGemini(request, true),
                        builder -> builder.operationId("geminiStreamGenerateContent")
                                .description("Gemini streamGenerateContent compatible agent gateway.")
                                .tag(TAG)
                                .requestBody(requestBodyBuilder().required(true))
                                .response(responseBuilder()))
                .GET("/aigateway/-/status", this::status,
                        builder -> builder.operationId("aiGatewayStatus")
                                .description("Agent gateway runtime status.")
                                .tag(TAG)
                                .response(responseBuilder()))
                .build();
    }

    private Mono<ServerResponse> handle(ServerRequest request, AiProvider.Protocol protocol) {
        return userId(request)
                .zipWith(body(request))
                .zipWith(callerKey(request).defaultIfEmpty(""))
                .flatMap(tuple -> {
                    var ir = codecRegistry.require(protocol).parse(tuple.getT1().getT2());
                    var invocation = invocation(
                            request, protocol, ir, tuple.getT1().getT1(), caller(tuple.getT2()));
                    return ir.stream() ? stream(invocation) : complete(invocation);
                });
    }

    private Mono<ServerResponse> handleGemini(ServerRequest request, boolean stream) {
        return userId(request)
                .zipWith(body(request))
                .zipWith(callerKey(request).defaultIfEmpty(""))
                .flatMap(tuple -> {
                    var model = request.pathVariable("model");
                    var ir = geminiCodec.parse(tuple.getT1().getT2(), model, stream);
                    var invocation = invocation(
                            request,
                            AiProvider.Protocol.GEMINI,
                            ir,
                            tuple.getT1().getT1(),
                            caller(tuple.getT2()));
                    return stream ? stream(invocation) : complete(invocation);
                });
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        return gatewayHealth
                .status()
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(status));
    }

    private static @Nullable String caller(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    // PAT 使用 pat_name 标识调用密钥，非 PAT 的 JWT 回退到 jti
    private static Mono<String> callerKey(ServerRequest request) {
        return request.principal()
                .flatMap(principal -> principal instanceof JwtAuthenticationToken jwt
                        ? Mono.justOrEmpty(patClaim(jwt))
                        : Mono.empty());
    }

    private static String patClaim(JwtAuthenticationToken jwt) {
        var token = jwt.getToken();
        var patName = token.getClaimAsString("pat_name");
        if (StringUtils.hasText(patName)) {
            return patName;
        }
        var tokenId = token.getClaimAsString("tokenId");
        return StringUtils.hasText(tokenId) ? tokenId : token.getClaimAsString("jti");
    }

    private static Mono<String> body(ServerRequest request) {
        return request.bodyToMono(String.class)
                .switchIfEmpty(Mono.error(bodyInvalid()))
                .flatMap(body -> StringUtils.hasText(body) ? Mono.just(body) : Mono.error(bodyInvalid()));
    }

    private static ApiException bodyInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "ai-gateway-body-invalid", "请求体不能为空。");
    }

    private Mono<ServerResponse> complete(AiGatewayInvocation invocation) {
        return aiGateway
                .complete(invocation)
                .flatMap(result -> {
                    var encoder = responseEncoders.require(invocation.protocol());
                    return planHeaders(invocation).flatMap(headers -> ServerResponse.ok()
                            .headers(target -> target.addAll(headers))
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(encoder.encode(result.response(), result.meta().model())));
                })
                .onErrorResume(error -> openAiQuota(invocation.protocol(), error));
    }

    private Mono<ServerResponse> stream(AiGatewayInvocation invocation) {
        var encoder = responseEncoders.require(invocation.protocol());
        var usage = new AtomicReference<AiTokenUsage>();
        var body = Flux.defer(() -> {
            var streamEncoder = new AtomicReference<AiStreamEncoder>();
            return aiGateway.stream(invocation).concatMap(event -> {
                if (event instanceof AiGatewayEvent.Meta meta) {
                    var created = encoder.stream(invocation.requestId(), meta.meta().model(), null);
                    streamEncoder.set(created);
                    return text(created.start());
                }
                var current = streamEncoder.get();
                if (current == null) {
                    current = encoder.stream(invocation.requestId(), metaModel(invocation), null);
                    streamEncoder.set(current);
                    return Flux.concat(text(current.start()), encode(current, event, usage, invocation.protocol()));
                }
                return encode(current, event, usage, invocation.protocol());
            });
        });
        return planHeaders(invocation).flatMap(headers -> ServerResponse.ok()
                .headers(target -> target.addAll(headers))
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body, String.class));
    }

    // 套餐用量响应头在成功响应前附加，SYSTEM 调用和套餐缺失时不附加
    private Mono<HttpHeaders> planHeaders(AiGatewayInvocation invocation) {
        if (invocation.usageScope() != AiPlan.BillingScope.USER) {
            return Mono.just(new HttpHeaders());
        }
        return planUsage
                .usage(invocation.userId())
                .map(this::planHeaders)
                .onErrorResume(error -> Mono.just(new HttpHeaders()))
                .switchIfEmpty(Mono.just(new HttpHeaders()));
    }

    private HttpHeaders planHeaders(PlanUsage.UsageView usage) {
        var headers = new HttpHeaders();
        if (usage.plan() != null) {
            headers.set("X-Plan-Name", StringUtils.hasText(usage.plan().displayName())
                    ? usage.plan().displayName()
                    : usage.plan().name());
        }
        windowHeaders(headers, "X-Plan-5h", usage.fiveHour());
        windowHeaders(headers, "X-Plan-Weekly", usage.weekly());
        return headers;
    }

    private static void windowHeaders(
            HttpHeaders headers, String prefix, PlanUsage.WindowUsage window) {
        headers.set(prefix + "-Limit", Long.toString(window.limit()));
        headers.set(prefix + "-Remaining", Long.toString(window.remaining()));
        if (window.endsAt() != null) {
            headers.set(prefix + "-Reset", window.endsAt().toString());
        }
    }

    private static Mono<ServerResponse> openAiQuota(AiProvider.Protocol protocol, Throwable error) {
        if (protocol != AiProvider.Protocol.OPENAI_CHAT
                || !(error instanceof ApiException api)
                || api.getStatusCode().value() != HttpStatus.TOO_MANY_REQUESTS.value()
                || !PlanQuotas.CODE_QUOTA_EXCEEDED.equals(api.getCode())) {
            return Mono.error(error);
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "insufficient_quota");
        body.put("code", api.getCode());
        body.put("message", api.getReason() == null ? "套餐额度不足。" : api.getReason());
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("error", body));
    }

    private Flux<String> encode(
            AiStreamEncoder encoder,
            AiGatewayEvent event,
            AtomicReference<AiTokenUsage> usage,
            AiProvider.Protocol protocol) {
        return switch (event) {
            case AiGatewayEvent.Meta ignored -> Flux.empty();
            case AiGatewayEvent.Delta delta -> text(encoder.event(delta.event()));
            case AiGatewayEvent.Usage value -> {
                usage.set(value.usage());
                yield Flux.empty();
            }
            case AiGatewayEvent.Done done -> text(encoder.finish(new AiStreamEvent(
                    null, null, null, usage.get(), done.finishReason())));
            case AiGatewayEvent.Error error -> text(error(protocol, error));
        };
    }

    private static Flux<String> text(String value) {
        return value == null || value.isEmpty() ? Flux.empty() : Flux.just(value);
    }

    private static String error(AiProvider.Protocol protocol, AiGatewayEvent.Error error) {
        var message = error.message() == null
                ? "网关调用失败。"
                : error.message().replace("\\", "\\\\").replace("\"", "\\\"");
        return switch (protocol) {
            case ANTHROPIC -> "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\""
                    + error.code() + "\",\"message\":\"" + message + "\"}}\n\n";
            case GEMINI -> "data: {\"error\":{\"code\":" + (error.retryable() ? 503 : 500)
                    + ",\"message\":\"" + message + "\",\"status\":\""
                    + (error.retryable() ? "UNAVAILABLE" : "INTERNAL") + "\"}}\n\n";
            default -> "data: {\"error\":{\"code\":\"" + openAiErrorType(error.code()) + "\",\"message\":\""
                    + message + "\",\"retryable\":" + error.retryable() + "}}\n\n";
        };
    }

    private static String openAiErrorType(String code) {
        return PlanQuotas.CODE_QUOTA_EXCEEDED.equals(code) ? "insufficient_quota" : code;
    }

    private static String metaModel(AiGatewayInvocation invocation) {
        var model = invocation.ir().requestParams().model();
        return StringUtils.hasText(model) ? model : "";
    }

    private Mono<String> userId(ServerRequest request) {
        return requests.userId(request, CODE_AUTH_REQUIRED, "缺少有效的 Agent 认证信息。");
    }

    private AiGatewayInvocation invocation(
            ServerRequest request,
            AiProvider.Protocol protocol,
            AiChatIr ir,
            String userId,
            @Nullable String callerKey) {
        var sessionKey = AiGatewayHeaders.sessionKey(request);
        var requestId = "req-" + UUID.randomUUID();
        return new AiGatewayInvocation(
                protocol,
                ir,
                userId,
                AiGatewayHeaders.agentSource(request, protocol),
                sessionKey,
                AiGatewayHeaders.sessionId(request),
                requestId,
                AiGatewayHeaders.idempotencyKey(request, requestId),
                AiGatewayHeaders.mode(request),
                AiGatewayHeaders.routeGroup(request),
                AiGatewayHeaders.agentName(request),
                AiGatewayHeaders.conversationId(request),
                StringUtils.hasText(ir.requestParams().model())
                        ? ir.requestParams().model().strip()
                        : null,
                callerKey);
    }
}
