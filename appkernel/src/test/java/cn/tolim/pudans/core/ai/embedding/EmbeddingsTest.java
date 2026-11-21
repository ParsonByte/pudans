package cn.tolim.pudans.core.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jEmbeddingModelFactory;
import cn.tolim.pudans.infra.exception.ApiException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Arrays;
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
class EmbeddingsTest {

    private static final String MODEL_NAME = "embedding-model";

    private static final String PROVIDER_NAME = "embedding-provider";

    private static final String MODEL_CODE = "text-embedding-3-small";

    @Mock
    AiSettings aiSettings;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderAccounts providerAccounts;

    @Mock
    LangChain4jEmbeddingModelFactory embeddingModelFactory;

    @Mock
    EmbeddingModel embeddingModel;

    @InjectMocks
    Embeddings service;

    @Test
    void rejectsMissingEmbeddingModelConfiguration() {
        var spec = setting(64);
        spec.getEmbedding().setKnowledgeBaseModel(null);
        when(aiSettings.spec()).thenReturn(Mono.just(spec));

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-embedding-model-missing"))
                .verify();

        verifyNoInteractions(client, embeddingModelFactory, embeddingModel);
    }

    @Test
    void rejectsMissingSetting() {
        when(aiSettings.spec()).thenReturn(Mono.empty());

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-embedding-model-missing"))
                .verify();

        verifyNoInteractions(client, embeddingModelFactory, embeddingModel);
    }

    @Test
    void fallsBackToMemoryModel() {
        var spec = setting(64);
        spec.getEmbedding().setKnowledgeBaseModel(null);
        spec.getEmbedding().setMemoryModel("memory-model");
        when(aiSettings.spec()).thenReturn(Mono.just(spec));
        stubEmbeddingTarget(model("memory-model"));
        stubAccounts(account("account-a", true));
        stubEmbedding(List.of("hello"), vector(1f, 2f));

        StepVerifier.create(service.embed(List.of("hello")))
                .assertNext(batch -> assertThat(batch.modelCode()).isEqualTo(MODEL_CODE))
                .verifyComplete();

        verify(client).get(AiModel.class, "memory-model");
    }

    @Test
    void fallsBackToQueryModel() {
        var spec = setting(64);
        spec.getEmbedding().setKnowledgeBaseModel(null);
        spec.getEmbedding().setQueryModel("query-model");
        when(aiSettings.spec()).thenReturn(Mono.just(spec));
        stubEmbeddingTarget(model("query-model"));
        stubAccounts(account("account-a", true));
        stubEmbedding(List.of("hello"), vector(1f, 2f));

        StepVerifier.create(service.embed(List.of("hello")))
                .assertNext(batch -> assertThat(batch.modelCode()).isEqualTo(MODEL_CODE))
                .verifyComplete();

        verify(client).get(AiModel.class, "query-model");
    }

    @Test
    void prefersExplicitModelNameOverSetting() {
        stubSetting(64);
        var custom = model("custom-model");
        custom.getSpec().setModelCode("custom-embedding");
        stubEmbeddingTarget(custom);
        stubAccounts(account("account-a", true));
        stubEmbedding(List.of("hello"), vector(1f, 2f));

        StepVerifier.create(service.embed(List.of("hello"), "custom-model"))
                .assertNext(batch -> {
                    assertThat(batch.modelCode()).isEqualTo("custom-embedding");
                    assertThat(batch.dimension()).isEqualTo(2);
                })
                .verifyComplete();

        verify(client, never()).get(AiModel.class, MODEL_NAME);
    }

