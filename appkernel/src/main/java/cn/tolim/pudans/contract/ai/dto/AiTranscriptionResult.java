package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiTranscriptionResult(
        @JsonProperty("request_id") String requestId,
        String text,
        @JsonProperty("duration_ms") Integer durationMs,
        Integer speakers,
        List<AiTranscriptionSegment> segments) {

    public AiTranscriptionResult(String requestId, String text, Integer durationMs) {
        this(requestId, text, durationMs, null, List.of());
    }

    public AiTranscriptionResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
