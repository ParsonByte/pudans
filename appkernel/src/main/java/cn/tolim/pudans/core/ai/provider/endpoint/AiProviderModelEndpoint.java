package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiProviderModelEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private final DeepSeekProviderAccess access;

    private final DeepSeekModelsClient modelsClient;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiproviders/{name}/models", this::list, builder -> builder
                        .operationId("listAiProviderModels")
                        .description("List models exposed by the provider.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .response(responseBuilder().implementation(DeepSeekModelsClient.ModelList.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> access.requireAccount(provider)
                        .flatMap(account -> modelsClient.list(provider, account)))
                .flatMap(models -> ServerResponse.ok().bodyValue(models));
    }

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description);
    }
}
