package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSpeechRequest(
        String text,
        @JsonProperty("voice_id") String voiceId,
        String format,
        @JsonProperty("filter_markdown") Boolean filterMarkdown,
        String model,
        Double speed,
        Double volume,
        Integer pitch,
        String emotion,
        @JsonProperty("sample_rate") Integer sampleRate,
        Integer bitrate,
        Integer channel,
        @JsonProperty("language_boost") String languageBoost,
        Boolean stream,
        @JsonProperty("subtitle_enable") Boolean subtitleEnable) {

    public AiSpeechRequest(String text, String voiceId, String format, Boolean filterMarkdown) {
        this(text, voiceId, format, filterMarkdown, null, null, null, null, null, null, null, null, null, null, null);
    }
}
