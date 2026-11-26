package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiImageGenerationRequest(
        String model,
        String prompt,
        @JsonProperty("disable_prompt_enhanced") Boolean disablePromptEnhanced,
        Integer n,
        String size,
        @JsonProperty("aspect_ratio") String aspectRatio,
        String area,
        @JsonProperty("output_format") String outputFormat,
        @JsonProperty("response_format") String responseFormat,
        Boolean stream,
        @JsonProperty("output_compression") Integer outputCompression,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark,
        List<AiImageInput> images) {

    public AiImageGenerationRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
