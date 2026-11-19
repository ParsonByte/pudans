package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.GenericModelsClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProtocol;
import cn.tolim.pudans.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
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

    private final ReactiveExtensionClient client;

    private final AiProviderAccountService accountService;

    private final DeepSeekModelsClient deepSeekModelsClient;

    private final GenericModelsClient genericModelsClient;

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
        var name = request.pathVariable("name");
        return client.get(AiProvider.class, name)
                .flatMap(provider -> {
                    var spec = provider.getSpec();
                    if (spec == null || !spec.isEnabled()) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_DISABLED, "供应商不存在或已禁用。"));
                    }
                    return accountService
                            .firstEnabled(name)
                            .switchIfEmpty(Mono.error(new ApiException(
                                    HttpStatus.SERVICE_UNAVAILABLE,
                                    CODE_NO_ACCOUNT,
                                    "供应商没有可用账号。")))
                            .flatMap(account -> {
                                var protocol = spec.getProtocol();
                                if (DeepSeekProtocol.isDeepSeek(protocol)) {
                                    return deepSeekModelsClient.list(provider, account);
                                }
                                if (GenericModelsClient.supports(protocol)) {
                                    return genericModelsClient.list(provider, account);
                                }
                                return Mono.error(new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        CODE_UNSUPPORTED,
                                        "该供应商不支持获取模型列表。"));
                            });
                })
                .flatMap(models -> ServerResponse.ok().bodyValue(models));
    }

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description);
    }
}
