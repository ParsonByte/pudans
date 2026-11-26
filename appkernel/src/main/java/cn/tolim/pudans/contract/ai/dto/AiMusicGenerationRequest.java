package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiMusicGenerationRequest(
        String model,
        String prompt,
        String lyrics,
        Boolean stream,
        @JsonProperty("output_format") String outputFormat,
        @JsonProperty("audio_setting") AiAudioSetting audioSetting,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark,
        @JsonProperty("lyrics_optimizer") Boolean lyricsOptimizer,
        @JsonProperty("is_instrumental") Boolean isInstrumental,
        @JsonProperty("audio_url") String audioUrl,
        @JsonProperty("audio_base64") String audioBase64,
        @JsonProperty("cover_feature_id") String coverFeatureId) {}
