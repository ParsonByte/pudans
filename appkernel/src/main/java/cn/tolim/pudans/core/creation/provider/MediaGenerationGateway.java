package cn.tolim.pudans.core.creation.provider;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MediaGenerationGateway {

    // 供应商标识必须与模型目录中的 provider 一致。
    String provider();

    // 提交生成请求，clientReference 作为供应商侧幂等键。
    Mono<Submission> submit(Request request);

    // 查询供应商任务；未知客户端引用必须返回 UNKNOWN，不能伪造成功。
    Mono<State> find(Reference reference);

    // 尽力取消供应商任务；不承诺退款。
    Mono<Void> cancel(Reference reference);

    record Request(
            String ownerId,
            String workspaceId,
            String clientReference,
            UUID taskId,
            UUID attemptId,
            UUID channelId,
            UUID modelId,
            String providerModel,
            GenerationTask.MediaType mediaType,
            GenerationTask.Operation operation,
            String prompt,
            Map<String, Object> settings,
            List<String> inputAssetIds,
            UUID parentAssetId) {}

    record Submission(
            String providerTaskId,
            String providerRequestId,
            UUID channelId,
            State state) {}

    record Reference(
            String clientReference,
            String providerTaskId,
            String providerRequestId,
            UUID channelId,
            UUID modelId,
            String providerModel,
            GenerationTask.MediaType mediaType) {}

    record State(
            Phase phase,
            String providerTaskId,
            String providerRequestId,
            String errorCode,
            String errorMessage,
            Result result) {}

    record Result(List<Asset> assets) {

        public Result {
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }

    record Asset(
            String providerAssetId,
            String originalName,
            String contentType,
            long size,
            Flux<DataBuffer> body) {}

    enum Phase {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        UNKNOWN
    }

    class Failure extends RuntimeException {

        private final String code;

        private final boolean requestSent;

        // 保存稳定错误码和请求是否可能已到达供应商。
        public Failure(String code, String message, boolean requestSent) {
            super(message);
            this.code = code;
            this.requestSent = requestSent;
        }

        // 保存底层异常并标记请求结果未知。
        public Failure(String code, String message, boolean requestSent, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.requestSent = requestSent;
        }

        public String code() {
            return code;
        }

        public boolean requestSent() {
            return requestSent;
        }
    }
}
