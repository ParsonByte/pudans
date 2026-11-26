package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiVideoContent(
        String type,
        String text,
        @JsonProperty("image_url") AiVideoMedia imageUrl,
        @JsonProperty("video_url") AiVideoMedia videoUrl,
        @JsonProperty("audio_url") AiVideoMedia audioUrl,
        String role) {}
