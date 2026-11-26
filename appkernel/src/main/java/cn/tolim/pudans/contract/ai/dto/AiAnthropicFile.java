package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AiAnthropicFile(
        String id,
        String type,
        @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("created_at") Instant createdAt,
        String filename,
        @JsonProperty("mime_type") String mimeType) {}
