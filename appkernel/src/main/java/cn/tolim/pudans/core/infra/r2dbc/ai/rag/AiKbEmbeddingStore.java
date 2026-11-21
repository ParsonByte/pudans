package cn.tolim.pudans.core.infra.r2dbc.ai.rag;

import cn.tolim.pudans.core.ai.rag.RetrievalCandidate;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiKbEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String KB_NAME = "kb_name";

    private static final String DOCUMENT_ID = "document_id";

    private static final String FILE_NAME = "file_name";

    private static final String PAGE_NO = "page_no";

    private static final String HEADING_PATH = "heading_path";

    private final AiKbChunkRepository chunkRepository;

    @Override
    public String add(Embedding embedding) {
        throw unsupportedWrite();
    }

    @Override
    public void add(String id, Embedding embedding) {
        updateEmbedding(chunkId(id), embedding.vector()).block();
    }

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        throw unsupportedWrite();
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw unsupportedWrite();
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return searchReactive(request).block();
    }

    @Override
    public CompletableFuture<EmbeddingSearchResult<TextSegment>> searchAsync(EmbeddingSearchRequest request) {
        return searchReactive(request).toFuture();
    }

    public Mono<Void> updateEmbedding(long chunkId, float[] vector) {
        return chunkRepository
                .updateEmbedding(chunkId, vector)
                .flatMap(updated -> Boolean.TRUE.equals(updated)
                        ? Mono.empty()
                        : Mono.error(new IllegalStateException("知识库分块不存在，无法写入向量：" + chunkId)));
    }

    public Mono<List<RetrievalCandidate>> searchCandidates(EmbeddingSearchRequest request) {
        return searchReactive(request).map(result -> result.matches().stream()
                .map(AiKbEmbeddingStore::candidate)
                .toList());
    }

    public Mono<EmbeddingSearchResult<TextSegment>> searchReactive(EmbeddingSearchRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("向量检索请求不能为空。"));
        }
        if (request.queryEmbedding().vector().length == 0) {
            return Mono.error(new IllegalArgumentException("查询向量不能为空。"));
        }
        List<String> kbNames;
        try {
            kbNames = kbNames(request.filter());
        } catch (RuntimeException error) {
            return Mono.error(error);
        }
        return chunkRepository
                .searchVectorCandidates(
                        kbNames, request.queryEmbedding().vector(), request.maxResults())
                .filter(candidate -> candidate.candidate().score() >= request.minScore())
                .map(AiKbEmbeddingStore::match)
                .collectList()
                .map(EmbeddingSearchResult::new);
    }

    private static EmbeddingMatch<TextSegment> match(AiKbChunkRepository.VectorCandidate candidate) {
        var retrieved = candidate.candidate();
        var metadata = new Metadata();
        metadata.put(KB_NAME, retrieved.kbName());
        metadata.put(DOCUMENT_ID, retrieved.documentId());
        metadata.put(FILE_NAME, retrieved.fileName());
        if (retrieved.pageNo() != null) {
            metadata.put(PAGE_NO, retrieved.pageNo());
        }
        if (StringUtils.hasText(retrieved.headingPath())) {
            metadata.put(HEADING_PATH, retrieved.headingPath());
        }
        return new EmbeddingMatch<>(
                retrieved.score(),
                String.valueOf(retrieved.chunkId()),
                Embedding.from(candidate.embedding()),
                TextSegment.from(retrieved.content(), metadata));
    }

    private static RetrievalCandidate candidate(EmbeddingMatch<TextSegment> match) {
        var segment = match.embedded();
        var metadata = segment.metadata();
        return new RetrievalCandidate(
                chunkId(match.embeddingId()),
                requiredLong(metadata, DOCUMENT_ID),
                metadata.getString(KB_NAME),
                metadata.getString(FILE_NAME),
                segment.text(),
                metadata.getInteger(PAGE_NO),
                metadata.getString(HEADING_PATH),
                match.score());
    }

    private static List<String> kbNames(Filter filter) {
        if (filter instanceof IsIn in && KB_NAME.equals(in.key())) {
            return in.comparisonValues().stream().map(String::valueOf).toList();
        }
        if (filter instanceof IsEqualTo equal && KB_NAME.equals(equal.key())) {
            return List.of(String.valueOf(equal.comparisonValue()));
        }
        throw new IllegalArgumentException("知识库向量检索需要 kb_name 过滤条件。");
    }

    private static long chunkId(String id) {
        try {
            var chunkId = Long.parseLong(id);
            if (chunkId <= 0) {
                throw new IllegalArgumentException("知识库分块标识必须为正数：" + id);
            }
            return chunkId;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("知识库分块标识格式无效：" + id, error);
        }
    }

    private static long requiredLong(Metadata metadata, String key) {
        var value = metadata.getLong(key);
        if (value == null) {
            throw new IllegalArgumentException("知识库向量元数据缺少 " + key + "。");
        }
        return value;
    }

    private static UnsupportedOperationException unsupportedWrite() {
        return new UnsupportedOperationException("知识库向量写入必须指定已有分块标识。");
    }
}