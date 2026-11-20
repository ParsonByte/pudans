package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.provider.AiProviderModelSyncService;
import cn.tolim.pudans.core.ai.provider.AiProviderModelSyncService.SyncResult;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
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

    private static final String CODE_DISABLED = "ai-provider-disabled";

    private static final String CODE_NO_ACCOUNT = "ai-provider-account-missing";

    private static final String CODE_UNSUPPORTED = "ai-provider-models-unsupported";

    private final AiProviderModelSyncService modelSyncService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiproviders/{name}/models", this::list, builder -> builder
                        .operationId("listAiProviderModels")
                        .description("List models exposed by the provider.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .response(responseBuilder().implementation(DeepSeekModelsClient.ModelList.class)))
                .POST("/aiproviders/{name}/actions/sync-models", this::syncModels, builder -> builder
                        .operationId("syncAiProviderModels")
                        .description("Synchronize local models with the provider without deleting local models.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .response(responseBuilder().implementation(SyncResult.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return modelSyncService
                .list(request.pathVariable("name"))
                .flatMap(models -> ServerResponse.ok().bodyValue(models));
    }

    private Mono<ServerResponse> syncModels(ServerRequest request) {
        return modelSyncService
                .sync(request.pathVariable("name"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description);
    }
}
