package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record MiniMaxFile(
        @JsonProperty("file_id") Long fileId,
        Long bytes,
        @JsonProperty("created_at") Long createdAt,
        String filename,
        String purpose) {}
