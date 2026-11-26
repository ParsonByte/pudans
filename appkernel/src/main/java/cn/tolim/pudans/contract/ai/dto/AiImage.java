package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiImage(
        String url,
        @JsonProperty("b64_json") String b64Json,
        String size,
        @JsonProperty("revised_prompt") String revisedPrompt,
        AiImageError error) {}
