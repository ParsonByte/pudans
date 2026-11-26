package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiTranscriptionResult(
        @JsonProperty("request_id") String requestId,
        String text,
        @JsonProperty("duration_ms") Integer durationMs) {}
