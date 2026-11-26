package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiVideoRegenerationRequest(
        String model,
        @JsonProperty("source_task_id") String sourceTaskId,
        List<AiVideoContent> content,
        String resolution,
        @JsonProperty("generate_audio") Boolean generateAudio,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark) {

    public AiVideoRegenerationRequest {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
