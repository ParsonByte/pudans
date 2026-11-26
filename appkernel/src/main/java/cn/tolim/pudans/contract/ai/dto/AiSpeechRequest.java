package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSpeechRequest(
        String text,
        @JsonProperty("voice_id") String voiceId,
        String format,
        @JsonProperty("filter_markdown") Boolean filterMarkdown) {}