    @Test
    void resolvesModelProviderAndFirstEnabledAccount() {
        stubSetting(64);
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts(account("disabled-account", false), account("enabled-account", true), account("later", true));
        stubEmbedding(List.of("hello"), vector(1f, 2f));

        StepVerifier.create(service.embed(List.of("hello")))
                .assertNext(batch -> {
                    assertThat(batch.modelCode()).isEqualTo(MODEL_CODE);
                    assertThat(batch.dimension()).isEqualTo(2);
                    assertThat(batch.vectors()).hasSize(1);
                })
                .verifyComplete();

        var accountCaptor = ArgumentCaptor.forClass(AiProviderAccount.class);
        verify(providerAccounts).apiKey(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getMetadata().getName()).isEqualTo("enabled-account");
        verify(client).get(AiModel.class, MODEL_NAME);
        verify(client).get(AiProvider.class, PROVIDER_NAME);
    }

    @Test
    void rejectsNonEmbeddingModels() {
        stubSetting(64);
        var model = model(MODEL_NAME);
        model.getSpec().setModelType(AiModel.ModelType.CHAT);
        stubModel(model);

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-embedding-model-missing"))
                .verify();

        verifyNoInteractions(embeddingModelFactory, embeddingModel);
    }

    @Test
    void rejectsDisabledModels() {
        stubSetting(64);
        var model = model(MODEL_NAME);
        model.getSpec().setEnabled(false);
        stubModel(model);

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-embedding-model-missing"))
                .verify();

        verifyNoInteractions(embeddingModelFactory, embeddingModel);
    }

    @Test
    void rejectsMissingProviders() {
        stubSetting(64);
        stubModel(model(MODEL_NAME));
        when(client.get(AiProvider.class, PROVIDER_NAME)).thenReturn(Mono.empty());

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-embedding-provider-missing"))
                .verify();

        verifyNoInteractions(embeddingModelFactory, embeddingModel);
    }

