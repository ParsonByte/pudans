package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiLegacyVideoRequest(
        String model,
        String prompt,
        @JsonProperty("prompt_optimizer") Boolean promptOptimizer,
        @JsonProperty("fast_pretreatment") Boolean fastPretreatment,
        Integer duration,
        String resolution,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark,
        @JsonProperty("first_frame_image") String firstFrameImage,
        @JsonProperty("last_frame_image") String lastFrameImage,
        @JsonProperty("subject_reference") List<AiVideoSubjectReference> subjectReference) {

    public AiLegacyVideoRequest {
        subjectReference =
                subjectReference == null ? List.of() : List.copyOf(subjectReference);
    }
}
