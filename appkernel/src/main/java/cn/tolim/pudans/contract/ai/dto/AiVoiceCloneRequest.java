package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiVoiceCloneRequest(
        @JsonProperty("file_id") Long fileId,
        @JsonProperty("voice_id") String voiceId,
        @JsonProperty("clone_prompt") AiVoiceClonePrompt clonePrompt,
        String text,
        String model,
        @JsonProperty("language_boost") String languageBoost,
        @JsonProperty("text_validation") String textValidation,
        Double accuracy,
        @JsonProperty("need_noise_reduction") Boolean needNoiseReduction,
        @JsonProperty("need_volume_normalization") Boolean needVolumeNormalization,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark) {}
