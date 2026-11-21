package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.embedding.EmbeddingBatch;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.rag.AiKbEmbeddingStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiChunkEmbeddings implements ChunkEmbeddings {

    private static final Logger log = LoggerFactory.getLogger(AiChunkEmbeddings.class);

    private final AiSettings aiSettings;

    private final Embeddings embeddings;

    private final AiKbEmbeddingStore embeddingStore;

    @Override
    public Mono<Void> embed(List<KnowledgeBaseChunk> chunks, String embeddingModel) {
        if (chunks == null || chunks.isEmpty()) {
            return Mono.empty();
        }
        return Mono.defer(() -> embedChunks(chunks, embeddingModel));
    }

    private Mono<Void> embedChunks(List<KnowledgeBaseChunk> chunks, String embeddingModel) {
        for (var chunk : chunks) {
            if (chunk.getId() == null) {
                return Mono.error(new IllegalStateException("知识库分块缺少 ID，无法写入嵌入向量。"));
            }
        }
        var embeddable = chunks.stream().filter(chunk -> StringUtils.hasText(chunk.getContent())).toList();
        if (embeddable.size() < chunks.size()) {
            log.warn("跳过 {} 个空内容的知识库分块。", chunks.size() - embeddable.size());
        }
        if (embeddable.isEmpty()) {
            return Mono.empty();
        }
        var contents = embeddable.stream().map(KnowledgeBaseChunk::getContent).toList();
        return embeddingModelName(embeddingModel)
                .flatMap(name -> embeddings.embed(contents, name))
                .onErrorResume(error -> {
                    if (!isModelMissing(error)) {
                        return Mono.error(error);
                    }
                    log.warn("未配置嵌入模型，分块仅保留关键词检索：{}", error.getMessage());
                    return Mono.empty();
                })
                .flatMap(batch -> updateChunks(embeddable, batch));
    }

    private Mono<String> embeddingModelName(String embeddingModel) {
        if (StringUtils.hasText(embeddingModel)) {
            return Mono.just(embeddingModel);
        }
        return aiSettings.spec()
                .flatMap(spec -> Mono.justOrEmpty(
                        spec.getEmbedding() == null ? null : spec.getEmbedding().getKnowledgeBaseModel()))
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("未配置知识库嵌入模型，分块仅保留关键词检索。");
                    return Mono.empty();
                }));
    }

    private Mono<Void> updateChunks(List<KnowledgeBaseChunk> chunks, EmbeddingBatch batch) {
        if (batch.vectors().size() != chunks.size()) {
            return Mono.error(new IllegalStateException("嵌入向量数量与分块数量不一致。"));
        }
        return Flux.range(0, chunks.size())
                .concatMap(index -> embeddingStore.updateEmbedding(
                        chunks.get(index).getId(), batch.vectors().get(index)))
                .then();
    }

    private static boolean isModelMissing(Throwable error) {
        return error instanceof ApiException apiException
                && Embeddings.MODEL_MISSING_CODE.equals(apiException.getCode());
    }
}
