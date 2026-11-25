package cn.tolim.pudans.contract.creation;

import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerationTask(
        UUID id,
        String ownerId,
        String workspaceId,
        UUID sessionId,
        UUID runId,
        UUID parentTaskId,
        UUID parentAssetId,
        MediaType mediaType,
        Operation operation,
        UUID modelId,
        String prompt,
        Map<String, Object> settings,
        List<String> inputAssetIds,
        Status status,
        boolean providerRequestSent,
        String providerTaskId,
        List<String> resultAssetIds,
        String errorCode,
        String errorMessage,
        UUID quotaReservationId,
        String idempotencyKey,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public GenerationTask {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (mediaType == null || operation == null || modelId == null || status == null) {
            throw new IllegalArgumentException("mediaType, operation, modelId and status must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        settings = settings == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        inputAssetIds = inputAssetIds == null ? List.of() : List.copyOf(inputAssetIds);
        resultAssetIds = resultAssetIds == null ? List.of() : List.copyOf(resultAssetIds);
    }

    public boolean terminal() {
        return status.terminal();
    }

    public record Attempt(
            UUID id,
            UUID taskId,
            int attemptNo,
            UUID channelId,
            UUID modelId,
            String providerTaskId,
            String providerRequestId,
            AttemptStatus status,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {

        public Attempt {
            if (id == null || taskId == null || status == null) {
                throw new IllegalArgumentException("id, taskId and status must not be null");
            }
            if (attemptNo <= 0) {
                throw new IllegalArgumentException("attemptNo must be positive");
            }
        }
    }

    public enum MediaType {
        IMAGE("image"),
        VIDEO("video");

        private final String code;

        MediaType(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        // 按稳定机器码解析媒体类型。
        public static MediaType fromCode(String code) {
            for (var value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unsupported media type: " + code);
        }
    }

    public enum Operation {
        TEXT("text"),
        REFERENCE("reference"),
        FRAME("frame"),
        EDIT("edit"),
        MOTION("motion"),
        CONTINUE("continue"),
        UPSCALE("upscale"),
        WATERMARK("watermark"),
        LIP_SYNC("lip_sync"),
        SOUND_EFFECT("sound_effect"),
        DEPTH_MAP("depth_map");

        private final String code;

        Operation(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        // 按稳定机器码解析生成操作。
        public static Operation fromCode(String code) {
            for (var value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unsupported operation: " + code);
        }
    }

    public enum Status {
        QUEUED("queued"),
        DISPATCHING("dispatching"),
        PROVIDER_RUNNING("provider_running"),
        DOWNLOADING("downloading"),
        ARCHIVING("archiving"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        CANCELLED("cancelled"),
        RETRY_QUEUED("retry_queued");

        private final String code;

        Status(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED;
        }

        // 状态迁移只允许方案中登记的路径。
        public boolean canTransitionTo(Status next) {
            return switch (this) {
                case QUEUED, RETRY_QUEUED ->
                    next == DISPATCHING || next == FAILED || next == CANCELLED;
                case DISPATCHING ->
                    next == PROVIDER_RUNNING || next == FAILED || next == CANCELLED;
                case PROVIDER_RUNNING ->
                    next == DOWNLOADING || next == FAILED || next == CANCELLED;
                case DOWNLOADING -> next == ARCHIVING || next == FAILED;
                case ARCHIVING -> next == SUCCEEDED || next == FAILED;
                case FAILED -> next == RETRY_QUEUED;
                case SUCCEEDED, CANCELLED -> false;
            };
        }

        // 按数据库机器码还原状态。
        public static Status fromCode(String code) {
            for (var value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unsupported generation task status: " + code);
        }
    }

    public enum AttemptStatus {
        REQUESTING("requesting"),
        SUBMITTED("submitted"),
        RUNNING("running"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        CANCELLED("cancelled");

        private final String code;

        AttemptStatus(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        public boolean active() {
            return this == REQUESTING || this == SUBMITTED || this == RUNNING;
        }

        // 按数据库机器码还原尝试状态。
        public static AttemptStatus fromCode(String code) {
            for (var value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unsupported generation attempt status: " + code);
        }
    }
}
