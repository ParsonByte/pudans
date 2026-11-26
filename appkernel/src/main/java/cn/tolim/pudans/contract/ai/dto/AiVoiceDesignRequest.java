package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiVoiceDesignRequest(
        String prompt,
        @JsonProperty("preview_text") String previewText,
        @JsonProperty("voice_id") String voiceId,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark) {}
