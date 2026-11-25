package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddingModels;
import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddings;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.vector.MemoryVectorRepository;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapabilityProbe;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryVectorRetrieval {

    private final MemoryEmbeddingModels models;

    private final MemoryEmbeddings embeddings;

    private final MemoryVectorRepository vectors;

    private final PgVectorCapabilityProbe capabilityProbe;

    public record SearchResult(
            List<MemoryVectorRepository.MemoryVectorHit> pool,
            List<MemoryVectorRepository.MemoryVectorHit> personal,
            boolean available,
            String warning) {}

    public Mono<SearchResult> search(RequestIdentity identity, String query, int limit) {
        return capabilityProbe
                .probe()
                .flatMap(capability -> {
                    if (!capability.usable()) {
                        return Mono.just(new SearchResult(
                                List.of(), List.of(), false, capability.reasonCode()));
                    }
                    return models
                            .defaultSpace(identity.workspaceId())
                            .flatMap(space -> embeddings
                                    .embed(space, query)
                                    .flatMap(vector -> Mono.zip(
                                            vectors.searchPool(
                                                            space.vectorSpace(), vector, limit)
                                                    .collectList(),
                                            vectors.searchPersonal(
                                                            space.vectorSpace(),
                                                            identity.ownerId(),
                                                            identity.workspaceId(),
                                                            vector,
                                                            limit)
                                                    .collectList())
                                            .map(hits -> new SearchResult(
                                                    hits.getT1(),
                                                    hits.getT2(),
                                                    true,
                                                    null))));
                })
                .onErrorResume(this::notConfigured,
                        error -> Mono.just(new SearchResult(
                                List.of(), List.of(), false, warningCode(error))))
                .onErrorResume(error -> {
                    if (error instanceof ApiException api
                            && api.getCode().startsWith("memory-vector-dimension")) {
                        return Mono.error(error);
                    }
                    log.warn("记忆向量检索不可用，降级为词法检索", error);
                    return Mono.just(new SearchResult(
                            List.of(), List.of(), false, "memory-vector-search-unavailable"));
                });
    }

    private boolean notConfigured(Throwable error) {
        if (!(error instanceof ApiException api)) {
            return false;
        }
        return api.getCode().startsWith("memory-embedding")
                || api.getCode().startsWith("memory-vector-space")
                || "ai-model-default-missing".equals(api.getCode())
                || "ai-model-unavailable".equals(api.getCode());
    }

    private static String warningCode(Throwable error) {
        return error instanceof ApiException api
                ? api.getCode()
                : "memory-embedding-not-configured";
    }
}
