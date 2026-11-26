package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiImageUsage(
        @JsonProperty("generated_images") Integer generatedImages,
        @JsonProperty("total_tokens") Integer totalTokens,
        @JsonProperty("input_tokens") Integer inputTokens,
        @JsonProperty("output_tokens") Integer outputTokens,
        @JsonProperty("input_tokens_details") Map<String, Object> inputTokensDetails,
        @JsonProperty("output_tokens_details") Map<String, Object> outputTokensDetails) {}
