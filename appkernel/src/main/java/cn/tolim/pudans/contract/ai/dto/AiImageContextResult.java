package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiImageContextResult(
        Long created,
        @JsonProperty("revised_prompt") String revisedPrompt,
        @JsonProperty("aspect_ratio") String aspectRatio,
        AiImageUsage usage,
        @JsonProperty("request_id") String requestId) {}
