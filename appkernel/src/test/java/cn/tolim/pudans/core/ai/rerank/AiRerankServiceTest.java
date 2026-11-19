package cn.tolim.pudans.core.ai.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiRerankServiceTest {

    private static final String MODEL_NAME = "rerank-model";

    private static final String PROVIDER_NAME = "rerank-provider";

    private static final String MODEL_CODE = "rerank-v1";

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiRerankClient rerankClient;

    @InjectMocks
    AiRerankService service;

    @Test
    void resolvesModelProviderAndFirstEnabledAccount() {
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.OPENAI_CHAT, true));
        stubAccounts(account("disabled-account", false), account("enabled-account", true), account("later", true));
        var accountCaptor = ArgumentCaptor.forClass(AiProviderAccount.class);
        var requestCaptor = ArgumentCaptor.forClass(RerankRequest.class);
        when(rerankClient.rerank(
                        any(AiProvider.class), accountCaptor.capture(), eq(MODEL_CODE), requestCaptor.capture()))
                .thenReturn(Mono.just(new RerankResult(List.of(scored(0, 0.9), scored(1, 0.4)))));

        StepVerifier.create(service.rerank(MODEL_NAME, "安装", List.of("a", "b"), 2))
                .assertNext(result -> {
                    assertThat(result.results()).extracting(RerankResult.ScoredIndex::index)
                            .containsExactly(0, 1);
                    assertThat(result.results()).extracting(RerankResult.ScoredIndex::score)
                            .containsExactly(0.9, 0.4);
                })
                .verifyComplete();

        assertThat(accountCaptor.getValue().getMetadata().getName()).isEqualTo("enabled-account");
        assertThat(requestCaptor.getValue().query()).isEqualTo("安装");
        assertThat(requestCaptor.getValue().documents()).containsExactly("a", "b");
        assertThat(requestCaptor.getValue().topK()).isEqualTo(2);
        verify(client).get(AiModel.class, MODEL_NAME);
        verify(client).get(AiProvider.class, PROVIDER_NAME);
    }

    @Test
    void sortsByScoreDescendingAndKeepsOriginalIndexes() {
        stubTarget();
        when(rerankClient.rerank(
                        any(AiProvider.class), any(AiProviderAccount.class), eq(MODEL_CODE), any(RerankRequest.class)))
                .thenReturn(Mono.just(new RerankResult(
                        List.of(scored(1, 0.2), scored(0, 0.8), scored(2, 0.5)))));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a", "b", "c"), 3))
                .assertNext(result -> assertThat(result.results())
                        .extracting(RerankResult.ScoredIndex::index)
                        .containsExactly(0, 2, 1))
                .verifyComplete();
    }

    @Test
    void truncatesResultsToTopK() {
        stubTarget();
        when(rerankClient.rerank(
                        any(AiProvider.class), any(AiProviderAccount.class), eq(MODEL_CODE), any(RerankRequest.class)))
                .thenReturn(Mono.just(new RerankResult(
                        List.of(scored(0, 0.1), scored(1, 0.9), scored(2, 0.5)))));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a", "b", "c"), 2))
                .assertNext(result -> {
                    assertThat(result.results()).hasSize(2);
                    assertThat(result.results()).extracting(RerankResult.ScoredIndex::index)
                            .containsExactly(1, 2);
                })
                .verifyComplete();
    }

    @Test
    void rejectsMissingModels() {
        when(client.get(AiModel.class, MODEL_NAME)).thenReturn(Mono.empty());

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-rerank-model-missing"))
                .verify();

        verifyNoInteractions(rerankClient);
    }

    @Test
    void rejectsNonRerankModels() {
        var model = model(MODEL_NAME);
        model.getSpec().setModelType(AiModel.ModelType.CHAT);
        stubModel(model);

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-rerank-model-missing"))
                .verify();

        verifyNoInteractions(rerankClient);
    }

    @Test
    void rejectsUnsupportedProtocols() {
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.ANTHROPIC, true));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 400, "ai-rerank-protocol-unsupported"))
                .verify();

        verifyNoInteractions(rerankClient);
    }

    @Test
    void rejectsDisabledProviders() {
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.OPENAI_CHAT, false));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-rerank-provider-missing"))
                .verify();

        verifyNoInteractions(rerankClient);
    }

    @Test
    void rejectsMissingAccounts() {
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.OPENAI_CHAT, true));
        stubAccounts();

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-rerank-account-missing"))
                .verify();

        verifyNoInteractions(rerankClient);
    }

    @Test
    void rejectsOutOfRangeIndexes() {
        stubTarget();
        when(rerankClient.rerank(
                        any(AiProvider.class), any(AiProviderAccount.class), eq(MODEL_CODE), any(RerankRequest.class)))
                .thenReturn(Mono.just(new RerankResult(List.of(scored(5, 0.9)))));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a", "b"), 2))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    @Test
    void mapsUpstreamFailuresToBadGateway() {
        stubTarget();
        when(rerankClient.rerank(
                        any(AiProvider.class), any(AiProviderAccount.class), eq(MODEL_CODE), any(RerankRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    @Test
    void returnsEmptyResultWithoutCallingUpstreamForEmptyDocuments() {
        stubTarget();

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of(), 5))
                .assertNext(result -> assertThat(result.results()).isEmpty())
                .verifyComplete();

        verifyNoInteractions(rerankClient);
    }

    @Test
    void rejectsNonPositiveTopK() {
        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 0))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(client, rerankClient);
    }

    private void stubTarget() {
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.OPENAI_CHAT, true));
        stubAccounts(account("account-a", true));
    }

    private void stubModel(AiModel model) {
        when(client.get(AiModel.class, model.getMetadata().getName())).thenReturn(Mono.just(model));
    }

    private void stubProvider(AiProvider provider) {
        when(client.get(AiProvider.class, PROVIDER_NAME)).thenReturn(Mono.just(provider));
    }

    private void stubAccounts(AiProviderAccount... accounts) {
        when(client.listAll(eq(AiProviderAccount.class), any(), any())).thenReturn(Flux.fromArray(accounts));
    }

    private static AiModel model(String name) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        model.getSpec().setProvider(PROVIDER_NAME);
        model.getSpec().setModelCode(MODEL_CODE);
        model.getSpec().setModelType(AiModel.ModelType.RERANK);
        return model;
    }

    private static AiProvider provider(AiProvider.Protocol protocol, boolean enabled) {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(protocol);
        provider.getSpec().setBaseUrl("https://api.example.com");
        provider.getSpec().setEnabled(enabled);
        return provider;
    }

    private static AiProviderAccount account(String name, boolean enabled) {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName(name);
        account.setMetadata(metadata);
        account.getSpec().setProvider(PROVIDER_NAME);
        account.getSpec().setEnabled(enabled);
        return account;
    }

    private static RerankResult.ScoredIndex scored(int index, double score) {
        return new RerankResult.ScoredIndex(index, score);
    }

    private static void assertApiError(Throwable error, int status, String code) {
        assertThat(error).isInstanceOf(ApiException.class);
        var apiException = (ApiException) error;
        assertThat(apiException.getStatusCode().value()).isEqualTo(status);
        assertThat(apiException.getCode()).isEqualTo(code);
    }
}
