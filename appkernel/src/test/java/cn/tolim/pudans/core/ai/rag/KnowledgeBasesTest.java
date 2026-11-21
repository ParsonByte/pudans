package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbChunkRepository;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbDocumentRepository;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbQueryLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class KnowledgeBasesTest {

    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Mock
    ReactiveExtensionClient client;

    @Mock
    KnowledgeRetrieval retrievalService;

    @Mock
    KnowledgeIngestion ingestionService;

    @Mock
    AiKbDocumentRepository documentRepository;

    @Mock
    AiKbChunkRepository chunkRepository;

    @Mock
    AiKbQueryLogRepository queryLogRepository;

    @Mock
    StorageTransfer storageTransfer;

    @Mock
    Embeddings embeddingService;

    private KnowledgeBases service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBases(
                client,
                new KnowledgeBaseAcl(),
                retrievalService,
                ingestionService,
                documentRepository,
                chunkRepository,
                queryLogRepository,
                storageTransfer,
                embeddingService);
    }

    @Test
    void listsOnlyReadableKnowledgeBases() {
        var company = knowledgeBase("kb-company", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        var restricted = knowledgeBase("kb-private", KnowledgeBase.Visibility.PRIVATE, "uid-bob");
        var granted = knowledgeBase("kb-granted", KnowledgeBase.Visibility.PRIVATE, "uid-bob");
        granted.getSpec().getAcl().add(acl(KnowledgeBase.SubjectType.USER, "uid-alice"));
        when(client.listAll(eq(KnowledgeBase.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(company, restricted, granted));

        StepVerifier.create(service.listReadable("uid-alice", Set.of(), null))
                .expectNext(company, granted)
                .verifyComplete();
    }

    @Test
    void hidesUnreadableKnowledgeBaseDetail() {
        var restricted = knowledgeBase("kb-private", KnowledgeBase.Visibility.PRIVATE, "uid-bob");
        when(client.fetch(KnowledgeBase.class, "kb-private")).thenReturn(Mono.just(restricted));

        StepVerifier.create(service.findReadable("kb-private", "uid-alice", Set.of(), null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiException.getCode())
                            .isEqualTo(KnowledgeBases.CODE_KB_NOT_FOUND);
                })
                .verify();
    }

    @Test
    void searchesReadableKnowledgeBasesWithOptions() {
        var knowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        when(client.listAll(eq(KnowledgeBase.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(knowledgeBase));
        var result = new KnowledgeBaseRetrievalResult("prompt", List.of());
        when(retrievalService.retrieve(any(KnowledgeBaseRetrievalRequest.class)))
                .thenReturn(Mono.just(result));
        var options = new RetrievalOptions(60, 0.3, 8, 4000);

        when(embeddingService.embedOne("hello")).thenReturn(Mono.just(new float[] {0.1f, 0.2f}));

        StepVerifier.create(service.search("uid-alice", Set.of("role-a"), null, "hello", List.of("kb-a"), options))
                .expectNext(result)
                .verifyComplete();

        var request = ArgumentCaptor.forClass(KnowledgeBaseRetrievalRequest.class);
        verify(retrievalService).retrieve(request.capture());
        assertThat(request.getValue().userId()).isEqualTo("uid-alice");
        assertThat(request.getValue().query()).isEqualTo("hello");
        assertThat(request.getValue().knowledgeBases()).containsExactly(knowledgeBase);
        assertThat(request.getValue().roleNames()).containsExactly("role-a");
        assertThat(request.getValue().queryEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(request.getValue().candidateLimit()).isEqualTo(24);
    }

    @Test
    void skipsQueryEmbeddingWhenEveryKnowledgeBaseUsesExternalRetrieval() {
        var knowledgeBase = knowledgeBase("kb-ext", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        var external = new KnowledgeBase.External();
        external.setProvider(KnowledgeBase.ExternalProvider.DIFY);
        external.setEndpoint("https://dify.local");
        knowledgeBase.getSpec().setExternal(external);
        when(client.listAll(eq(KnowledgeBase.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(knowledgeBase));
        var result = new KnowledgeBaseRetrievalResult("prompt", List.of());
        when(retrievalService.retrieve(any(KnowledgeBaseRetrievalRequest.class)))
                .thenReturn(Mono.just(result));
        var options = new RetrievalOptions(60, 0.3, 8, 4000);

        StepVerifier.create(service.search("uid-alice", Set.of(), null, "hello", List.of("kb-ext"), options))
                .expectNext(result)
                .verifyComplete();

        var request = ArgumentCaptor.forClass(KnowledgeBaseRetrievalRequest.class);
        verify(retrievalService).retrieve(request.capture());
        assertThat(request.getValue().queryEmbedding()).isEmpty();
        verifyNoInteractions(embeddingService);
    }

    @Test
    void rejectsUploadFromAnotherUsersStorageFile() {
        var knowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        var file = storageFile("file-1", "uid-bob", "manual.md");
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));

        StepVerifier.create(service.uploadDocument(knowledgeBase, "uid-alice", "file-1", null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiException.getCode()).isEqualTo(KnowledgeBases.CODE_FORBIDDEN);
                })
                .verify();

        verify(storageTransfer, never()).open(any(), any());
        verify(ingestionService, never()).ingest(any(), any());
    }

    @Test
    void rejectsMissingStorageFileOnUpload() {
        var knowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        when(storageTransfer.getFile("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.uploadDocument(knowledgeBase, "uid-alice", "missing", null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiException.getCode())
                            .isEqualTo(KnowledgeBases.CODE_DOCUMENT_INVALID);
                })
                .verify();
    }

    @Test
    void uploadsOwnedStorageFileWithComputedChecksum() {
        var knowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        var file = storageFile("file-1", "uid-alice", "manual.md");
        var content = "hello".getBytes(StandardCharsets.UTF_8);
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageTransfer.open(eq("file-1"), any(StorageReadOptions.class)))
                .thenReturn(Mono.just(readResult(content)));
        var result = ingestionResult(9);
        when(ingestionService.ingest(eq(knowledgeBase), any(KnowledgeBaseIngestionRequest.class)))
                .thenReturn(Mono.just(result));

        StepVerifier.create(service.uploadDocument(knowledgeBase, "uid-alice", "file-1", null))
                .expectNext(result)
                .verifyComplete();

        var request = ArgumentCaptor.forClass(KnowledgeBaseIngestionRequest.class);
        verify(ingestionService).ingest(eq(knowledgeBase), request.capture());
        assertThat(request.getValue().fileName()).isEqualTo("manual.md");
        assertThat(request.getValue().mimeType()).isEqualTo("text/markdown");
        assertThat(request.getValue().uploadedBy()).isEqualTo("uid-alice");
        assertThat(request.getValue().content()).isEqualTo(content);
        assertThat(request.getValue().checksum()).isEqualTo(HELLO_SHA256);
    }

    @Test
    void deletesDocumentChunksAndRow() {
        var document = document(9, DocumentStatus.READY, "checksum");
        when(documentRepository.findByIdAndKbName(9L, "kb-a")).thenReturn(Mono.just(document));
        when(chunkRepository.deleteByDocumentId(9L)).thenReturn(Mono.empty());
        when(documentRepository.deleteByIdAndKbName(9L, "kb-a")).thenReturn(Mono.just(1L));

        StepVerifier.create(service.deleteDocument("kb-a", 9)).verifyComplete();

        verify(chunkRepository).deleteByDocumentId(9L);
        verify(documentRepository).deleteByIdAndKbName(9L, "kb-a");
    }

    @Test
    void rejectsRetryOfReadyDocument() {
        var knowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        var document = document(9, DocumentStatus.READY, "checksum");
        when(documentRepository.findByIdAndKbName(9L, "kb-a")).thenReturn(Mono.just(document));

        StepVerifier.create(service.retryDocument(knowledgeBase, 9, "uid-alice"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getCode())
                            .isEqualTo(KnowledgeBases.CODE_DOCUMENT_INVALID);
                })
                .verify();

        verify(ingestionService, never()).ingest(any(), any());
    }

    @Test
    void retriesFailedDocumentWithStoredChecksum() {
        var knowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        var document = document(9, DocumentStatus.FAILED, "stored-checksum");
        when(documentRepository.findByIdAndKbName(9L, "kb-a")).thenReturn(Mono.just(document));
        var file = storageFile("file-1", "uid-bob", "manual.md");
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageTransfer.open(eq("file-1"), any(StorageReadOptions.class)))
                .thenReturn(Mono.just(readResult("hello".getBytes(StandardCharsets.UTF_8))));
        var result = ingestionResult(9);
        when(ingestionService.ingest(eq(knowledgeBase), any(KnowledgeBaseIngestionRequest.class)))
                .thenReturn(Mono.just(result));

        StepVerifier.create(service.retryDocument(knowledgeBase, 9, "uid-alice"))
                .expectNext(result.document())
                .verifyComplete();

        var request = ArgumentCaptor.forClass(KnowledgeBaseIngestionRequest.class);
        verify(ingestionService).ingest(eq(knowledgeBase), request.capture());
        assertThat(request.getValue().checksum()).isEqualTo("stored-checksum");
    }

    @Test
    void reindexesReadyDocuments() {
        var knowledgeBase = knowledgeBase("kb-a", KnowledgeBase.Visibility.COMPANY, "uid-bob");
        var document = document(9, DocumentStatus.READY, "stored-checksum");
        when(documentRepository.findByKbNameAndStatus("kb-a", DocumentStatus.READY))
                .thenReturn(Flux.just(document));
        var file = storageFile("file-1", "uid-bob", "manual.md");
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageTransfer.open(eq("file-1"), any(StorageReadOptions.class)))
                .thenReturn(Mono.just(readResult("hello".getBytes(StandardCharsets.UTF_8))));
        when(chunkRepository.deleteByDocumentId(9L)).thenReturn(Mono.empty());
        when(documentRepository.updateStatus(eq(9L), eq(DocumentStatus.FAILED), eq(0), eq(0), isNull()))
                .thenReturn(Mono.just(document));
        when(ingestionService.ingest(eq(knowledgeBase), any(KnowledgeBaseIngestionRequest.class)))
                .thenReturn(Mono.just(ingestionResult(9)));

        StepVerifier.create(service.reindex(knowledgeBase, "uid-alice"))
                .expectNext(1L)
                .verifyComplete();

        verify(chunkRepository).deleteByDocumentId(9L);
        verify(documentRepository).updateStatus(eq(9L), eq(DocumentStatus.FAILED), eq(0), eq(0), isNull());
    }

    @Test
    void pagesDocumentsByStatus() {
        var document = document(9, DocumentStatus.READY, "checksum");
        when(documentRepository.listByKbName(eq("kb-a"), eq(DocumentStatus.READY), eq(20), eq(0L)))
                .thenReturn(Flux.just(document));
        when(documentRepository.countByKbName("kb-a", DocumentStatus.READY)).thenReturn(Mono.just(1L));

        StepVerifier.create(service.listDocuments("kb-a", DocumentStatus.READY, 1, 20))
                .assertNext(result -> {
                    assertThat(result.getPage()).isEqualTo(1);
                    assertThat(result.getSize()).isEqualTo(20);
                    assertThat(result.getTotal()).isEqualTo(1);
                    assertThat(result.getItems()).containsExactly(document);
                })
                .verifyComplete();
    }

    @Test
    void listsRecentQueryLogs() {
        var queryLog = new KnowledgeBaseQueryLog();
        queryLog.setQuery("hello");
        when(queryLogRepository.findRecentByKbName("kb-a", 50)).thenReturn(Flux.just(queryLog));

        StepVerifier.create(service.listQueryLogs("kb-a", 50))
                .expectNext(queryLog)
                .verifyComplete();
    }

    private static KnowledgeBase knowledgeBase(
            String name, KnowledgeBase.Visibility visibility, String ownerUserId) {
        var knowledgeBase = new KnowledgeBase();
        var metadata = new Metadata();
        metadata.setName(name);
        knowledgeBase.setMetadata(metadata);
        knowledgeBase.getSpec().setDisplayName(name);
        knowledgeBase.getSpec().setVisibility(visibility);
        knowledgeBase.getSpec().setOwnerUserId(ownerUserId);
        return knowledgeBase;
    }

    private static KnowledgeBase.Acl acl(KnowledgeBase.SubjectType subjectType, String subjectId) {
        var acl = new KnowledgeBase.Acl();
        acl.setSubjectType(subjectType);
        acl.setSubjectId(subjectId);
        acl.setPermission(KnowledgeBase.Permission.READ);
        return acl;
    }

    private static StorageFile storageFile(String name, String owner, String originalName) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName(name);
        file.setMetadata(metadata);
        file.getSpec().setOwner(owner);
        file.getSpec().setOriginalName(originalName);
        file.getSpec().setContentType("text/markdown");
        file.getSpec().setState(StorageFileState.ACTIVE);
        file.getSpec().setSize(5);
        return file;
    }

    private static KnowledgeBaseDocument document(long id, DocumentStatus status, String checksum) {
        var document = new KnowledgeBaseDocument();
        document.setId(id);
        document.setKbName("kb-a");
        document.setStorageFileId("file-1");
        document.setFileName("manual.md");
        document.setMimeType("text/markdown");
        document.setChecksum(checksum);
        document.setStatus(status);
        return document;
    }

    private static IngestionResult ingestionResult(long id) {
        return new IngestionResult(document(id, DocumentStatus.READY, "checksum"), false);
    }

    private static StorageReadResult readResult(byte[] content) {
        DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(content);
        return new StorageReadResult(
                new StorageObject("key", content.length, Instant.now(), "etag"),
                0,
                content.length - 1L,
                content.length,
                Flux.just(buffer));
    }
}
