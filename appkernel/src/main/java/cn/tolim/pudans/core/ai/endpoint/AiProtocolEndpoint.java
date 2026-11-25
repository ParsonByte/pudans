package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.ai.dto.AiProtocolDescriptor;
import cn.tolim.pudans.core.ai.protocol.AiProtocols;
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
public class AiProtocolEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProtocolV1alpha1";

    private final AiProtocols protocols;

    // 注册 AI 协议能力路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiprotocols", this::list, builder -> builder.operationId("listAiProtocols")
                        .description("List AI protocol descriptors and runtime support.")
                        .tag(TAG)
                        .response(responseBuilder()
                                .responseCode("200")
                                .description("AI protocols")
                                .implementationArray(AiProtocolDescriptor.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok()
                .body(
                        protocols.list().stream()
                                .map(descriptor -> new AiProtocolDescriptor(
                                        descriptor.protocol().value(),
                                        descriptor.protocol().displayName(),
                                        descriptor.description(),
                                        descriptor.requestPath(),
                                        descriptor.runtimeSupported()))
                                .toList(),
                        AiProtocolDescriptor.class);
    }
}
