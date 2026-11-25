package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiProviderEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private final AiProviders providers;

    // 注册内置 AI 供应商目录路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiproviders", this::list, builder -> builder.operationId("listAiProviders")
                        .description("List built-in AI providers, protocols and default endpoints.")
                        .tag(TAG)
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("AI providers")
                                .implementationArray(AiProviderDescriptor.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(providers.list(), AiProviderDescriptor.class);
    }
}