    @Test
    void rejectsMissingAccounts() {
        stubSetting(64);
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts();

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 503, "ai-embedding-account-missing"))
                .verify();

        verifyNoInteractions(embeddingModelFactory, embeddingModel);
    }

    @Test
    void rejectsUnsupportedProtocols() {
        stubSetting(64);
        stubModel(model(MODEL_NAME));
        stubProvider(provider(AiProvider.Protocol.ANTHROPIC));

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 400, "ai-embedding-protocol-unsupported"))
                .verify();

        verifyNoInteractions(embeddingModelFactory, embeddingModel);
    }

    @Test
    void splitsInputsIntoConfiguredBatches() {
        stubSetting(2);
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts(account("account-a", true));
        stubEmbedding(List.of("a", "b"), vector(0f, 0f), vector(1f, 1f));
        stubEmbedding(List.of("c", "d"), vector(2f, 2f), vector(3f, 3f));
        stubEmbedding(List.of("e"), vector(4f, 4f));

        StepVerifier.create(service.embed(List.of("a", "b", "c", "d", "e")))
                .assertNext(batch -> {
                    assertThat(batch.vectors()).hasSize(5);
                    assertThat(batch.vectors().get(4)).containsExactly(4f, 4f);
                })
                .verifyComplete();

        verify(embeddingModel, times(3)).embedAll(anyList());
    }

    @Test
    void rejectsDimensionMismatch() {
        stubSetting(64);
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts(account("account-a", true));
        stubEmbedding(List.of("hello"), new float[] {1f, 2f, 3f});

        StepVerifier.create(service.embed(List.of("hello")))
                .expectErrorSatisfies(error -> assertApiError(error, 400, "ai-embedding-dimension-mismatch"))
                .verify();
    }

    @Test
    void usesModelEmbeddingDimensionOverride() {
        var spec = setting(64);
        spec.getEmbedding().setDimension(4);
        when(aiSettings.spec()).thenReturn(Mono.just(spec));
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts(account("account-a", true));
        stubEmbedding(List.of("hello"), vector(1f, 2f));

        StepVerifier.create(service.embed(List.of("hello")))
                .assertNext(batch -> assertThat(batch.dimension()).isEqualTo(2))
                .verifyComplete();
    }

    @Test
    void returnsEmptyBatchWithoutCallingUpstreamForEmptyInputs() {
        stubSetting(64);
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts(account("account-a", true));

        StepVerifier.create(service.embed(List.of()))
                .assertNext(batch -> {
                    assertThat(batch.modelCode()).isEqualTo(MODEL_CODE);
                    assertThat(batch.dimension()).isEqualTo(2);
                    assertThat(batch.vectors()).isEmpty();
                })
                .verifyComplete();

        verifyNoInteractions(embeddingModelFactory, embeddingModel);
    }

    @Test
    void rejectsMultimodalInputs() {
        stubSetting(64);
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts(account("account-a", true));
        var image = new AiModelContent.Image(
                "https://example.com/cat.png", null, "image/png", null, null);

        StepVerifier.create(service.embedContents(
                        List.of(new AiModelContent.Text("cat"), image), null))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, Embeddings.INPUT_UNSUPPORTED_CODE))
                .verify();

        verifyNoInteractions(embeddingModel);
    }

    @Test
    void embedOneReturnsTheSingleVector() {
        stubSetting(64);
        stubEmbeddingTarget(model(MODEL_NAME));
        stubAccounts(account("account-a", true));
        stubEmbedding(List.of("hello"), vector(1f, 2f));

        StepVerifier.create(service.embedOne("hello"))
                .expectNextMatches(vector -> vector.length == 2 && vector[0] == 1f)
                .verifyComplete();
    }

    private void stubSetting(int batchSize) {
        when(aiSettings.spec()).thenReturn(Mono.just(setting(batchSize)));
    }

    private void stubEmbeddingTarget(AiModel model) {
        stubModel(model);
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT);
        stubProvider(provider);
        lenient()
                .when(embeddingModelFactory.embeddingModel(
                        any(), eq(AiProvider.Protocol.OPENAI_CHAT), anyString(), any(), any()))
                .thenReturn(embeddingModel);
    }

    private void stubModel(AiModel model) {
        when(client.get(AiModel.class, model.getMetadata().getName())).thenReturn(Mono.just(model));
    }

    private void stubProvider(AiProvider provider) {
        when(client.get(AiProvider.class, PROVIDER_NAME)).thenReturn(Mono.just(provider));
    }

    private void stubAccounts(AiProviderAccount... accounts) {
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.fromArray(accounts));
        lenient().when(providerAccounts.apiKey(any())).thenReturn(Mono.just("api-key"));
    }

    private void stubEmbedding(List<String> inputs, float[]... vectors) {
        doReturn(Response.from(Arrays.stream(vectors).map(Embedding::new).toList()))
                .when(embeddingModel)
                .embedAll(argThat(segments -> texts(segments).equals(inputs)));
    }

    private static List<String> texts(List<TextSegment> segments) {
        var texts = new ArrayList<String>();
        for (var segment : segments) {
            texts.add(segment.text());
        }
        return texts;
    }

    private static AiSetting.Spec setting(int batchSize) {
        var spec = new AiSetting().getSpec();
        spec.getEmbedding().setKnowledgeBaseModel(MODEL_NAME);
        spec.getEmbedding().setDimension(2);
        spec.getEmbedding().setBatchSize(batchSize);
        return spec;
    }

    private static AiModel model(String name) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        model.getSpec().setProvider(PROVIDER_NAME);
        model.getSpec().setModelCode(MODEL_CODE);
        model.getSpec().setModelType(AiModel.ModelType.EMBEDDING);
        model.getSpec().setEmbeddingDim(2);
        return model;
    }

    private static AiProvider provider(AiProvider.Protocol protocol) {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(protocol);
        provider.getSpec().setBaseUrl("https://api.example.com");
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

    private static float[] vector(float first, float second) {
        return new float[] {first, second};
    }

    private static void assertApiError(Throwable error, int status, String code) {
        assertThat(error).isInstanceOfSatisfying(ApiException.class, api -> {
            assertThat(api.getStatusCode().value()).isEqualTo(status);
            assertThat(api.getCode()).isEqualTo(code);
        });
    }
}