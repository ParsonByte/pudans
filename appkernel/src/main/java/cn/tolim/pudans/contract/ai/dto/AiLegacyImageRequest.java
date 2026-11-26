package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record AiLegacyImageRequest(
        String model,
        String prompt,
        @JsonProperty("subject_reference") List<AiImageSubjectReference> subjectReference,
        Map<String, Object> style,
        @JsonProperty("aspect_ratio") String aspectRatio,
        Integer width,
        Integer height,
        @JsonProperty("response_format") String responseFormat,
        Long seed,
        Integer n,
        @JsonProperty("prompt_optimizer") Boolean promptOptimizer,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark) {

    public AiLegacyImageRequest {
        subjectReference =
                subjectReference == null ? List.of() : List.copyOf(subjectReference);
        style = style == null ? Map.of() : Map.copyOf(style);
    }
}
