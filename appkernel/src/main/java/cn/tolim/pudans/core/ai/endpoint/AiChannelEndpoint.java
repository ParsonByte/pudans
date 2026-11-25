package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiChannel;
import cn.tolim.pudans.contract.ai.dto.AiChannelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiChannelTestResult;
import cn.tolim.pudans.contract.ai.dto.AiChannelUpdateRequest;
import cn.tolim.pudans.contract.ai.dto.AiModelDiscovery;
import cn.tolim.pudans.contract.ai.dto.AiModelImportRequest;
import cn.tolim.pudans.contract.ai.dto.AiModelImportResult;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiModelDiscoveries;
import cn.tolim.pudans.core.ai.provider.AiModelImports;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiChannelEndpoint implements CustomEndpoint {

    private static final String TAG = "AiChannelV1alpha1";

    private final AiChannels channels;

    private final AiModelDiscoveries discoveries;

    private final AiModelImports imports;

    private final ReactiveRequestIdentity identity;

    // 注册渠道列表、详情、创建、更新、删除和测试路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aichannels", this::list, builder -> builder.operationId("listAiChannels")
                        .description("List AI channels in the current workspace without credentials.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("AI channels")))
                .POST("/aichannels", this::create, builder -> builder.operationId("createAiChannel")
                        .description("Create an AI channel with write-only credentials.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiChannelCreateRequest.class))))
                        .response(responseBuilder().implementation(AiChannel.class)))
                .GET("/aichannels/{name}", this::get, builder -> builder.operationId("getAiChannel")
                        .description("Get an AI channel without credentials.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiChannel.class)))
                .PATCH("/aichannels/{name}", this::update, builder -> builder.operationId("updateAiChannel")
                        .description("Update an AI channel; empty credentials keep the stored value.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiChannelUpdateRequest.class))))
                        .response(responseBuilder().implementation(AiChannel.class)))
                .DELETE("/aichannels/{name}", this::delete, builder -> builder.operationId("deleteAiChannel")
                        .description("Delete an AI channel that is not referenced by a model mapping.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("AI channel deleted")))
                .POST("/aichannels/{name}/-/test", this::test, builder -> builder.operationId("testAiChannel")
                        .description("Test an AI channel with its resolved credentials.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiChannelTestResult.class)))
                .GET("/aichannels/{name}/-/models", this::discover, builder -> builder
                        .operationId("discoverAiChannelModels")
                        .description("Discover upstream model identifiers returned by the provider.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiModelDiscovery.class)))
                .POST("/aichannels/{name}/-/models", this::importModels, builder -> builder
                        .operationId("importAiChannelModels")
                        .description("Import selected discovered models and bind them to the channel.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(false)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiModelImportRequest.class))))
                        .response(responseBuilder().implementation(AiModelImportResult.class)))
                .build();
    }

    // 返回当前工作区的渠道列表。
    private Mono<ServerResponse> list(ServerRequest request) {
        return identity.resolve(request).flatMap(owner -> ServerResponse.ok()
                .body(channels.list(owner.workspaceId()), AiChannel.class));
    }

    // 创建渠道并返回不含凭据的资源。
    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(AiChannelCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request)
                        .flatMap(owner -> channels.create(owner.workspaceId(), body)))
                .flatMap(channel -> ServerResponse.status(HttpStatus.CREATED).bodyValue(channel));
    }

    // 按标识返回当前工作区的渠道。
    private Mono<ServerResponse> get(ServerRequest request) {
        var id = AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-channel-id-invalid", "渠道标识不是有效 UUID。");
        return identity.resolve(request)
                .flatMap(owner -> channels.get(owner.workspaceId(), id))
                .flatMap(channel -> ServerResponse.ok().bodyValue(channel));
    }

    // 更新渠道，保留未提交的加密凭据。
    private Mono<ServerResponse> update(ServerRequest request) {
        var id = AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-channel-id-invalid", "渠道标识不是有效 UUID。");
        return request.bodyToMono(AiChannelUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> identity.resolve(request)
                        .flatMap(owner -> channels.update(owner.workspaceId(), id, body)))
                .flatMap(channel -> ServerResponse.ok().bodyValue(channel));
    }

    // 删除未被模型映射引用的渠道。
    private Mono<ServerResponse> delete(ServerRequest request) {
        var id = AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-channel-id-invalid", "渠道标识不是有效 UUID。");
        return identity.resolve(request)
                .flatMap(owner -> channels.delete(owner.workspaceId(), id))
                .then(ServerResponse.noContent().build());
    }

    // 解析凭据并执行真实渠道健康检查。
    private Mono<ServerResponse> test(ServerRequest request) {
        var id = AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-channel-id-invalid", "渠道标识不是有效 UUID。");
        return identity.resolve(request)
                .flatMap(owner -> channels.test(owner.workspaceId(), id))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 使用真实渠道凭据发现并补齐模型元数据。
    private Mono<ServerResponse> discover(ServerRequest request) {
        var id = AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-channel-id-invalid", "渠道标识不是有效 UUID。");
        return identity.resolve(request)
                .flatMap(owner -> discoveries.discover(owner.workspaceId(), id))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 将选中的真实上游模型导入为逻辑模型和渠道路由。
    private Mono<ServerResponse> importModels(ServerRequest request) {
        var id = AiEndpointSupport.uuid(
                request.pathVariable("name"), "ai-channel-id-invalid", "渠道标识不是有效 UUID。");
        return request.bodyToMono(AiModelImportRequest.class)
                .defaultIfEmpty(new AiModelImportRequest(java.util.List.of()))
                .flatMap(body -> identity.resolve(request)
                        .flatMap(owner -> imports.importModels(owner.workspaceId(), id, body)))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }
}
