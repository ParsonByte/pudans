package cn.tolim.pudans.core.ai.config.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.ai.resource.AiChatMode;
import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.config.ChatModes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiChatModeEndpoint implements CustomEndpoint {

    private static final String TAG = "AiChatModeV1alpha1";

    private final ChatModes chatModeService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/chats/-/modes", this::list, builder -> builder
                        .operationId("listAiChatModes")
                        .description("List enabled chat modes available to the current user.")
                        .tag(TAG)
                        .response(responseBuilder().implementationArray(ChatModeView.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return chatModeService
                .available()
                .map(ChatModeView::from)
                .collectList()
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    public record ChatModeView(
            String name,
            @Nullable String displayName,
            @Nullable String routeGroup,
            AiModel.Tier defaultTier,
            List<String> kbCodes) {

        static ChatModeView from(AiChatMode mode) {
            var spec = mode.getSpec();
            return new ChatModeView(
                    mode.getMetadata().getName(),
                    spec == null ? null : spec.getDisplayName(),
                    spec == null ? null : spec.getRouteGroup(),
                    spec == null ? null : spec.getDefaultTier(),
                    spec == null || spec.getKnowledgeBaseCodes() == null
                            ? List.of()
                            : List.copyOf(spec.getKnowledgeBaseCodes()));
        }
    }
}
