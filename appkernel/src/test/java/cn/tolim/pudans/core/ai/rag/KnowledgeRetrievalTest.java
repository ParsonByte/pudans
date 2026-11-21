package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.rag.connector.ExternalRagRetriever;
import cn.tolim.pudans.core.ai.rerank.Reranking;
import cn.tolim.pudans.core.ai.rerank.RerankResult;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbChunkRepository;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbEmbeddingStore;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbQueryLogRepository;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalTest {

    @Mock
    AiKbChunkRepository chunkRepository;

    @Mock
    AiKbEmbeddingStore embeddingStore;

    @Mock
    AiKbQueryLogRepository queryLogRepository;

    @Mock
    Reranking rerankService;

    @Mock
    ExternalRagRetriever externalRagRetriever;

    private KnowledgeRetrieval service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRetrieval(
                chunkRepository,
                embeddingStore,
                queryLogRepository,
                new HybridRetrieval(),
                new KnowledgeBaseAcl(),
                new CitationAssembler(),
                rerankService,
                externalRagRetriever);
    }

    @Test
    void filtersUnreadableKnowledgeBasesBeforeSearching() {
        var readable = knowledgeBase("kb-company", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        var unreadable = knowledgeBase("kb-private", KnowledgeBase.Visibility.PRIVATE, "bob", null);
        when(chunkRepository.searchKeyword(List.of("kb-company"), "安装", 50))
                .thenReturn(Flux.just(candidate(1, 0.9, "安装说明")));
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(readable, unreadable), null)))
                .assertNext(result -> {
                    assertThat(result.citations()).hasSize(1);
                    assertThat(result.citations().get(0).fileName()).isEqualTo("manual.pdf");
                    assertThat(result.prompt()).contains("[1]《manual.pdf》");
                })
                .verifyComplete();

        verifyNoInteractions(embeddingStore);
        verifyNoInteractions(rerankService);
        var queryLogCaptor = ArgumentCaptor.forClass(KnowledgeBaseQueryLog.class);
        verify(queryLogRepository).insert(queryLogCaptor.capture());
        var queryLog = queryLogCaptor.getValue();
        assertThat(queryLog.getKbNames()).containsExactly("kb-company");
        assertThat(queryLog.getHitChunkIds()).containsExactly(1L);
        assertThat(queryLog.getHitCount()).isEqualTo(1);
        assertThat(queryLog.getTopScore()).isEqualByComparingTo("1.0000");
        assertThat(queryLog.getLatencyMs()).isNotNull();
    }

    @Test
    void returnsAnEmptyResultWhenNoKnowledgeBaseIsReadable() {
        var unreadable = knowledgeBase("kb-private", KnowledgeBase.Visibility.PRIVATE, "bob", null);
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(unreadable), null)))
                .assertNext(result -> {
                    assertThat(result.prompt()).isEmpty();
                    assertThat(result.citations()).isEmpty();
                })
                .verifyComplete();

        verifyNoInteractions(chunkRepository);
        verifyNoInteractions(rerankService);
        var queryLogCaptor = ArgumentCaptor.forClass(KnowledgeBaseQueryLog.class);
        verify(queryLogRepository).insert(queryLogCaptor.capture());
        assertThat(queryLogCaptor.getValue().getKbNames()).isEmpty();
        assertThat(queryLogCaptor.getValue().getHitCount()).isZero();
        assertThat(queryLogCaptor.getValue().getTopScore()).isNull();
    }

    @Test
    void fusesKeywordAndVectorCandidatesAndNumbersCitations() {
        var readable = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        when(chunkRepository.searchKeyword(List.of("kb-a"), "安装", 50))
                .thenReturn(Flux.just(candidate(1, 0.9, "关键词命中")));
        when(embeddingStore.searchCandidates(any(EmbeddingSearchRequest.class)))
                .thenReturn(Mono.just(List.of(candidate(2, 0.8, "向量命中"))));
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(readable), new float[] {0.1f, 0.2f})))
                .assertNext(result -> {
                    assertThat(result.citations()).hasSize(2);
                    assertThat(result.citations()).extracting(Citation::index).containsExactly(1, 2);
                    assertThat(result.citations()).extracting(Citation::docId).containsExactly(101L, 102L);
                    assertThat(result.prompt()).contains("[1]").contains("[2]");
                })
                .verifyComplete();

        var queryLogCaptor = ArgumentCaptor.forClass(KnowledgeBaseQueryLog.class);
        verify(queryLogRepository).insert(queryLogCaptor.capture());
        assertThat(queryLogCaptor.getValue().getHitCount()).isEqualTo(2);
        assertThat(queryLogCaptor.getValue().getTopScore()).isEqualByComparingTo("0.5000");
    }

    @Test
    void usesExternalConnectorForConfiguredKnowledgeBase() {
        var readable = knowledgeBase("kb-ext", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        var external = new KnowledgeBase.External();
        external.setProvider(KnowledgeBase.ExternalProvider.DIFY);
        external.setEndpoint("https://dify.local");
        readable.getSpec().setExternal(external);
        var chunk = new RetrievedChunk(
                new RetrievalCandidate(11, 12, "kb-ext", "external.pdf", "外部片段", null, null, 0.9), 0.9);
        when(externalRagRetriever.retrieve(eq(readable), eq("安装"), any(RetrievalOptions.class)))
                .thenReturn(Mono.just(List.of(chunk)));
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(readable), new float[] {0.1f})))
                .assertNext(result -> {
                    assertThat(result.citations()).hasSize(1);
                    assertThat(result.citations().get(0).fileName()).isEqualTo("external.pdf");
                    assertThat(result.prompt()).contains("[1]《external.pdf》").contains("外部片段");
                })
                .verifyComplete();

        verifyNoInteractions(chunkRepository, embeddingStore, rerankService);
        var queryLogCaptor = ArgumentCaptor.forClass(KnowledgeBaseQueryLog.class);
        verify(queryLogRepository).insert(queryLogCaptor.capture());
        assertThat(queryLogCaptor.getValue().getKbNames()).containsExactly("kb-ext");
        assertThat(queryLogCaptor.getValue().getHitChunkIds()).containsExactly(11L);
    }

    @Test
    void placesExternalChunksBeforeLocalChunks() {
        var externalKnowledgeBase = knowledgeBase("kb-ext", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        var external = new KnowledgeBase.External();
        external.setProvider(KnowledgeBase.ExternalProvider.DIFY);
        external.setEndpoint("https://dify.local");
        externalKnowledgeBase.getSpec().setExternal(external);
        var localKnowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        var externalChunk = new RetrievedChunk(
                new RetrievalCandidate(11, 12, "kb-ext", "external.pdf", "外部片段", null, null, 0.5), 0.5);
        when(externalRagRetriever.retrieve(eq(externalKnowledgeBase), eq("安装"), any(RetrievalOptions.class)))
                .thenReturn(Mono.just(List.of(externalChunk)));
        when(chunkRepository.searchKeyword(List.of("kb-a"), "安装", 50))
                .thenReturn(Flux.just(candidate(1, 0.9, "本地片段")));
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(externalKnowledgeBase, localKnowledgeBase), null)))
                .assertNext(result -> assertThat(result.citations())
                        .extracting(Citation::fileName)
                        .containsExactly("external.pdf", "manual.pdf"))
                .verifyComplete();

        verifyNoInteractions(embeddingStore, rerankService);
    }

    @Test
    void keepsTheResultWhenQueryLogWritingFails() {
        var readable = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        when(chunkRepository.searchKeyword(List.of("kb-a"), "安装", 50))
                .thenReturn(Flux.just(candidate(1, 0.9, "安装说明")));
        when(queryLogRepository.insert(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(service.retrieve(request(List.of(readable), null)))
                .assertNext(result -> assertThat(result.citations()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void reranksChunksBeforeAssemblingCitations() {
        var readable = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        readable.getSpec().setRerankModel("rerank-model");
        when(chunkRepository.searchKeyword(List.of("kb-a"), "安装", 50))
                .thenReturn(Flux.just(candidate(1, 0.9, "第一段"), candidate(2, 0.8, "第二段")));
        when(rerankService.rerank("rerank-model", "安装", List.of("第一段", "第二段"), 5))
                .thenReturn(Mono.just(new RerankResult(List.of(
                        new RerankResult.ScoredIndex(1, 0.9),
                        new RerankResult.ScoredIndex(0, 0.2)))));
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(readable), null)))
                .assertNext(result -> {
                    assertThat(result.citations()).extracting(Citation::docId).containsExactly(102L, 101L);
                    assertThat(result.citations()).extracting(Citation::snippet)
                            .containsExactly("第二段", "第一段");
                    assertThat(result.citations()).extracting(Citation::index).containsExactly(1, 2);
                })
                .verifyComplete();

        var queryLogCaptor = ArgumentCaptor.forClass(KnowledgeBaseQueryLog.class);
        verify(queryLogRepository).insert(queryLogCaptor.capture());
        assertThat(queryLogCaptor.getValue().getHitChunkIds()).containsExactly(2L, 1L);
        assertThat(queryLogCaptor.getValue().isRerankUsed()).isTrue();
    }

    @Test
    void usesFirstReadableKnowledgeBaseRerankModel() {
        var unreadable = knowledgeBase("kb-private", KnowledgeBase.Visibility.PRIVATE, "bob", null);
        unreadable.getSpec().setRerankModel("private-model");
        var readable = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        readable.getSpec().setRerankModel("rerank-model");
        when(chunkRepository.searchKeyword(List.of("kb-a"), "安装", 50))
                .thenReturn(Flux.just(candidate(1, 0.9, "第一段")));
        when(rerankService.rerank("rerank-model", "安装", List.of("第一段"), 5))
                .thenReturn(Mono.just(new RerankResult(List.of(new RerankResult.ScoredIndex(0, 0.9)))));
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(unreadable, readable), null)))
                .assertNext(result -> assertThat(result.citations()).hasSize(1))
                .verifyComplete();

        verify(rerankService).rerank("rerank-model", "安装", List.of("第一段"), 5);
    }

    @Test
    void keepsOriginalChunksWhenRerankFails() {
        var readable = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        readable.getSpec().setRerankModel("rerank-model");
        when(chunkRepository.searchKeyword(List.of("kb-a"), "安装", 50))
                .thenReturn(Flux.just(candidate(1, 0.9, "第一段"), candidate(2, 0.8, "第二段")));
        when(rerankService.rerank(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, "ai-rerank-upstream-error", "重排失败。")));
        when(queryLogRepository.insert(any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.retrieve(request(List.of(readable), null)))
                .assertNext(result -> {
                    assertThat(result.citations()).extracting(Citation::docId).containsExactly(101L, 102L);
                    assertThat(result.citations()).extracting(Citation::snippet)
                            .containsExactly("第一段", "第二段");
                })
                .verifyComplete();

        var queryLogCaptor = ArgumentCaptor.forClass(KnowledgeBaseQueryLog.class);
        verify(queryLogRepository).insert(queryLogCaptor.capture());
        assertThat(queryLogCaptor.getValue().isRerankUsed()).isFalse();
    }

    @Test
    void rejectsInvalidRequests() {
        var options = new RetrievalOptions(60, 0.0, 5, 4000);
        assertThatThrownBy(() -> new KnowledgeBaseRetrievalRequest(
                        "alice", null, " ", List.of(), Set.of(), null, null, options, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeBaseRetrievalRequest(
                        "alice", null, "查询", List.of(), Set.of(), null, null, options, 0))
                .isInstanceOf(IllegalArgumentException.class);
        StepVerifier.create(service.retrieve(null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private static KnowledgeBaseRetrievalRequest request(List<KnowledgeBase> knowledgeBases, float[] queryEmbedding) {
        return new KnowledgeBaseRetrievalRequest(
                "alice",
                "req-1",
                "安装",
                knowledgeBases,
                Set.of(),
                "dept-a",
                queryEmbedding,
                new RetrievalOptions(60, 0.0, 5, 4000),
                50);
    }

    private static KnowledgeBase knowledgeBase(
            String code, KnowledgeBase.Visibility visibility, String ownerUserId, String ownerDeptId) {
        var knowledgeBase = new KnowledgeBase();
        var metadata = new Metadata();
        metadata.setName("/registry/pudans.tolim.cn/knowledgebases/" + code);
        knowledgeBase.setMetadata(metadata);
        knowledgeBase.getSpec().setVisibility(visibility);
        knowledgeBase.getSpec().setOwnerUserId(ownerUserId);
        knowledgeBase.getSpec().setOwnerDeptId(ownerDeptId);
        return knowledgeBase;
    }

    private static RetrievalCandidate candidate(long chunkId, double score, String content) {
        return new RetrievalCandidate(chunkId, 100 + chunkId, "kb-a", "manual.pdf", content, 1, "第一章", score);
    }
}
