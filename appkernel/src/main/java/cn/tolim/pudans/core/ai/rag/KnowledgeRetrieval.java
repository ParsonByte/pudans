package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.core.ai.rag.connector.ExternalRagRetriever;
import cn.tolim.pudans.core.ai.rerank.Reranking;
import cn.tolim.pudans.core.ai.rerank.RerankResult;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbChunkRepository;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbEmbeddingStore;
import cn.tolim.pudans.infra.r2dbc.ai.rag.AiKbQueryLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrieval {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrieval.class);

    private static final int SCORE_SCALE = 4;

    private final AiKbChunkRepository chunkRepository;

    private final AiKbEmbeddingStore embeddingStore;

    private final AiKbQueryLogRepository queryLogRepository;

    private final HybridRetrieval hybridRetrieval;

    private final KnowledgeBaseAcl knowledgeBaseAcl;

    private final CitationAssembler citationAssembler;

    private final Reranking reranking;

    private final ExternalRagRetriever externalRagRetriever;

    public Mono<KnowledgeBaseRetrievalResult> retrieve(KnowledgeBaseRetrievalRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("检索请求不能为空。"));
        }
        var startedAt = System.nanoTime();
        var readableKnowledgeBases = request.knowledgeBases().stream()
                .filter(candidate -> knowledgeBaseAcl.canRead(
                        candidate, request.userId(), request.roleNames(), request.deptId()))
                .toList();
        var kbNames = readableKnowledgeBases.stream()
                .map(KnowledgeBaseCodes::codeOf)
                .distinct()
                .toList();
        if (kbNames.isEmpty()) {
            return writeQueryLog(request, List.of(), List.of(), false, startedAt)
                    .thenReturn(new KnowledgeBaseRetrievalResult("", List.of()));
        }
        var externalKnowledgeBases = readableKnowledgeBases.stream()
                .filter(ExternalRagRetriever::isExternal)
                .toList();
        var localKnowledgeBases = readableKnowledgeBases.stream()
                .filter(knowledgeBase -> !ExternalRagRetriever.isExternal(knowledgeBase))
                .toList();
        var externalChunks = Flux.fromIterable(externalKnowledgeBases)
                .concatMap(knowledgeBase -> externalRagRetriever.retrieve(
                        knowledgeBase, request.query(), request.options()))
                .collectList()
                .map(lists -> lists.stream().flatMap(List::stream).toList());
        return Mono.zip(externalChunks, retrieveLocal(localKnowledgeBases, request))
                .map(tuple -> {
                    var chunks = mergeChunks(tuple.getT1(), tuple.getT2().chunks(), request.options());
                    return new FusedResult(
                            citationAssembler.assemble(chunks), chunks, tuple.getT2().reranked());
                })
                .flatMap(fused -> writeQueryLog(
                                request, kbNames, fused.chunks(), fused.reranked(), startedAt)
                        .thenReturn(new KnowledgeBaseRetrievalResult(
                                fused.prompt().prompt(), fused.prompt().citations())));
    }

    private Mono<RerankOutcome> retrieveLocal(
            List<KnowledgeBase> knowledgeBases, KnowledgeBaseRetrievalRequest request) {
        if (knowledgeBases.isEmpty()) {
            return Mono.just(new RerankOutcome(List.of(), false));
        }
        var kbNames = knowledgeBases.stream()
                .map(KnowledgeBaseCodes::codeOf)
                .distinct()
                .toList();
        var keywordCandidates = chunkRepository.searchKeyword(
                kbNames, request.query(), request.candidateLimit());
        Mono<List<RetrievalCandidate>> vectorCandidates =
                request.queryEmbedding() == null || request.queryEmbedding().length == 0
                        ? Mono.just(List.of())
                        : embeddingStore.searchCandidates(vectorSearchRequest(
                                kbNames, request.queryEmbedding(), request.candidateLimit()));
        return Mono.zip(keywordCandidates.collectList(), vectorCandidates)
                .flatMap(candidates -> retrieveChunks(
                        candidates.getT1(), candidates.getT2(), knowledgeBases, request));
    }

    private static List<RetrievedChunk> mergeChunks(
            List<RetrievedChunk> externalChunks, List<RetrievedChunk> localChunks, RetrievalOptions options) {
        var merged = new ArrayList<RetrievedChunk>(externalChunks.size() + localChunks.size());
        merged.addAll(externalChunks);
        merged.addAll(localChunks);
        var limited = merged.stream().limit(options.topK()).toList();
        return HybridRetrieval.applyCharBudget(limited, options.charBudget());
    }

    private static EmbeddingSearchRequest vectorSearchRequest(
            List<String> kbNames, float[] queryVector, int candidateLimit) {
        return EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(queryVector))
                .maxResults(candidateLimit)
                .filter(MetadataFilterBuilder.metadataKey("kb_name").isIn(kbNames))
                .build();
    }

    private Mono<RerankOutcome> retrieveChunks(
            List<RetrievalCandidate> keywordCandidates,
            List<RetrievalCandidate> vectorCandidates,
            List<KnowledgeBase> readableKnowledgeBases,
            KnowledgeBaseRetrievalRequest request) {
        var chunks = hybridRetrieval.retrieve(keywordCandidates, vectorCandidates, request.options());
        var modelName = rerankModel(readableKnowledgeBases);
        if (chunks.isEmpty() || modelName == null) {
            return Mono.just(new RerankOutcome(chunks, false));
        }
        var documents = chunks.stream().map(chunk -> chunk.candidate().content()).toList();
        return Mono.defer(() -> reranking.rerank(
                        modelName, request.query(), documents, request.options().topK()))
                .map(result -> new RerankOutcome(
                        reorder(chunks, result, request.options().topK()), true))
                .switchIfEmpty(Mono.just(new RerankOutcome(chunks, false)))
                .onErrorResume(error -> {
                    log.warn("知识库重排失败，保留原始检索结果。", error);
                    return Mono.just(new RerankOutcome(chunks, false));
                });
    }

    private static String rerankModel(List<KnowledgeBase> knowledgeBases) {
        for (var knowledgeBase : knowledgeBases) {
            var spec = knowledgeBase == null ? null : knowledgeBase.getSpec();
            if (spec != null && StringUtils.hasText(spec.getRerankModel())) {
                return spec.getRerankModel();
            }
        }
        return null;
    }

    private static List<RetrievedChunk> reorder(List<RetrievedChunk> chunks, RerankResult result, int topK) {
        var reordered = new ArrayList<RetrievedChunk>(Math.min(result.results().size(), topK));
        for (var scored : result.results()) {
            if (reordered.size() >= topK) {
                break;
            }
            reordered.add(chunks.get(scored.index()));
        }
        return List.copyOf(reordered);
    }

    private Mono<Void> writeQueryLog(
            KnowledgeBaseRetrievalRequest request,
            List<String> kbNames,
            List<RetrievedChunk> chunks,
            boolean reranked,
            long startedAt) {
        var queryLog = new KnowledgeBaseQueryLog();
        queryLog.setUserId(request.userId());
        queryLog.setRequestId(request.requestId());
        queryLog.setKbNames(List.copyOf(kbNames));
        queryLog.setQuery(request.query());
        queryLog.setRerankUsed(reranked);
        queryLog.setHitChunkIds(chunks.stream().map(chunk -> chunk.candidate().chunkId()).toList());
        queryLog.setHitCount(chunks.size());
        if (!chunks.isEmpty()) {
            queryLog.setTopScore(BigDecimal.valueOf(chunks.get(0).score()).setScale(SCORE_SCALE, RoundingMode.HALF_UP));
        }
        queryLog.setLatencyMs(latencyMillis(startedAt));
        return queryLogRepository.insert(queryLog)
                .then()
                .onErrorResume(error -> {
                    log.warn("知识库检索日志写入失败。", error);
                    return Mono.empty();
                });
    }

    private static int latencyMillis(long startedAt) {
        var elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return (int) Math.min(Integer.MAX_VALUE, elapsed);
    }

    private record RerankOutcome(List<RetrievedChunk> chunks, boolean reranked) {}

    private record FusedResult(
            CitationAssembler.KnowledgePrompt prompt, List<RetrievedChunk> chunks, boolean reranked) {}

}
