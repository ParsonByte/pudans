package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiImageGenerationResult(
        Long created,
        List<AiImage> data,
        @JsonProperty("output_format") String outputFormat,
        AiImageUsage usage,
        @JsonProperty("request_id") String requestId) {

    public AiImageGenerationResult {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
