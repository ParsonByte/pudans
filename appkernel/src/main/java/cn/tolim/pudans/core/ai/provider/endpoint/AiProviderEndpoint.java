package cn.tolim.pudans.core.ai.provider.endpoint;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.MetadataOperator;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChat;
import cn.tolim.pudans.core.ai.rerank.Reranking;
import cn.tolim.pudans.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private static final String CODE_TEST_FAILED = "ai-provider-test-failed";

    private static final String CODE_QUOTA_INVALID = "ai-provider-quota-invalid";

    private static final String TEST_PROMPT = "Reply with OK.";

    private static final List<AiModel.Capability> SYNC_TEST_CAPABILITIES = List.of(
            AiModel.Capability.IMAGE_GENERATION,
            AiModel.Capability.SPEECH_SYNTHESIS,
            AiModel.Capability.RERANK);

    private final ReactiveExtensionClient client;

    private final LangChain4jChat chatService;

    private final AiModelAdapterRegistry modelAdapters;

    private final Embeddings embeddingService;

    private final Reranking rerankService;

    private final ProviderAccounts accountService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiproviders/{name}/test", this::testProvider, builder -> builder
                        .operationId("testAiProvider")
                        .description("Test provider connectivity with its first enabled account.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .response(responseBuilder().implementation(TestResult.class)))
                .POST("/aimodels/{name}/test", this::testModel, builder -> builder
                        .operationId("testAiModel")
                        .description("Test a model with its provider's first enabled account.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .response(responseBuilder().implementation(TestResult.class)))
                .POST("/aiprovideraccounts/{name}/test", this::testAccount, builder -> builder
                        .operationId("testAiProviderAccount")
                        .description("Test a provider account with the first enabled chat model.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .response(responseBuilder().implementation(TestResult.class)))
                .POST("/aiprovideraccounts/{name}/quota", this::updateQuota, builder -> builder
                        .operationId("updateAiProviderAccountQuota")
                        .description("Update quotas, quota windows and balance for a provider account.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .requestBody(requestBodyBuilder().required(true).implementation(QuotaUpdate.class))
                        .response(responseBuilder().implementation(AiProviderAccount.class)))
                .POST("/aiproviders/-/arrange", this::arrange, builder -> builder
                        .operationId("arrangeAiProviders")
                        .description("Write provider order according to the given names.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(ArrangeRequest.class))
                        .response(responseBuilder()))
                .POST("/aiproviders/{name}/actions/duplicate", this::duplicate, builder -> builder
                        .operationId("duplicateAiProvider")
                        .description("Duplicate a provider together with its accounts and models.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .requestBody(requestBodyBuilder().required(true).implementation(DuplicateRequest.class))
                        .response(responseBuilder().implementation(AiProvider.class)))
                .build();
    }

    private Mono<ServerResponse> testProvider(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.get(AiProvider.class, name)
                .flatMap(provider -> firstAccount(name)
                        .switchIfEmpty(Mono.error(testFailed("供应商没有可用账号。")))
                        .flatMap(account -> firstChatModel(name)
                                .switchIfEmpty(Mono.error(testFailed("供应商没有可用对话模型。")))
                                .flatMap(model -> test(provider, account, model))))
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorMap(error -> !(error instanceof ApiException), this::testError);
    }

    private Mono<ServerResponse> testModel(ServerRequest request) {
        return client.get(AiModel.class, request.pathVariable("name"))
                .flatMap(model -> {
                    if (model.getSpec() == null) {
                        return Mono.error(testFailed("模型配置不能为空。"));
                    }
                    if (model.getSpec().getModelType() == AiModel.ModelType.EMBEDDING) {
                        return client.get(AiProvider.class, model.getSpec().getProvider())
                                .flatMap(provider -> firstAccount(provider.getMetadata().getName())
                                        .switchIfEmpty(Mono.error(testFailed("供应商没有可用账号。")))
                                        .flatMap(account -> testEmbedding(provider, account, model)));
                    }
                    if (model.getSpec().getModelType() == AiModel.ModelType.CHAT) {
                        return client.get(AiProvider.class, model.getSpec().getProvider())
                                .flatMap(provider -> firstAccount(provider.getMetadata().getName())
                                        .switchIfEmpty(Mono.error(testFailed("供应商没有可用账号。")))
                                        .flatMap(account -> test(provider, account, model)));
                    }
                    return client.get(AiProvider.class, model.getSpec().getProvider())
                            .flatMap(provider -> firstAccount(provider.getMetadata().getName())
                                    .switchIfEmpty(Mono.error(testFailed("供应商没有可用账号。")))
                                    .flatMap(account -> testCapability(provider, account, model)));
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorMap(error -> !(error instanceof ApiException), this::testError);
    }

    private Mono<ServerResponse> testAccount(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.get(AiProviderAccount.class, name)
                .filter(account -> account.getSpec() != null && account.getSpec().isEnabled())
                .switchIfEmpty(Mono.error(testFailed("账号不存在或已禁用。")))
                .flatMap(account -> client.get(AiProvider.class, account.getSpec().getProvider())
                        .flatMap(provider -> firstChatModel(provider.getMetadata().getName())
                                .switchIfEmpty(Mono.error(testFailed("供应商没有可用对话模型。")))
                                .flatMap(model -> test(provider, account, model))))
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorMap(error -> !(error instanceof ApiException), this::testError);
    }

    private Mono<ServerResponse> updateQuota(ServerRequest request) {
        return request
                .bodyToMono(QuotaUpdate.class)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_QUOTA_INVALID, "请求体不能为空。")))
                .flatMap(update -> {
                    if (update.quota() == null && update.windows() == null && update.balance() == null) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_QUOTA_INVALID, "至少提供一个更新字段。"));
                    }
                    if (update.quota() != null
                            && update.quota().getLimit() != null
                            && update.quota().getLimit() < 0) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_QUOTA_INVALID, "配额上限不能为负数。"));
                    }
                    return client.get(AiProviderAccount.class, request.pathVariable("name"))
                            .flatMap(account -> {
                                var spec = account.getSpec();
                                if (spec == null) {
                                    spec = new AiProviderAccount.Spec();
                                    account.setSpec(spec);
                                }
                                if (update.quota() != null) {
                                    spec.setQuota(update.quota());
                                }
                                if (update.windows() != null) {
                                    spec.setWindows(update.windows());
                                }
                                if (update.balance() != null) {
                                    spec.setBalance(update.balance());
                                }
                                return client.update(account);
                            });
                })
                .flatMap(account -> ServerResponse.ok().bodyValue(account));
    }

    private Mono<ServerResponse> arrange(ServerRequest request) {
        return request
                .bodyToMono(ArrangeRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> {
                    var names = body.names() == null
                            ? List.<String>of()
                            : body.names().stream().filter(StringUtils::hasText).toList();
                    if (names.isEmpty() || new HashSet<>(names).size() != names.size()) {
                        return Mono.error(invalid("供应商名称列表不能为空且不得重复。"));
                    }
                    return Flux.fromIterable(names)
                            .index()
                            .concatMap(tuple -> client.get(AiProvider.class, tuple.getT2())
                                    .switchIfEmpty(Mono.error(notFound("供应商不存在：" + tuple.getT2() + "。")))
                                    .flatMap(provider -> {
                                        if (provider.getSpec() == null) {
                                            provider.setSpec(new AiProvider.Spec());
                                        }
                                        provider.getSpec().setOrder(tuple.getT1().intValue());
                                        return client.update(provider);
                                    }))
                            .collectList()
                            .flatMap(providers -> ServerResponse.ok().bodyValue(providers));
                });
    }

    private Mono<ServerResponse> duplicate(ServerRequest request) {
        return request
                .bodyToMono(DuplicateRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> {
                    if (!StringUtils.hasText(body.name())) {
                        return Mono.error(invalid("新供应商名称不能为空。"));
                    }
                    return client.get(AiProvider.class, request.pathVariable("name"))
                            .switchIfEmpty(Mono.error(notFound("供应商不存在。")))
                            .flatMap(source -> client.get(AiProvider.class, body.name())
                                    .flatMap(existing -> Mono.<AiProvider>error(conflict("供应商名称已存在。")))
                                    .switchIfEmpty(Mono.defer(() -> copyProvider(source, body))))
                            .flatMap(provider -> ServerResponse.ok().bodyValue(provider));
                });
    }

    private Mono<AiProvider> copyProvider(AiProvider source, DuplicateRequest request) {
        var copy = new AiProvider();
        copy.setMetadata(copyMetadata(source.getMetadata(), request.name()));
        copy.setSpec(source.getSpec() == null
                ? new AiProvider.Spec()
                : JsonUtils.deepCopy(source.getSpec()));
        copy.getSpec().setDisplayName(
                StringUtils.hasText(request.displayName())
                        ? request.displayName()
                        : source.getSpec() == null ? null : source.getSpec().getDisplayName());
        copy.setStatus(new AiProvider.Status());
        var sourceName = source.getMetadata().getName();
        return client.create(copy).flatMap(created -> copyAccounts(sourceName, request.name())
                .then(copyModels(sourceName, request.name()))
                .thenReturn(created));
    }

    private Mono<Void> copyAccounts(String sourceName, String providerName) {
        var options = ListOptions.builder().andQuery(equal("spec.provider", sourceName)).build();
        return client.listAll(AiProviderAccount.class, options, defaultSort())
                .index()
                .concatMap(tuple -> {
                    var source = tuple.getT2();
                    var copy = new AiProviderAccount();
                    copy.setMetadata(copyMetadata(
                            source.getMetadata(), providerName + "-account-" + (tuple.getT1() + 1)));
                    copy.setSpec(source.getSpec() == null
                            ? new AiProviderAccount.Spec()
                            : JsonUtils.deepCopy(source.getSpec()));
                    copy.getSpec().setProvider(providerName);
                    copy.getSpec().setApiKey(null);
                    copy.getSpec().setConfigured(false);
                    copy.setStatus(new AiProviderAccount.Status());
                    return client.create(copy);
                })
                .then();
    }

    private Mono<Void> copyModels(String sourceName, String providerName) {
        var options = ListOptions.builder().andQuery(equal("spec.provider", sourceName)).build();
        return client.listAll(AiModel.class, options, defaultSort())
                .index()
                .concatMap(tuple -> {
                    var source = tuple.getT2();
                    var copy = new AiModel();
                    copy.setMetadata(copyMetadata(
                            source.getMetadata(), providerName + "-model-" + (tuple.getT1() + 1)));
                    copy.setSpec(source.getSpec() == null
                            ? new AiModel.Spec()
                            : JsonUtils.deepCopy(source.getSpec()));
                    copy.getSpec().setProvider(providerName);
                    return client.create(copy);
                })
                .then();
    }

    private static Metadata copyMetadata(@Nullable MetadataOperator source, String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        if (source != null) {
            metadata.setLabels(source.getLabels() == null ? null : new HashMap<>(source.getLabels()));
            metadata.setAnnotations(
                    source.getAnnotations() == null ? null : new HashMap<>(source.getAnnotations()));
        }
        return metadata;
    }

    private Mono<TestResult> testEmbedding(AiProvider provider, AiProviderAccount account, AiModel model) {
        var started = Instant.now();
        return embeddingService
                .embed(List.of(TEST_PROMPT), model.getMetadata().getName())
                .map(batch -> new TestResult(
                        true,
                        Duration.between(started, Instant.now()).toMillis(),
                        "Embedding 连通性正常。",
                        batch.modelCode()))
                .flatMap(result -> markSuccess(provider, account, result))
                .onErrorResume(error -> markFailure(provider, account, error).then(Mono.error(error)));
    }

    private Mono<TestResult> test(AiProvider provider, AiProviderAccount account, AiModel model) {
        var started = Instant.now();
        return chatService
                .chat(provider, account, model, provider.getSpec().getProtocol(), testRequest())
                .map(response -> new TestResult(
                        true,
                        Duration.between(started, Instant.now()).toMillis(),
                        "连通性正常。",
                        response.model()))
                .flatMap(result -> markSuccess(provider, account, result))
                .onErrorResume(error -> markFailure(provider, account, error).then(Mono.error(error)));
    }

    private Mono<TestResult> testCapability(
            AiProvider provider, AiProviderAccount account, AiModel model) {
        var capability = syncTestCapability(model);
        if (capability == null) {
            return Mono.error(testFailed("该模型不支持同步连通性测试，请通过生成接口验证。"));
        }
        var protocol = provider.getSpec().getProtocol();
        var started = Instant.now();
        if (capability == AiModel.Capability.RERANK) {
            return rerankService
                    .rerank(
                            provider,
                            account,
                            model,
                            TEST_PROMPT,
                            List.of(TEST_PROMPT, TEST_PROMPT),
                            1)
                    .map(result -> new TestResult(
                            true,
                            Duration.between(started, Instant.now()).toMillis(),
                            "重排连通性正常。",
                            model.getSpec().getModelCode()))
                    .flatMap(result -> markSuccess(provider, account, result))
                    .onErrorResume(error -> markFailure(provider, account, error).then(Mono.error(error)));
        }
        if (!modelAdapters.supports(protocol, capability)) {
            return Mono.error(testFailed("供应商协议不支持模型能力：" + capability + "。"));
        }
        return modelAdapters
                .require(protocol, capability)
                .invoke(provider, account, model, testModelRequest(capability))
                .map(response -> new TestResult(
                        true,
                        Duration.between(started, Instant.now()).toMillis(),
                        "连通性正常。",
                        response.modelCode()))
                .flatMap(result -> markSuccess(provider, account, result))
                .onErrorResume(error -> markFailure(provider, account, error).then(Mono.error(error)));
    }

    private Mono<TestResult> markSuccess(
            AiProvider provider, @Nullable AiProviderAccount account, TestResult result) {
        var now = Instant.now();
        var status = provider.getStatus() == null ? new AiProvider.Status() : provider.getStatus();
        status.setHealthScore(100.0);
        status.setLastTestedAt(now);
        status.setMessage(result.message());
        provider.setStatus(status);
        var updates = new ArrayList<Mono<?>>();
        updates.add(client.update(provider));
        if (account != null) {
            var accountStatus =
                    account.getStatus() == null ? new AiProviderAccount.Status() : account.getStatus();
            accountStatus.setHealthScore(100.0);
            accountStatus.setLastUsedAt(now);
            accountStatus.setLastError(null);
            account.setStatus(accountStatus);
            updates.add(client.update(account));
        }
        return Mono.when(updates)
                .thenReturn(result)
                .onErrorResume(error -> {
                    log.warn("供应商测试状态写回失败。", error);
                    return Mono.just(result);
                });
    }

    private Mono<Void> markFailure(
            AiProvider provider, @Nullable AiProviderAccount account, Throwable error) {
        var reason = testError(error).getReason();
        var message = StringUtils.hasText(reason) ? reason : "供应商连通性测试失败。";
        var now = Instant.now();
        var status = provider.getStatus() == null ? new AiProvider.Status() : provider.getStatus();
        status.setLastTestedAt(now);
        status.setMessage(message);
        provider.setStatus(status);
        var updates = new ArrayList<Mono<?>>();
        updates.add(client.update(provider));
        if (account != null) {
            var accountStatus =
                    account.getStatus() == null ? new AiProviderAccount.Status() : account.getStatus();
            accountStatus.setLastError(message);
            account.setStatus(accountStatus);
            updates.add(client.update(account));
        }
        return Mono.when(updates)
                .then()
                .onErrorResume(updateError -> {
                    log.warn("供应商测试失败状态写回失败。", updateError);
                    return Mono.empty();
                });
    }

    private static AiModelRequest testModelRequest(AiModel.Capability capability) {
        return new AiModelRequest(
                capability,
                List.of(new AiModelContent.Text(TEST_PROMPT)),
                Map.of(),
                false,
                null);
    }

    private static AiModel.@Nullable Capability syncTestCapability(AiModel model) {
        var capabilities = model.getSpec().effectiveCapabilities();
        for (var capability : SYNC_TEST_CAPABILITIES) {
            if (capabilities.contains(capability)) {
                return capability;
            }
        }
        return null;
    }

    private static AiChatIr testRequest() {
        return new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text(TEST_PROMPT, null)))),
                List.of(),
                new AiRequestParams(null, 0.0, 8, null, null, null, null, Map.of()),
                false);
    }

    private Mono<AiProviderAccount> firstAccount(String providerName) {
        return accountService.firstEnabled(providerName);
    }

    private Mono<AiModel> firstChatModel(String providerName) {
        var options = ListOptions.builder().andQuery(equal("spec.provider", providerName)).build();
        return client.listAll(AiModel.class, options, defaultSort())
                .filter(model -> model.getSpec() != null
                        && model.getSpec().isEnabled()
                        && model.getSpec().getModelType() == AiModel.ModelType.CHAT)
                .next();
    }

    private static ApiException testFailed(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_TEST_FAILED, message);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ai-provider-request-invalid", message);
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "ai-provider-not-found", message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "ai-provider-conflict", message);
    }

    private ApiException testError(Throwable error) {
        if (error instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_TEST_FAILED, "供应商连通性测试失败。", error);
    }

    private static org.springdoc.core.fn.builders.parameter.Builder nameParameter() {
        return parameterBuilder().in(ParameterIn.PATH).name("name").required(true);
    }

    public record TestResult(boolean successful, long latencyMillis, String message, String model) {}

    public record QuotaUpdate(
            AiProviderAccount.Quota quota,
            List<AiProviderAccount.QuotaWindow> windows,
            AiProviderAccount.Balance balance) {}

    public record ArrangeRequest(List<String> names) {}

    public record DuplicateRequest(String name, @Nullable String displayName) {}
}
