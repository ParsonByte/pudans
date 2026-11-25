package cn.tolim.pudans.core.ai.embedding;

import cn.tolim.pudans.infra.exception.ApiException;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class MemoryEmbeddings {

    public Mono<float[]> embed(MemoryEmbeddingModels.Space space, String text) {
        if (space == null || space.embeddingModel() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "memory-embedding-model-not-configured",
                    "Embedding 模型尚未配置。"));
        }
        return Mono.fromCallable(() -> space.embeddingModel().embed(text))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> vector(response.content()))
                .map(vector -> validateDimension(vector, space.vectorSpace().getDimension()));
    }

    private static float[] vector(Embedding embedding) {
        if (embedding == null || embedding.vector() == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "memory-embedding-response-invalid",
                    "Embedding 服务没有返回向量。");
        }
        return embedding.vector();
    }

    private static float[] validateDimension(float[] vector, int expected) {
        if (vector == null || vector.length != expected) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "memory-vector-dimension-mismatch",
                    "Embedding 向量维度与向量空间登记不一致。");
        }
        return vector;
    }
}