package cn.tolim.pudans.core.ai.memory.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.resource.AiMemoryAsset;
import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.memory.asset.MemoryLayers;
import cn.tolim.pudans.core.ai.memory.asset.MemoryAssets;
import cn.tolim.pudans.core.ai.memory.asset.MemoryAssets.ImportCommand;
import cn.tolim.pudans.core.ai.memory.asset.MemoryAssets.ImportMessage;
import cn.tolim.pudans.core.ai.memory.asset.MemoryAssetView;
import cn.tolim.pudans.core.ai.memory.asset.MemoryL1Item;
import cn.tolim.pudans.core.ai.memory.asset.MemoryL2Detail;
import cn.tolim.pudans.core.ai.memory.asset.MemoryL2Item;
import cn.tolim.pudans.core.ai.memory.asset.MemoryL3Content;
import cn.tolim.pudans.core.ai.memory.asset.MemoryLayerCounts;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository.LogEntry;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryAssetEndpoint implements CustomEndpoint {

    private static final String TAG = "AiMemoryAssetV1alpha1";

    private static final int MAX_LIMIT = 200;

    private final MemoryAssets assetService;

    private final MemoryLayers layerService;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aimemoryassets", this::list, builder -> builder
                        .operationId("listAiMemoryAssets")
                        .description("List memory assets including synthesized scopes.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("scope").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("agent").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("mine").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(MemoryAssetView.class)))
                .POST("/aimemoryassets", this::create, builder -> builder
                        .operationId("createAiMemoryAsset")
                        .description("Create a memory asset pointing at an existing memory scope.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(CreateRequest.class))
                        .response(responseBuilder().implementation(MemoryAssetView.class)))
                .GET("/aimemoryassets/{name}", this::detail, builder -> builder
                        .operationId("getAiMemoryAsset")
                        .description("Get a memory asset with layer counts.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .response(responseBuilder().implementation(MemoryAssetView.class)))
                .PATCH("/aimemoryassets/{name}", this::patch, builder -> builder
                        .operationId("patchAiMemoryAsset")
                        .description("Update a memory asset owned by the current user.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(true).implementation(PatchRequest.class))
                        .response(responseBuilder().implementation(MemoryAssetView.class)))
                .DELETE("/aimemoryassets/{name}", this::delete, builder -> builder
                        .operationId("deleteAiMemoryAsset")
                        .description("Remove a memory asset; only clear=true also clears its memory content.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("clear").required(false))
                        .response(responseBuilder().implementation(MemoryLayerCounts.class)))
                .GET("/aimemoryassets/{name}/counts", this::counts, builder -> builder
                        .operationId("getAiMemoryAssetCounts")
                        .description("Get L0 to L3 counts for a memory asset.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .response(responseBuilder().implementation(MemoryLayerCounts.class)))
                .GET("/aimemoryassets/{name}/layers/{layer}", this::layers, builder -> builder
                        .operationId("listAiMemoryAssetLayer")
                        .description("List L0, L1 or L2 records, or read the L3 persona.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("layer").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("from").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("to").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("path").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("q").required(false))
                        .response(responseBuilder().implementationArray(Object.class)))
                .PATCH("/aimemoryassets/{name}/layers/L1/{id}", this::updateL1, builder -> builder
                        .operationId("updateAiMemoryAssetL1")
                        .description("Update one L1 memory record content.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("id").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(true).implementation(ContentRequest.class))
                        .response(responseBuilder().implementation(MemoryL1Item.class)))
                .PATCH("/aimemoryassets/{name}/layers/L2/{id}", this::updateL2, builder -> builder
                        .operationId("updateAiMemoryAssetL2")
                        .description("Update one L2 scene content and optional summary.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("id").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(true).implementation(L2UpdateRequest.class))
                        .response(responseBuilder().implementation(MemoryL2Detail.class)))
                .PATCH("/aimemoryassets/{name}/layers/L3", this::updateL3, builder -> builder
                        .operationId("updateAiMemoryAssetL3")
                        .description("Overwrite the L3 persona text.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(true).implementation(ContentRequest.class))
                        .response(responseBuilder().implementation(MemoryL3Content.class)))
                .DELETE("/aimemoryassets/{name}/layers/L0", this::deleteL0, builder -> builder
                        .operationId("deleteAiMemoryAssetL0")
                        .description("Batch soft delete L0 messages in the asset scope.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(false).implementation(L0DeleteRequest.class))
                        .response(responseBuilder().implementation(Long.class)))
                .DELETE("/aimemoryassets/{name}/layers/L1", this::deleteL1, builder -> builder
                        .operationId("deleteAiMemoryAssetL1")
                        .description("Batch delete L1 records in the asset scope.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(false).implementation(L1DeleteRequest.class))
                        .response(responseBuilder().implementation(Long.class)))
                .POST("/aimemoryassets/{name}/clear", this::clear, builder -> builder
                        .operationId("clearAiMemoryAsset")
                        .description("Clear all L0 to L3 memory data in the asset scope.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .response(responseBuilder().implementation(MemoryLayerCounts.class)))
                .POST("/aimemoryassets/{name}/bindings", this::bind, builder -> builder
                        .operationId("bindAiMemoryAsset")
                        .description("Bind an asset to an agent, with at most two assets per agent.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(true).implementation(BindingRequest.class))
                        .response(responseBuilder().implementation(MemoryAssetView.class)))
                .DELETE("/aimemoryassets/{name}/bindings/{agent}", this::unbind, builder -> builder
                        .operationId("unbindAiMemoryAsset")
                        .description("Remove an agent binding from a memory asset.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("agent").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .response(responseBuilder().implementation(MemoryAssetView.class)))
                .POST("/aimemoryassets/{name}/import", this::importMessages, builder -> builder
                        .operationId("importAiMemoryAsset")
                        .description("Import up to 100 user or assistant messages into L0 and the L1 pipeline.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .requestBody(requestBodyBuilder().required(true).implementation(ImportRequest.class))
                        .response(responseBuilder().implementation(MemoryAssets.ImportResult.class)))
                .GET("/aimemoryassets/{name}/generation-logs", this::generationLogs, builder -> builder
                        .operationId("listAiMemoryAssetGenerationLogs")
                        .description("List memory generation logs for the asset scope.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("layer").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("from").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("to").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(LogEntry.class)))
                .GET("/aimemorygenerationlogs/{id}", this::generationLog, builder -> builder
                        .operationId("getAiMemoryGenerationLog")
                        .description("Get one memory generation log entry.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("id").required(true))
                        .response(responseBuilder().implementation(LogEntry.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", 50, 1, MAX_LIMIT);
        var scope = query(request, "scope");
        if (scope != null && !"team".equals(scope) && !"fixed".equals(scope)) {
            throw invalid("scope 必须是 team 或 fixed。");
        }
        return requests
                .userId(request)
                .flatMap(userId -> assetService.list(
                        userId,
                        query(request, "team"),
                        scope,
                        query(request, "agent"),
                        booleanParam(request, "mine"),
                        limit))
                .flatMap(assets -> ServerResponse.ok().bodyValue(assets));
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request
                .bodyToMono(CreateRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> {
                    var spec = body.resolveSpec();
                    var metadata = body.metadata();
                    return requests.userId(request).flatMap(userId -> assetService.create(
                            userId,
                            new MemoryAssets.CreateCommand(
                                    metadata == null ? body.name() : metadata.getName(),
                                    metadata == null ? body.generateName() : metadata.getGenerateName(),
                                    spec.getDisplayName(),
                                    spec.getDescription(),
                                    spec.getTeam(),
                                    spec.getOwnerUserId(),
                                    spec.getSourceUserId(),
                                    spec.getSourceAgentId(),
                                    spec.getVisibility(),
                                    spec.getStatus(),
                                    spec.getAgents())));
                })
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> detail(ServerRequest request) {
        var name = request.pathVariable("name");
        return requests
                .userId(request)
                .flatMap(userId -> assetService.detail(userId, name, query(request, "team")))
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> patch(ServerRequest request) {
        var name = request.pathVariable("name");
        return request
                .bodyToMono(PatchRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId -> assetService.patch(
                        userId,
                        name,
                        query(request, "team"),
                        new MemoryAssets.PatchCommand(
                                body.displayName(),
                                body.description(),
                                body.visibility(),
                                body.status(),
                                body.agents()))))
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        var name = request.pathVariable("name");
        var clear = booleanParam(request, "clear");
        return requests
                .userId(request)
                .flatMap(userId -> assetService.delete(userId, name, query(request, "team"), clear))
                .flatMap(counts -> ServerResponse.ok().bodyValue(counts));
    }

    private Mono<ServerResponse> counts(ServerRequest request) {
        var name = request.pathVariable("name");
        return requests
                .userId(request)
                .flatMap(userId -> assetService.layerCounts(userId, name, query(request, "team")))
                .flatMap(counts -> ServerResponse.ok().bodyValue(counts));
    }

    private Mono<ServerResponse> layers(ServerRequest request) {
        var name = request.pathVariable("name");
        var layer = layer(request.pathVariable("layer"));
        var team = query(request, "team");
        var beforeId = AiEndpointRequests.longParam(request, "beforeId", Long.MAX_VALUE, 1, Long.MAX_VALUE);
        var limit = AiEndpointRequests.intParam(request, "limit", 20, 1, MAX_LIMIT);
        var from = instantParam(request, "from");
        var to = instantParam(request, "to");
        var keyword = query(request, "q");
        return requests.userId(request).flatMap(userId -> switch (layer) {
            case "L0" -> layerService
                    .listL0(userId, name, team, beforeId, from, to, keyword, limit)
                    .collectList()
                    .flatMap(items -> ServerResponse.ok().bodyValue(items));
            case "L1" -> layerService
                    .listL1(userId, name, team, beforeId, from, to, keyword, limit)
                    .collectList()
                    .flatMap(items -> ServerResponse.ok().bodyValue(items));
            case "L2" -> {
                var path = query(request, "path");
                yield path == null
                        ? layerService
                                .listL2(userId, name, team, beforeId, limit)
                                .collectList()
                                .flatMap(items -> ServerResponse.ok().bodyValue(items))
                        : layerService
                                .detailL2(userId, name, team, path)
                                .flatMap(item -> ServerResponse.ok().bodyValue(item));
            }
            case "L3" -> layerService
                    .detailL3(userId, name, team)
                    .flatMap(persona -> ServerResponse.ok().bodyValue(persona));
            default -> Mono.error(invalid("layer 必须是 L0、L1、L2 或 L3。"));
        });
    }

    private Mono<ServerResponse> updateL1(ServerRequest request) {
        var name = request.pathVariable("name");
        var id = longPathVariable(request, "id");
        return request
                .bodyToMono(ContentRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId ->
                        layerService.updateL1(userId, name, query(request, "team"), id, body.content())))
                .flatMap(item -> ServerResponse.ok().bodyValue(item));
    }

    private Mono<ServerResponse> updateL2(ServerRequest request) {
        var name = request.pathVariable("name");
        var id = request.pathVariable("id");
        return request
                .bodyToMono(L2UpdateRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId -> layerService.updateL2(
                        userId, name, query(request, "team"), id, body.path(), body.content(), body.summary())))
                .flatMap(item -> ServerResponse.ok().bodyValue(item));
    }

    private Mono<ServerResponse> updateL3(ServerRequest request) {
        var name = request.pathVariable("name");
        return request
                .bodyToMono(ContentRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId ->
                        layerService.updateL3(userId, name, query(request, "team"), body.content())))
                .flatMap(persona -> ServerResponse.ok().bodyValue(persona));
    }

    private Mono<ServerResponse> deleteL0(ServerRequest request) {
        var name = request.pathVariable("name");
        return request
                .bodyToMono(L0DeleteRequest.class)
                .defaultIfEmpty(new L0DeleteRequest(null, null))
                .flatMap(body -> requests.userId(request).flatMap(userId -> layerService.deleteL0(
                        userId, name, query(request, "team"), body.messageIds(), body.sessionIds())))
                .flatMap(count -> ServerResponse.ok().bodyValue(count));
    }

    private Mono<ServerResponse> deleteL1(ServerRequest request) {
        var name = request.pathVariable("name");
        return request
                .bodyToMono(L1DeleteRequest.class)
                .defaultIfEmpty(new L1DeleteRequest(null))
                .flatMap(body -> requests.userId(request).flatMap(userId ->
                        layerService.deleteL1(userId, name, query(request, "team"), body.ids())))
                .flatMap(count -> ServerResponse.ok().bodyValue(count));
    }

    private Mono<ServerResponse> clear(ServerRequest request) {
        var name = request.pathVariable("name");
        return requests
                .userId(request)
                .flatMap(userId -> assetService.clear(userId, name, query(request, "team")))
                .flatMap(counts -> ServerResponse.ok().bodyValue(counts));
    }

    private Mono<ServerResponse> bind(ServerRequest request) {
        var name = request.pathVariable("name");
        return request
                .bodyToMono(BindingRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId ->
                        assetService.bind(userId, name, query(request, "team"), body.agent())))
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> unbind(ServerRequest request) {
        var name = request.pathVariable("name");
        var agent = request.pathVariable("agent");
        return requests
                .userId(request)
                .flatMap(userId -> assetService.unbind(userId, name, query(request, "team"), agent))
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> importMessages(ServerRequest request) {
        var name = request.pathVariable("name");
        return request
                .bodyToMono(ImportRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests.userId(request).flatMap(userId -> assetService.importMessages(
                        userId,
                        name,
                        query(request, "team"),
                        new ImportCommand(body.messages(), body.sessionId()))))
                .flatMap(result -> ServerResponse.accepted().bodyValue(result));
    }

    private Mono<ServerResponse> generationLogs(ServerRequest request) {
        var name = request.pathVariable("name");
        var layer = optionalPromptLayer(query(request, "layer"));
        var status = query(request, "status");
        var from = instantParam(request, "from");
        var to = instantParam(request, "to");
        var beforeId = AiEndpointRequests.longParam(request, "beforeId", Long.MAX_VALUE, 1, Long.MAX_VALUE);
        var limit = AiEndpointRequests.intParam(request, "limit", 50, 1, MAX_LIMIT);
        return requests
                .userId(request)
                .flatMap(userId -> ServerResponse.ok()
                        .body(
                                assetService.generationLogs(
                                        userId, name, query(request, "team"), layer, status, from, to, beforeId, limit),
                                LogEntry.class));
    }

    private Mono<ServerResponse> generationLog(ServerRequest request) {
        var id = longPathVariable(request, "id");
        return requests
                .userId(request)
                .flatMap(userId -> assetService.generationLog(userId, id))
                .flatMap(entry -> ServerResponse.ok().bodyValue(entry));
    }

    private static String layer(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid("layer 不能为空。");
        }
        var normalized = value.toUpperCase(Locale.ROOT);
        if (!List.of("L0", "L1", "L2", "L3").contains(normalized)) {
            throw invalid("layer 必须是 L0、L1、L2 或 L3。");
        }
        return normalized;
    }

    private static AiMemoryPrompt.@Nullable Layer optionalPromptLayer(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return AiMemoryPrompt.Layer.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalid("layer 必须是 L1、L2 或 L3。");
        }
    }

    private static long longPathVariable(ServerRequest request, String name) {
        var value = request.pathVariable(name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw invalid(name + " 必须是整数。");
        }
    }

    private static boolean booleanParam(ServerRequest request, String name) {
        return request
                .queryParam(name)
                .filter(StringUtils::hasText)
                .map(value -> "true".equalsIgnoreCase(value) || "1".equals(value))
                .orElse(false);
    }

    private static @Nullable String query(ServerRequest request, String name) {
        return request.queryParam(name).filter(StringUtils::hasText).orElse(null);
    }

    private static @Nullable Instant instantParam(ServerRequest request, String name) {
        var value = query(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw invalid(name + " 时间格式不正确。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, MemoryAssets.CODE_INVALID, message);
    }

    public record CreateRequest(
            @Nullable Metadata metadata,
            @Nullable String name,
            @Nullable String generateName,
            AiMemoryAsset.@Nullable Spec spec,
            @Nullable String displayName,
            @Nullable String description,
            @Nullable String team,
            @Nullable String ownerUserId,
            @Nullable String sourceUserId,
            @Nullable String sourceAgentId,
            AiMemoryAsset.@Nullable Visibility visibility,
            AiMemoryAsset.@Nullable Status status,
            @Nullable List<String> agents) {

        AiMemoryAsset.Spec resolveSpec() {
            if (spec != null) {
                return spec;
            }
            var resolved = new AiMemoryAsset.Spec();
            resolved.setDisplayName(displayName);
            resolved.setDescription(description);
            resolved.setTeam(team);
            resolved.setOwnerUserId(ownerUserId);
            resolved.setSourceUserId(sourceUserId);
            resolved.setSourceAgentId(sourceAgentId);
            resolved.setVisibility(visibility);
            resolved.setStatus(status);
            resolved.setAgents(agents);
            return resolved;
        }
    }

    public record PatchRequest(
            @Nullable String displayName,
            @Nullable String description,
            AiMemoryAsset.@Nullable Visibility visibility,
            AiMemoryAsset.@Nullable Status status,
            @Nullable List<String> agents) {}

    public record ContentRequest(@Nullable String content) {}

    public record L2UpdateRequest(
            @Nullable String content, @Nullable String summary, @Nullable String path) {}

    public record L0DeleteRequest(@Nullable List<Long> messageIds, @Nullable List<String> sessionIds) {}

    public record L1DeleteRequest(@Nullable List<Long> ids) {}

    public record BindingRequest(@Nullable String agent) {}

    public record ImportRequest(
            @Nullable List<ImportMessage> messages, @Nullable String sessionId) {}
}