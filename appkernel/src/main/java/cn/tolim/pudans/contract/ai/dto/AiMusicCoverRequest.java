package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiMusicCoverRequest(
        String model,
        @JsonProperty("audio_url") String audioUrl,
        @JsonProperty("audio_base64") String audioBase64) {}
