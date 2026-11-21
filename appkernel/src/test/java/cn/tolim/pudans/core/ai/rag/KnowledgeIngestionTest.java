package cn.tolim.pudans.core.ai.rag;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbChunkRepository;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionTest {

    @Mock
    AiKbDocumentRepository documentRepository;

    @Mock
    AiKbChunkRepository chunkRepository;

    @Mock
    ObjectProvider<ChunkEmbeddings> embeddingServiceProvider;

    private KnowledgeIngestion service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIngestion(
                documentRepository,
                chunkRepository,
                new Chunking(),
                new DocumentParserResolver(List.of(new TxtDocumentParser(), new MarkdownDocumentParser())),
                embeddingServiceProvider);
    }

    @Test
    void ingestsADocumentAndMarksItReady() {
        var request = request("hello world");
        when(documentRepository.findByKbNameAndChecksum("kb-a", "sum")).thenReturn(Mono.empty());
        when(documentRepository.insertPending(any())).thenReturn(Mono.just(7L));
        var inserted = new ArrayList<List<KnowledgeBaseChunk>>();
        when(chunkRepository.insertAll(anyList())).thenAnswer(invocation -> {
            List<KnowledgeBaseChunk> chunks = invocation.getArgument(0);
            inserted.add(List.copyOf(chunks));
            for (var index = 0; index < chunks.size(); index++) {
                chunks.get(index).setId(100L + index);
            }
            return Flux.fromIterable(chunks);
        });
        when(documentRepository.updateStatus(7L, DocumentStatus.READY, 1, 2, null))
                .thenReturn(Mono.just(document(DocumentStatus.READY, 7L)));

        StepVerifier.create(service.ingest(knowledgeBase(), request))
                .assertNext(result -> {
                    assertThat(result.deduplicated()).isFalse();
                    assertThat(result.document().getStatus()).isEqualTo(DocumentStatus.READY);
                })
                .verifyComplete();

        verify(embeddingServiceProvider).getIfAvailable();
        var documentCaptor = ArgumentCaptor.forClass(KnowledgeBaseDocument.class);
        verify(documentRepository).insertPending(documentCaptor.capture());
        var pending = documentCaptor.getValue();
        assertThat(pending.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(pending.getKbName()).isEqualTo("kb-a");
        assertThat(pending.getChecksum()).isEqualTo("sum");
        assertThat(pending.getFileSize()).isEqualTo(11L);
        assertThat(pending.getVersion()).isEqualTo(1);
        assertThat(inserted).singleElement().satisfies(chunks -> {
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getKbName()).isEqualTo("kb-a");
            assertThat(chunks.get(0).getDocumentId()).isEqualTo(7L);
            assertThat(chunks.get(0).getContent()).isEqualTo("hello world");
        });
    }

    @Test
    void reusesAnExistingDocumentWithTheSameChecksum() {
        var existing = document(DocumentStatus.READY, 9L);
        when(documentRepository.findByKbNameAndChecksum("kb-a", "sum")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.ingest(knowledgeBase(), request("hello world")))
                .assertNext(result -> {
                    assertThat(result.deduplicated()).isTrue();
                    assertThat(result.document()).isSameAs(existing);
                })
                .verifyComplete();

        verifyNoInteractions(chunkRepository);
        verify(documentRepository, never()).insertPending(any());
    }

    @Test
    void reprocessesAFailedDocumentWithTheSameChecksum() {
        when(documentRepository.findByKbNameAndChecksum("kb-a", "sum"))
                .thenReturn(Mono.just(document(DocumentStatus.FAILED, 9L)));
        when(chunkRepository.deleteByDocumentId(9L)).thenReturn(Mono.empty());
        when(documentRepository.updateStatus(9L, DocumentStatus.PENDING, 0, 0, null))
                .thenReturn(Mono.just(document(DocumentStatus.PENDING, 9L)));
        when(chunkRepository.insertAll(anyList())).thenAnswer(invocation -> {
            List<KnowledgeBaseChunk> chunks = invocation.getArgument(0);
            chunks.forEach(chunk -> chunk.setId(300L));
            return Flux.fromIterable(chunks);
        });
        when(documentRepository.updateStatus(9L, DocumentStatus.READY, 1, 2, null))
                .thenReturn(Mono.just(document(DocumentStatus.READY, 9L)));

        StepVerifier.create(service.ingest(knowledgeBase(), request("hello world")))
                .assertNext(result -> assertThat(result.document().getStatus()).isEqualTo(DocumentStatus.READY))
                .verifyComplete();

        verify(documentRepository, never()).insertPending(any());
        verify(chunkRepository).deleteByDocumentId(9L);
    }

    @Test
    void callsTheReservedEmbeddingServiceWhenAvailable() {
        ChunkEmbeddings embeddingService = mock(ChunkEmbeddings.class);
        when(embeddingServiceProvider.getIfAvailable()).thenReturn(embeddingService);
        when(documentRepository.findByKbNameAndChecksum("kb-a", "sum")).thenReturn(Mono.empty());
        when(documentRepository.insertPending(any())).thenReturn(Mono.just(7L));
        when(chunkRepository.insertAll(anyList())).thenAnswer(invocation -> {
            List<KnowledgeBaseChunk> chunks = invocation.getArgument(0);
            chunks.forEach(chunk -> chunk.setId(100L));
            return Flux.fromIterable(chunks);
        });
        when(embeddingService.embed(anyList(), eq("embedding-model"))).thenReturn(Mono.empty());
        when(documentRepository.updateStatus(7L, DocumentStatus.READY, 1, 2, null))
                .thenReturn(Mono.just(document(DocumentStatus.READY, 7L)));

        StepVerifier.create(service.ingest(knowledgeBase(), request("hello world")))
                .expectNextCount(1)
                .verifyComplete();

        verify(embeddingService).embed(anyList(), eq("embedding-model"));
    }

    @Test
    void marksTheDocumentFailedWhenNoParserSupportsIt() {
        var unsupported = new KnowledgeIngestion(
                documentRepository,
                chunkRepository,
                new Chunking(),
                new DocumentParserResolver(List.of()),
                embeddingServiceProvider);
        when(documentRepository.findByKbNameAndChecksum("kb-a", "sum")).thenReturn(Mono.empty());
        when(documentRepository.insertPending(any())).thenReturn(Mono.just(7L));
        when(chunkRepository.deleteByDocumentId(7L)).thenReturn(Mono.empty());
        when(documentRepository.updateStatus(7L, DocumentStatus.FAILED, 0, 0, "不支持的文档类型。"))
                .thenReturn(Mono.just(document(DocumentStatus.FAILED, 7L)));

        StepVerifier.create(unsupported.ingest(knowledgeBase(), request("hello world")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("不支持的文档类型"))
                .verify();

        verify(documentRepository).updateStatus(7L, DocumentStatus.FAILED, 0, 0, "不支持的文档类型。");
        verify(documentRepository, never()).updateStatus(
                eq(7L), eq(DocumentStatus.READY), anyInt(), anyInt(), any());
    }

    @Test
    void rejectsAnEmptyRequest() {
        assertThatThrownBy(() -> new KnowledgeBaseIngestionRequest(
                        "file-1", "manual.txt", "text/plain", " ", "alice", "text".getBytes(UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        StepVerifier.create(service.ingest(null, request("text")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private static KnowledgeBaseIngestionRequest request(String text) {
        return new KnowledgeBaseIngestionRequest(
                "file-1", "manual.txt", "text/plain", "sum", "alice", text.getBytes(UTF_8));
    }

    private static KnowledgeBase knowledgeBase() {
        var knowledgeBase = new KnowledgeBase();
        var metadata = new Metadata();
        metadata.setName("/registry/pudans.tolim.cn/knowledgebases/kb-a");
        knowledgeBase.setMetadata(metadata);
        knowledgeBase.getSpec().setEmbeddingModel("embedding-model");
        knowledgeBase.getSpec().setChunkStrategy(KnowledgeBase.ChunkStrategy.RECURSIVE);
        knowledgeBase.getSpec().setChunkSize(100);
        knowledgeBase.getSpec().setChunkOverlap(0);
        return knowledgeBase;
    }

    private static KnowledgeBaseDocument document(DocumentStatus status, long id) {
        var document = new KnowledgeBaseDocument();
        document.setId(id);
        document.setStatus(status);
        return document;
    }
}
