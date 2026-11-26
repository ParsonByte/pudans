package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record AiVideoGenerationRequest(
        String model,
        List<AiVideoContent> content,
        String resolution,
        Integer duration,
        String ratio,
        Map<String, Object> extra,
        @JsonProperty("generate_audio") Boolean generateAudio,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark) {

    public AiVideoGenerationRequest {
        content = content == null ? List.of() : List.copyOf(content);
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }
}
