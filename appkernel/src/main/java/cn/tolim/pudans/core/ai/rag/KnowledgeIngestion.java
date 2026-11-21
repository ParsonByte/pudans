package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
import cn.tolim.pudans.core.infra.r2dbc.ai.rag.AiKbChunkRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.rag.AiKbDocumentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestion {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestion.class);

    private static final int MAX_ERROR_LENGTH = 1024;

    private final AiKbDocumentRepository documentRepository;

    private final AiKbChunkRepository chunkRepository;

    private final Chunking chunking;

    private final DocumentParserResolver parserResolver;

    private final ObjectProvider<ChunkEmbeddings> embeddingServiceProvider;

    public Mono<IngestionResult> ingest(KnowledgeBase knowledgeBase, KnowledgeBaseIngestionRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("入库请求不能为空。"));
        }
        if (knowledgeBase == null || knowledgeBase.getSpec() == null) {
            return Mono.error(new IllegalArgumentException("知识库配置不能为空。"));
        }
        String kbName;
        try {
            kbName = KnowledgeBaseCodes.codeOf(knowledgeBase);
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }
        return documentRepository.findByKbNameAndChecksum(kbName, request.checksum())
                .flatMap(existing -> existing.getStatus() == DocumentStatus.FAILED
                        ? reprocess(knowledgeBase, kbName, existing.getId(), request)
                        : Mono.just(new IngestionResult(existing, true)))
                .switchIfEmpty(Mono.defer(() -> create(knowledgeBase, kbName, request)))
                .switchIfEmpty(Mono.error(new IllegalStateException("知识库文档入库失败。")));
    }

    private Mono<IngestionResult> create(
            KnowledgeBase knowledgeBase, String kbName, KnowledgeBaseIngestionRequest request) {
        var document = new KnowledgeBaseDocument();
        document.setKbName(kbName);
        document.setStorageFileId(request.storageFileId());
        document.setFileName(request.fileName());
        document.setMimeType(request.mimeType());
        document.setFileSize((long) request.content().length);
        document.setChecksum(request.checksum());
        document.setStatus(DocumentStatus.PENDING);
        document.setVersion(1);
        document.setUploadedBy(request.uploadedBy());
        return documentRepository.insertPending(document)
                .flatMap(id -> process(knowledgeBase, kbName, id, request))
                .switchIfEmpty(documentRepository.findByKbNameAndChecksum(kbName, request.checksum())
                        .flatMap(existing -> existing.getStatus() == DocumentStatus.FAILED
                                ? reprocess(knowledgeBase, kbName, existing.getId(), request)
                                : Mono.just(new IngestionResult(existing, true))));
    }

    private Mono<IngestionResult> reprocess(
            KnowledgeBase knowledgeBase, String kbName, long documentId, KnowledgeBaseIngestionRequest request) {
        return chunkRepository.deleteByDocumentId(documentId)
                .then(documentRepository.updateStatus(documentId, DocumentStatus.PENDING, 0, 0, null))
                .then(process(knowledgeBase, kbName, documentId, request));
    }

    private Mono<IngestionResult> process(
            KnowledgeBase knowledgeBase, String kbName, long documentId, KnowledgeBaseIngestionRequest request) {
        return prepare(knowledgeBase, request)
                .flatMap(prepared -> {
                    prepared.chunks().forEach(chunk -> {
                        chunk.setKbName(kbName);
                        chunk.setDocumentId(documentId);
                    });
                    return chunkRepository.insertAll(prepared.chunks())
                            .collectList()
                            .flatMap(chunks -> embed(knowledgeBase, chunks))
                            .flatMap(chunks -> documentRepository.updateStatus(
                                    documentId,
                                    DocumentStatus.READY,
                                    chunks.size(),
                                    prepared.tokenCount(),
                                    null));
                })
                .map(document -> new IngestionResult(document, false))
                .onErrorResume(error -> fail(documentId, error));
    }

    private Mono<PreparedChunks> prepare(KnowledgeBase knowledgeBase, KnowledgeBaseIngestionRequest request) {
        return Mono.defer(() -> parserResolver
                        .resolve(request.fileName(), request.mimeType())
                        .parseAsync(request.content()))
                .map(parsed -> {
                    var spec = knowledgeBase.getSpec();
                    var chunks = chunking.chunk(
                            parsed, spec.getChunkStrategy(), spec.getChunkSize(), spec.getChunkOverlap());
                    if (chunks.isEmpty()) {
                        throw new IllegalArgumentException("文档解析后没有可入库的内容。");
                    }
                    var tokenCount = chunks.stream().mapToInt(KnowledgeBaseChunk::getTokenCount).sum();
                    return new PreparedChunks(chunks, tokenCount);
                });
    }

    private Mono<List<KnowledgeBaseChunk>> embed(KnowledgeBase knowledgeBase, List<KnowledgeBaseChunk> chunks) {
        var embeddingService = embeddingServiceProvider.getIfAvailable();
        if (embeddingService == null) {
            return Mono.just(chunks);
        }
        return embeddingService.embed(chunks, knowledgeBase.getSpec().getEmbeddingModel()).thenReturn(chunks);
    }

    private Mono<IngestionResult> fail(long documentId, Throwable error) {
        return chunkRepository.deleteByDocumentId(documentId)
                .then(documentRepository.updateStatus(
                        documentId, DocumentStatus.FAILED, 0, 0, failureMessage(error)))
                .onErrorResume(updateError -> {
                    log.warn("知识库文档失败状态写入失败：{}", documentId, updateError);
                    return Mono.empty();
                })
                .then(Mono.error(error));
    }

    private static String failureMessage(Throwable error) {
        var message = error.getMessage();
        var text = message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    private record PreparedChunks(List<KnowledgeBaseChunk> chunks, int tokenCount) {
    }
}
