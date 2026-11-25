package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.ai.dto.AiChatCatalog;
import cn.tolim.pudans.contract.ai.dto.AiCreationCatalog;
import cn.tolim.pudans.contract.ai.dto.AiMemoryCatalog;
import cn.tolim.pudans.contract.ai.dto.AiModelCatalogItem;
import cn.tolim.pudans.contract.ai.dto.AiSandboxCatalog;
import cn.tolim.pudans.contract.ai.dto.AiSkillDescriptor;
import cn.tolim.pudans.contract.ai.dto.AiToolDescriptor;
import cn.tolim.pudans.contract.ai.dto.AiUnderstandingCatalog;
import cn.tolim.pudans.core.ai.catalog.AiCatalogs;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiCatalogEndpoint implements CustomEndpoint {

    private static final String TAG = "AiCatalogV1alpha1";

    private final AiCatalogs catalogs;

    private final ReactiveRequestIdentity identity;

    // 注册模型、创作、对话、工具、技能、理解、记忆和沙箱目录。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/catalogs/-/models", this::models, builder -> builder.operationId("listAiCatalogModels")
                        .description("List active models with a healthy channel in the current workspace.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Available models")))
                .GET("/catalogs/-/creation", this::creation, builder -> builder.operationId("getAiCreationCatalog")
                        .description("Get creation media modes, operations and model parameter schemas.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiCreationCatalog.class)))
                .GET("/catalogs/-/chat", this::chat, builder -> builder.operationId("getAiChatCatalog")
                        .description("Get chat modes, reasoning levels, available models and tools.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiChatCatalog.class)))
                .GET("/catalogs/-/tools", this::tools, builder -> builder.operationId("listAiCatalogTools")
                        .description("List tools registered by the running capability registry.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Available tools")))
                .GET("/catalogs/-/skills", this::skills, builder -> builder.operationId("listAiCatalogSkills")
                        .description("List skills registered by the running capability registry.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Available skills")))
                .GET("/catalogs/-/understanding", this::understanding, builder -> builder.operationId("getAiUnderstandingCatalog")
                        .description("Get dynamic understanding dimensions.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiUnderstandingCatalog.class)))
                .GET("/catalogs/-/memory", this::memory, builder -> builder.operationId("getAiMemoryCatalog")
                        .description("Get dynamic memory types and layers.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiMemoryCatalog.class)))
                .GET("/catalogs/-/sandbox", this::sandbox, builder -> builder.operationId("getAiSandboxCatalog")
                        .description("Get dynamic sandbox providers, templates and resource profiles.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiSandboxCatalog.class)))
                .build();
    }

    private Mono<ServerResponse> models(ServerRequest request) {
        return workspace(request).flatMap(workspaceId -> ServerResponse.ok()
                .body(catalogs.models(workspaceId), AiModelCatalogItem.class));
    }

    private Mono<ServerResponse> creation(ServerRequest request) {
        return workspace(request)
                .flatMap(catalogs::creation)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> chat(ServerRequest request) {
        return workspace(request)
                .flatMap(catalogs::chat)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> tools(ServerRequest request) {
        return workspace(request).flatMap(ignored -> ServerResponse.ok()
                .body(catalogs.tools(), AiToolDescriptor.class));
    }

    private Mono<ServerResponse> skills(ServerRequest request) {
        return workspace(request).flatMap(ignored -> ServerResponse.ok()
                .body(catalogs.skills(), AiSkillDescriptor.class));
    }

    private Mono<ServerResponse> understanding(ServerRequest request) {
        return workspace(request)
                .flatMap(ignored -> catalogs.understanding())
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> memory(ServerRequest request) {
        return workspace(request)
                .flatMap(ignored -> catalogs.memory())
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> sandbox(ServerRequest request) {
        return workspace(request)
                .flatMap(ignored -> catalogs.sandbox())
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<String> workspace(ServerRequest request) {
        return identity.resolve(request).map(owner -> owner.workspaceId());
    }
}
