package cn.tolim.pudans.core.ai.embedding;

import cn.tolim.pudans.infra.exception.ApiException;
import io.agentscope.core.message.TextBlock;
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
        return space.embeddingModel()
                .embed(TextBlock.builder().text(text).build())
                .subscribeOn(Schedulers.boundedElastic())
                .map(MemoryEmbeddings::toFloat)
                .map(vector -> validateDimension(vector, space.vectorSpace().getDimension()));
    }

    private static float[] toFloat(double[] values) {
        if (values == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "memory-embedding-response-invalid",
                    "Embedding 服务没有返回向量。");
        }
        var vector = new float[values.length];
        for (var index = 0; index < values.length; index++) {
            vector[index] = (float) values[index];
        }
        return vector;
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
