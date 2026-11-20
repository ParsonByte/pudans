package cn.tolim.pudans.core.ai.rag;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.core.extension.ai.KnowledgeBase;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbChunkRepository;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbDocumentRepository;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbQueryLogRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseManagementService {

    public static final String CODE_KB_NOT_FOUND = "ai-kb-not-found";

    public static final String CODE_DOCUMENT_INVALID = "ai-kb-document-invalid";

    public static final String CODE_DOCUMENT_NOT_FOUND = "ai-kb-document-not-found";

    public static final String CODE_FORBIDDEN = "ai-kb-forbidden";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseManagementService.class);

    private static final int RETRIEVAL_CANDIDATE_FACTOR = 3;

    private final ReactiveExtensionClient client;

    private final KnowledgeBaseAcl knowledgeBaseAcl;

    private final KnowledgeBaseRetrievalService retrievalService;

    private final KnowledgeBaseIngestionService ingestionService;

    private final AiKbDocumentRepository documentRepository;

    private final AiKbChunkRepository chunkRepository;

    private final AiKbQueryLogRepository queryLogRepository;

    private final StorageService storageService;

    private final AiEmbeddingService embeddingService;

    public Flux<KnowledgeBase> listReadable(String userId, Set<String> roleNames, @Nullable String deptId) {
        return client.listAll(KnowledgeBase.class, ListOptions.builder().build(), defaultSort())
                .filter(knowledgeBase -> knowledgeBaseAcl.canRead(knowledgeBase, userId, roleNames, deptId));
    }

    public Flux<KnowledgeBase> listVisible(String userId, Set<String> roleNames, @Nullable String deptId) {
        return client.listAll(KnowledgeBase.class, ListOptions.builder().build(), defaultSort())
                .filter(knowledgeBase -> knowledgeBaseAcl.canView(knowledgeBase, userId, roleNames, deptId));
    }

    public Mono<KnowledgeBase> findReadable(
            String name, String userId, Set<String> roleNames, @Nullable String deptId) {
        return client.fetch(KnowledgeBase.class, name)
                .filter(knowledgeBase -> knowledgeBaseAcl.canRead(knowledgeBase, userId, roleNames, deptId))
                .switchIfEmpty(Mono.error(notFound()));
    }

    public Mono<KnowledgeBase> findVisible(
            String name, String userId, Set<String> roleNames, @Nullable String deptId) {
        return client.fetch(KnowledgeBase.class, name)
                .filter(knowledgeBase -> knowledgeBaseAcl.canView(knowledgeBase, userId, roleNames, deptId))
                .switchIfEmpty(Mono.error(notFound()));
    }

    public Mono<KnowledgeBaseRetrievalResult> search(
            String userId,
            Set<String> roleNames,
            @Nullable String deptId,
            String query,
            List<String> kbCodes,
            RetrievalOptions options) {
        return listReadable(userId, roleNames, deptId)
                .filter(knowledgeBase -> kbCodes.isEmpty()
                        || kbCodes.contains(KnowledgeBaseCodes.codeOf(knowledgeBase)))
                .collectList()
                .flatMap(knowledgeBases -> queryEmbedding(query)
                        .flatMap(queryEmbedding -> retrievalService.retrieve(new KnowledgeBaseRetrievalRequest(
                                userId,
                                null,
                                query,
                                knowledgeBases,
                                roleNames,
                                deptId,
                                queryEmbedding,
                                options,
                                Math.max(1, options.topK()) * RETRIEVAL_CANDIDATE_FACTOR))
                                .defaultIfEmpty(new KnowledgeBaseRetrievalResult("", List.of()))));
    }

    private Mono<float[]> queryEmbedding(String query) {
        return embeddingService.embedOne(query).onErrorResume(error -> {
            log.warn("知识库查询向量生成失败，降级为关键词检索。", error);
            return Mono.just(new float[0]);
        });
    }

    public Mono<IngestionResult> uploadDocument(
            KnowledgeBase knowledgeBase,
            String userId,
            String storageFileId,
            @Nullable String fileName) {
        if (!StringUtils.hasText(storageFileId)) {
            return Mono.error(invalidDocument("存储文件标识不能为空。"));
        }
        return storageService.getFile(storageFileId)
                .onErrorMap(
                        ExtensionNotFoundException.class,
                        error -> invalidDocument("存储文件不存在。", error))
                .switchIfEmpty(Mono.error(invalidDocument("存储文件不存在。")))
                .flatMap(file -> requireOwner(file, userId)
                        .then(Mono.defer(() -> readDocument(file, fileName, null)))
                        .flatMap(input -> ingest(knowledgeBase, input, userId)));
    }

    public Mono<ListResult<KnowledgeBaseDocument>> listDocuments(
            String kbName, @Nullable DocumentStatus status, int page, int size) {
        var offset = (long) (page - 1) * size;
        var items = documentRepository.listByKbName(kbName, status, size, offset).collectList();
        var total = documentRepository.countByKbName(kbName, status);
        return Mono.zip(items, total)
                .map(tuple -> new ListResult<>(page, size, tuple.getT2(), tuple.getT1()));
    }

    public Mono<KnowledgeBaseDocument> findDocument(String kbName, long documentId) {
        return documentRepository.findByIdAndKbName(documentId, kbName)
                .switchIfEmpty(Mono.error(documentNotFound()));
    }

    public Mono<Void> deleteDocument(String kbName, long documentId) {
        return findDocument(kbName, documentId)
                .flatMap(ignored -> chunkRepository.deleteByDocumentId(documentId)
                        .then(documentRepository.deleteByIdAndKbName(documentId, kbName))
                        .then());
    }

    public Mono<KnowledgeBaseDocument> retryDocument(
            KnowledgeBase knowledgeBase, long documentId, String userId) {
        var kbName = KnowledgeBaseCodes.codeOf(knowledgeBase);
        return documentRepository.findByIdAndKbName(documentId, kbName)
                .switchIfEmpty(Mono.error(documentNotFound()))
                .flatMap(document -> {
                    if (document.getStatus() != DocumentStatus.FAILED) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT, CODE_DOCUMENT_INVALID, "只有失败的文档可以重试。"));
                    }
                    return storageService
                            .getFile(document.getStorageFileId())
                            .onErrorMap(
                                    ExtensionNotFoundException.class,
                                    error -> invalidDocument("存储文件不存在。", error))
                            .switchIfEmpty(Mono.error(invalidDocument("存储文件不存在。")))
                            .flatMap(file -> readDocument(file, document.getFileName(), document.getChecksum()))
                            .flatMap(input -> ingest(knowledgeBase, input, userId))
                            .map(IngestionResult::document);
                });
    }

    public Mono<Long> reindex(KnowledgeBase knowledgeBase, String userId) {
        var kbName = KnowledgeBaseCodes.codeOf(knowledgeBase);
        return documentRepository
                .findByKbNameAndStatus(kbName, DocumentStatus.READY)
                .concatMap(document -> reindexDocument(knowledgeBase, document, userId)
                        .thenReturn(1L)
                        .onErrorResume(error -> {
                            log.warn("知识库文档重建失败：{}", document.getId(), error);
                            return Mono.just(0L);
                        }))
                .reduce(0L, Long::sum);
    }

    public Flux<KnowledgeBaseQueryLog> listQueryLogs(String kbName, int limit) {
        return queryLogRepository.findRecentByKbName(kbName, limit);
    }

    private Mono<IngestionResult> reindexDocument(
            KnowledgeBase knowledgeBase, KnowledgeBaseDocument document, String userId) {
        return storageService
                .getFile(document.getStorageFileId())
                .onErrorMap(
                        ExtensionNotFoundException.class,
                        error -> invalidDocument("存储文件不存在。", error))
                .switchIfEmpty(Mono.error(invalidDocument("存储文件不存在。")))
                .flatMap(file -> readDocument(file, document.getFileName(), document.getChecksum()))
                .flatMap(input -> chunkRepository
                        .deleteByDocumentId(document.getId())
                        .then(documentRepository.updateStatus(
                                document.getId(), DocumentStatus.FAILED, 0, 0, null))
                        .then(ingest(knowledgeBase, input, userId)));
    }

    private Mono<DocumentInput> readDocument(
            StorageFile file, @Nullable String fileName, @Nullable String checksum) {
        var resolvedName = StringUtils.hasText(fileName) ? fileName : file.getSpec().getOriginalName();
        if (!StringUtils.hasText(resolvedName)) {
            return Mono.error(invalidDocument("文件名不能为空。"));
        }
        if (file.getSpec().getState() != StorageFileState.ACTIVE) {
            return Mono.error(invalidDocument("存储文件不可用。"));
        }
        var fileId = file.getMetadata().getName();
        return storageService
                .open(fileId, StorageReadOptions.full())
                .onErrorMap(error -> error instanceof ApiException
                        ? error
                        : invalidDocument("无法读取存储文件内容。", error))
                .flatMap(result -> DataBufferUtils.join(result.body()))
                .map(KnowledgeBaseManagementService::readBytes)
                .switchIfEmpty(Mono.just(new byte[0]))
                .flatMap(content -> {
                    if (content.length == 0) {
                        return Mono.error(invalidDocument("存储文件内容为空。"));
                    }
                    return Mono.just(new DocumentInput(
                            fileId,
                            resolvedName,
                            file.getSpec().getContentType(),
                            StringUtils.hasText(checksum) ? checksum : sha256(content),
                            content));
                });
    }

    private Mono<IngestionResult> ingest(
            KnowledgeBase knowledgeBase, DocumentInput input, String userId) {
        var request = new KnowledgeBaseIngestionRequest(
                input.storageFileId(),
                input.fileName(),
                input.mimeType(),
                input.checksum(),
                userId,
                input.content());
        return ingestionService
                .ingest(knowledgeBase, request)
                .onErrorMap(
                        IllegalArgumentException.class,
                        error -> invalidDocument(messageOf(error), error));
    }

    private static String messageOf(Throwable error) {
        var message = error.getMessage();
        return StringUtils.hasText(message) ? message : "文档入库失败。";
    }

    private static Mono<Void> requireOwner(StorageFile file, String userId) {
        var owner = file.getSpec().getOwner();
        if (!StringUtils.hasText(owner) || !owner.equals(userId)) {
            return Mono.error(
                    new ApiException(HttpStatus.FORBIDDEN, CODE_FORBIDDEN, "存储文件不属于当前用户。"));
        }
        return Mono.empty();
    }

    private static byte[] readBytes(DataBuffer buffer) {
        try {
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 算法不可用。", error);
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_KB_NOT_FOUND, "知识库不存在或无权访问。");
    }

    private static ApiException documentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_DOCUMENT_NOT_FOUND, "文档不存在。");
    }

    private static ApiException invalidDocument(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_DOCUMENT_INVALID, message);
    }

    private static ApiException invalidDocument(String message, Throwable cause) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_DOCUMENT_INVALID, message, cause);
    }

    private record DocumentInput(
            String storageFileId,
            String fileName,
            @Nullable String mimeType,
            String checksum,
            byte[] content) {}
}
