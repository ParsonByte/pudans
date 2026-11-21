package cn.tolim.pudans.core.ai.rag;

import java.util.List;
import reactor.core.publisher.Mono;

public interface ChunkEmbeddings {

    Mono<Void> embed(List<KnowledgeBaseChunk> chunks, String embeddingModel);
}
