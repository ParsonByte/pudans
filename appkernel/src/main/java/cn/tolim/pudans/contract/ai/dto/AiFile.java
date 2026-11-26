package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiFile(
        String id,
        String object,
        Long bytes,
        @JsonProperty("created_at") Long createdAt,
        String filename,
        String purpose,
        @JsonProperty("expires_at") Long expiresAt) {}
