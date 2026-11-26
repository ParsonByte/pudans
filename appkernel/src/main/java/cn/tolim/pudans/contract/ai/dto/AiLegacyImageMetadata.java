package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiLegacyImageMetadata(
        @JsonProperty("success_count") Integer successCount,
        @JsonProperty("failed_count") Integer failedCount) {}
