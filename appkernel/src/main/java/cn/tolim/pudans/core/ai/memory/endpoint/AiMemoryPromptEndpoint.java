package cn.tolim.pudans.core.ai.memory.endpoint;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiMemoryPrompt;
import cn.tolim.pudans.contract.core.extension.ai.AiMemoryPromptBinding;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.ResolvedMemoryPrompt;
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
public class AiMemoryPromptEndpoint implements CustomEndpoint {

    private static final String TAG = "AiMemoryPromptV1alpha1";

    private final ReactiveExtensionClient client;

    private final MemoryPromptResolver resolver;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aimemoryprompts/-/resolve", this::resolve, builder -> builder
                        .operationId("resolveAiMemoryPrompt")
                        .description("Resolve the effective memory prompt for a layer, team and agent.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("layer").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("agent").required(false))
                        .response(responseBuilder().implementation(ResolvedMemoryPrompt.class)))
                .build();
    }

    private Mono<ServerResponse> resolve(ServerRequest request) {
        var layer = layer(request.queryParam("layer").orElse(null));
        var team = request.queryParam("team").orElse(null);
        var agent = request.queryParam("agent").orElse(null);
        return Mono.zip(
                        client.listAll(AiMemoryPromptBinding.class, ListOptions.builder().build(), defaultSort())
                                .collectList(),
                        client.listAll(AiMemoryPrompt.class, ListOptions.builder().build(), defaultSort())
                                .collectList())
                .map(tuple -> resolver.resolve(layer, team, agent, tuple.getT1(), tuple.getT2()))
                .flatMap(resolved -> ServerResponse.ok().bodyValue(resolved));
    }

    private static AiMemoryPrompt.Layer layer(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServerWebInputException("layer 不能为空。");
        }
        try {
            return AiMemoryPrompt.Layer.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ServerWebInputException("layer 必须是 L1、L2 或 L3。");
        }
    }
}
