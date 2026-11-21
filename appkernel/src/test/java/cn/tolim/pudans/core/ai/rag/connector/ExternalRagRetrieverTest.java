package cn.tolim.pudans.core.ai.rag.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.rag.RetrievalOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExternalRagRetrieverTest {

    @Mock
    ExternalRagConnectorResolver connectorResolver;

    @Mock
    ProviderAccounts providerAccounts;

    @Mock
    ExternalRagConnector connector;

    private ExternalRagRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new ExternalRagRetriever(connectorResolver, providerAccounts);
    }

    @Test
    void selectsConnectorAndMapsDocumentsToChunks() {
        var knowledgeBase = knowledgeBase("kb-ext");
        var external = external(KnowledgeBase.ExternalProvider.DIFY);
        external.setTopK(2);
        external.setScoreThreshold(0.4);
        external.setFilters(Map.of("source", "manual"));
        knowledgeBase.getSpec().setExternal(external);
        when(connectorResolver.connector(KnowledgeBase.ExternalProvider.DIFY)).thenReturn(connector);
        when(connector.retrieve(any(ExternalRagTarget.class), any(ExternalRagRequest.class)))
                .thenReturn(Mono.just(new ExternalRagResult(List.of(
                        new ExternalRagDocument("doc-1", "manual.pdf", "chunk-1", "低分片段", 0.3, null, null),
                        new ExternalRagDocument("doc-2", "guide.pdf", null, "高分片段", 0.9, 2, "第二章"),
                        new ExternalRagDocument(null, null, null, "无标识片段", 0.99, null, null)))));

        var chunks = retriever.retrieve(knowledgeBase, "安装", options()).block();

        assertThat(chunks).hasSize(2);
        var first = chunks.get(0);
        assertThat(first.candidate().kbName()).isEqualTo("kb-ext");
        assertThat(first.candidate().fileName()).isEqualTo("guide.pdf");
        assertThat(first.candidate().pageNo()).isEqualTo(2);
        assertThat(first.candidate().headingPath()).isEqualTo("第二章");
        assertThat(first.candidate().documentId()).isPositive();
        assertThat(first.candidate().chunkId()).isPositive();
        assertThat(first.score()).isEqualTo(0.9);
        assertThat(chunks.get(1).candidate().fileName()).isEqualTo("manual.pdf");

        var targetCaptor = ArgumentCaptor.forClass(ExternalRagTarget.class);
        var requestCaptor = ArgumentCaptor.forClass(ExternalRagRequest.class);
        verify(connector).retrieve(targetCaptor.capture(), requestCaptor.capture());
        assertThat(targetCaptor.getValue().apiKey()).isEmpty();
        assertThat(requestCaptor.getValue().topK()).isEqualTo(2);
        assertThat(requestCaptor.getValue().scoreThreshold()).isEqualTo(0.4);
        assertThat(requestCaptor.getValue().filters()).containsEntry("source", "manual");
    }

    @Test
    void resolvesCredentialReferenceThroughProviderAccount() {
        var knowledgeBase = knowledgeBase("kb-ext");
        var external = external(KnowledgeBase.ExternalProvider.RAGFLOW);
        external.setCredentialRef("accounts/primary");
        knowledgeBase.getSpec().setExternal(external);
        var account = new AiProviderAccount();
        when(providerAccounts.get("accounts/primary")).thenReturn(Mono.just(account));
        when(providerAccounts.apiKey(account)).thenReturn(Mono.just("resolved-key"));
        when(connectorResolver.connector(KnowledgeBase.ExternalProvider.RAGFLOW)).thenReturn(connector);
        when(connector.retrieve(any(ExternalRagTarget.class), any(ExternalRagRequest.class)))
                .thenReturn(Mono.just(new ExternalRagResult(List.of())));

        var chunks = retriever.retrieve(knowledgeBase, "安装", options()).block();

        assertThat(chunks).isEmpty();
        var targetCaptor = ArgumentCaptor.forClass(ExternalRagTarget.class);
        verify(connector).retrieve(targetCaptor.capture(), any(ExternalRagRequest.class));
        assertThat(targetCaptor.getValue().apiKey()).isEqualTo("resolved-key");
    }

    @Test
    void rejectsInvalidExternalConfiguration() {
        var knowledgeBase = knowledgeBase("kb-ext");
        var external = new KnowledgeBase.External();
        knowledgeBase.getSpec().setExternal(external);

        StepVerifier.create(retriever.retrieve(knowledgeBase, "安装", options()))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("provider"))
                .verify();

        external.setProvider(KnowledgeBase.ExternalProvider.DIFY);
        StepVerifier.create(retriever.retrieve(knowledgeBase, "安装", options()))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("endpoint"))
                .verify();

        external.setEndpoint("https://dify.local");
        external.setTopK(0);
        StepVerifier.create(retriever.retrieve(knowledgeBase, "安装", options()))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("topK"))
                .verify();

        external.setTopK(5);
        external.setScoreThreshold(1.5);
        StepVerifier.create(retriever.retrieve(knowledgeBase, "安装", options()))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("scoreThreshold"))
                .verify();
    }

    @Test
    void rejectsMissingCredentialReference() {
        var knowledgeBase = knowledgeBase("kb-ext");
        var external = external(KnowledgeBase.ExternalProvider.DIFY);
        external.setCredentialRef("accounts/missing");
        knowledgeBase.getSpec().setExternal(external);
        when(providerAccounts.get("accounts/missing")).thenReturn(Mono.empty());

        StepVerifier.create(retriever.retrieve(knowledgeBase, "安装", options()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ExternalRagException.class);
                    assertThat(error.getMessage()).contains("凭据引用不存在");
                })
                .verify();
        verifyNoInteractions(connectorResolver);
    }

    @Test
    void returnsEmptyListWhenExternalIsNotConfigured() {
        var knowledgeBase = knowledgeBase("kb-local");

        var chunks = retriever.retrieve(knowledgeBase, "安装", options()).block();

        assertThat(chunks).isEmpty();
        verifyNoInteractions(connectorResolver, providerAccounts);
    }

    @Test
    void masksApiKeyWhenTargetIsRendered() {
        var target = new ExternalRagTarget("https://rag.local", "dataset-1", "kb-1", "sk-secret", null);

        assertThat(target.toString()).contains("已配置").doesNotContain("sk-secret");
    }

    @Test
    void detectsExternalConfiguration() {
        var knowledgeBase = knowledgeBase("kb-ext");
        assertThat(ExternalRagRetriever.isExternal(null)).isFalse();
        assertThat(ExternalRagRetriever.isExternal(knowledgeBase)).isFalse();

        knowledgeBase.getSpec().setExternal(new KnowledgeBase.External());

        assertThat(ExternalRagRetriever.isExternal(knowledgeBase)).isTrue();
    }

    private static RetrievalOptions options() {
        return new RetrievalOptions(60, 0.25, 5, 4000);
    }

    private static KnowledgeBase knowledgeBase(String code) {
        var knowledgeBase = new KnowledgeBase();
        var metadata = new Metadata();
        metadata.setName("/registry/pudans.tolim.cn/knowledgebases/" + code);
        knowledgeBase.setMetadata(metadata);
        return knowledgeBase;
    }

    private static KnowledgeBase.External external(KnowledgeBase.ExternalProvider provider) {
        var external = new KnowledgeBase.External();
        external.setProvider(provider);
        external.setEndpoint("https://rag.local");
        external.setDataset("dataset-1");
        return external;
    }
}