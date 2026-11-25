package cn.tolim.pudans.contract.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum AiModelType {
    CHAT("chat", "chat"),
    EMBEDDING("embedding", "embedding"),
    IMAGE("image", "image"),
    RERANK("rerank", "rerank"),
    VIDEO("video", "video"),
    AUDIO("audio", "audio"),
    THREE_D("3d", "3d"),
    UNKNOWN("unknown", null);

    private final String value;

    private final String capability;

    AiModelType(String value, String capability) {
        this.value = value;
        this.capability = capability;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String capability() {
        return capability;
    }

    public boolean supported() {
        return capability != null;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AiModelType fromValue(String value) {
        return find(value).orElse(UNKNOWN);
    }

    public static Optional<AiModelType> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var normalized = value.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type != UNKNOWN && type.value.equals(normalized))
                .findFirst();
    }

    public static AiModelType fromCatalogType(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return switch (value.trim().toLowerCase()) {
            case "chat", "language" -> CHAT;
            case "embedding" -> EMBEDDING;
            case "image", "image-generation" -> IMAGE;
            case "reranking", "rerank" -> RERANK;
            case "video" -> VIDEO;
            case "speech", "audio", "transcription", "speech-to-text", "text-to-speech", "tts", "asr" -> AUDIO;
            case "3d", "3d-generation", "3d-model", "three-d" -> THREE_D;
            default -> UNKNOWN;
        };
    }
}
