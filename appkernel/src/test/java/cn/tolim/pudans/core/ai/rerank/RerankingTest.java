package cn.tolim.pudans.core.ai.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jScoringModelFactory;
import cn.tolim.pudans.infra.exception.ApiException;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RerankingTest {

    private static final String MODEL_NAME = "rerank-model";

    private static final String PROVIDER_NAME = "rerank-provider";

    private static final String MODEL_CODE = "rerank-v1";

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderAccounts accountService;

    @Mock
    LangChain4jScoringModelFactory scoringModelFactory;

    @Mock
    ScoringModel scoringModel;

    @InjectMocks
    Reranking service;

    @Test
    void resolvesModelProviderAndFirstEnabledAccount() {
        stubModel(model(MODEL_NAME));
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT, true);
        stubProvider(provider);
        stubSupported(provider);
        when(accountService.firstEnabled(PROVIDER_NAME)).thenReturn(Mono.just(account("enabled-account", true)));
        stubScoring(0.9, 0.4);

        StepVerifier.create(service.rerank(MODEL_NAME, "安装", List.of("a", "b"), 2))
                .assertNext(result -> {
                    assertThat(result.results()).extracting(RerankResult.ScoredIndex::index)
                            .containsExactly(0, 1);
                    assertThat(result.results()).extracting(RerankResult.ScoredIndex::score)
                            .containsExactly(0.9, 0.4);
                })
                .verifyComplete();

        verify(client).get(AiModel.class, MODEL_NAME);
        verify(client).get(AiProvider.class, PROVIDER_NAME);
        verify(accountService).firstEnabled(PROVIDER_NAME);
    }

    @Test
    void sortsByScoreDescendingAndKeepsOriginalIndexes() {
        stubTarget();
        stubScoring(0.2, 0.8, 0.5);

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a", "b", "c"), 3))
                .assertNext(result -> assertThat(result.results())
                        .extracting(RerankResult.ScoredIndex::index)
                        .containsExactly(1, 2, 0))
                .verifyComplete();
    }

    @Test
    void truncatesResultsToTopK() {
        stubTarget();
        stubScoring(0.1, 0.9, 0.5);

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

        verifyNoInteractions(scoringModel);
    }

    @Test
    void rejectsNonRerankModels() {
        var model = model(MODEL_NAME);
        model.getSpec().setModelType(AiModel.ModelType.CHAT);
        stubModel(model);

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-rerank-model-missing"))
                .verify();

        verifyNoInteractions(scoringModel);
    }

    @Test
    void rejectsUnsupportedProtocols() {
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.ANTHROPIC, true));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 400, "ai-rerank-protocol-unsupported"))
                .verify();

        verifyNoInteractions(scoringModel);
    }

    @Test
    void rejectsDisabledProviders() {
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.OPENAI_CHAT, false));

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-rerank-provider-missing"))
                .verify();

        verifyNoInteractions(scoringModel);
    }

    @Test
    void rejectsMissingAccounts() {
        stubModel(model(MODEL_NAME));
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT, true);
        stubProvider(provider);
        stubSupported(provider);
        when(accountService.firstEnabled(PROVIDER_NAME)).thenReturn(Mono.empty());

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 1))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-rerank-account-missing"))
                .verify();

        verifyNoInteractions(scoringModel);
    }

    @Test
    void rejectsScoreCountMismatch() {
        stubTarget();
        stubScoring(0.9);

        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a", "b"), 2))
                .expectErrorSatisfies(error -> assertApiError(error, 502, "ai-rerank-upstream-error"))
                .verify();
    }

    @Test
    void mapsUpstreamFailuresToBadGateway() {
        stubTarget();
        when(accountService.apiKey(any())).thenReturn(Mono.just("api-key"));
        when(scoringModelFactory.create(any(), eq("api-key"), eq(MODEL_CODE))).thenReturn(scoringModel);
        when(scoringModel.scoreAll(anyList(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

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

        verifyNoInteractions(scoringModel);
    }

    @Test
    void rejectsNonPositiveTopK() {
        StepVerifier.create(service.rerank(MODEL_NAME, "查询", List.of("a"), 0))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(client, scoringModel);
    }

    private void stubTarget() {
        stubModel(model(MODEL_NAME));
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT, true);
        stubProvider(provider);
        stubSupported(provider);
        when(accountService.firstEnabled(PROVIDER_NAME)).thenReturn(Mono.just(account("account-a", true)));
    }

    private void stubSupported(AiProvider provider) {
        when(scoringModelFactory.supports(provider.getSpec().getProtocol())).thenReturn(true);
    }

    private void stubScoring(double... scores) {
        when(accountService.apiKey(any())).thenReturn(Mono.just("api-key"));
        when(scoringModelFactory.create(any(), eq("api-key"), eq(MODEL_CODE))).thenReturn(scoringModel);
        when(scoringModel.scoreAll(anyList(), anyString())).thenReturn(Response.from(asList(scores)));
    }

    private static List<Double> asList(double... scores) {
        var values = new Double[scores.length];
        for (var index = 0; index < scores.length; index++) {
            values[index] = scores[index];
        }
        return List.of(values);
    }

    private void stubModel(AiModel model) {
        when(client.get(AiModel.class, model.getMetadata().getName())).thenReturn(Mono.just(model));
    }

    private void stubProvider(AiProvider provider) {
        when(client.get(AiProvider.class, PROVIDER_NAME)).thenReturn(Mono.just(provider));
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

    private static void assertApiError(Throwable error, int status, String code) {
        assertThat(error).isInstanceOf(ApiException.class);
        var apiException = (ApiException) error;
        assertThat(apiException.getStatusCode().value()).isEqualTo(status);
        assertThat(apiException.getCode()).isEqualTo(code);
    }
}