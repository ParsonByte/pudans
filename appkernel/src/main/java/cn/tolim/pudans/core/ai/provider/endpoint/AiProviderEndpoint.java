package cn.tolim.pudans.core.ai.provider.endpoint;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiProviderAdapterRegistry;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

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

    private final AiProviderAdapterRegistry adapterRegistry;

    private final AiModelAdapterRegistry modelAdapters;

    private final AiEmbeddingService embeddingService;

    private final AiProviderAccountService accountService;

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
                        return testEmbedding(model);
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

    private Mono<TestResult> testEmbedding(AiModel model) {
        var started = Instant.now();
        return embeddingService
                .embed(List.of(TEST_PROMPT), model.getMetadata().getName())
                .map(batch -> new TestResult(
                        true,
                        Duration.between(started, Instant.now()).toMillis(),
                        "Embedding 连通性正常。",
                        batch.modelCode()))
                .onErrorMap(error -> testError(error));
    }

    private Mono<TestResult> test(AiProvider provider, AiProviderAccount account, AiModel model) {
        var started = Instant.now();
        return adapterRegistry
                .require(provider.getSpec().getProtocol())
                .chat(provider, account, model, testRequest())
                .map(response -> new TestResult(
                        true,
                        Duration.between(started, Instant.now()).toMillis(),
                        "连通性正常。",
                        response.model()))
                .onErrorMap(error -> testError(error));
    }

    private Mono<TestResult> testCapability(
            AiProvider provider, AiProviderAccount account, AiModel model) {
        var capability = syncTestCapability(model);
        if (capability == null) {
            return Mono.error(testFailed("该模型不支持同步连通性测试，请通过生成接口验证。"));
        }
        var protocol = provider.getSpec().getProtocol();
        if (!modelAdapters.supports(protocol, capability)) {
            return Mono.error(testFailed("供应商协议不支持模型能力：" + capability + "。"));
        }
        var started = Instant.now();
        return modelAdapters
                .require(protocol, capability)
                .invoke(provider, account, model, testModelRequest(capability))
                .map(response -> new TestResult(
                        true,
                        Duration.between(started, Instant.now()).toMillis(),
                        "连通性正常。",
                        response.modelCode()))
                .onErrorMap(error -> testError(error));
    }

    private static AiModelRequest testModelRequest(AiModel.Capability capability) {
        var inputs = capability == AiModel.Capability.RERANK
                ? List.<AiModelContent>of(
                        new AiModelContent.Text(TEST_PROMPT), new AiModelContent.Text(TEST_PROMPT))
                : List.<AiModelContent>of(new AiModelContent.Text(TEST_PROMPT));
        var params = capability == AiModel.Capability.RERANK
                ? Map.<String, JsonNode>of("topK", JsonNodeFactory.instance.numberNode(1))
                : Map.<String, JsonNode>of();
        return new AiModelRequest(capability, inputs, params, false, null);
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
}
