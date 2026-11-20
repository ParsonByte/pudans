package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.provider.preset.ProviderPresetCatalog;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiProviderPresetEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private final ProviderPresetCatalog presetCatalog;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiproviders/-/presets", this::presets, builder -> builder
                        .operationId("listAiProviderPresets")
                        .description("List backend-owned provider presets for the provider picker.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(ProviderPresetCatalog.Catalog.class)))
                .build();
    }

    private Mono<ServerResponse> presets(ServerRequest request) {
        return Mono.just(presetCatalog.catalog())
                .flatMap(catalog -> ServerResponse.ok().bodyValue(catalog));
    }
}