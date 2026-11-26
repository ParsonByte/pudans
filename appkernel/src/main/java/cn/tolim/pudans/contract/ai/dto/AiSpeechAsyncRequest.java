package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AiSpeechAsyncRequest(
        String model,
        String text,
        @JsonProperty("text_file_id") Long textFileId,
        @JsonProperty("voice_setting") Map<String, Object> voiceSetting,
        @JsonProperty("audio_setting") Map<String, Object> audioSetting,
        @JsonProperty("pronunciation_dict") Map<String, Object> pronunciationDict,
        @JsonProperty("language_boost") String languageBoost,
        @JsonProperty("voice_modify") Map<String, Object> voiceModify,
        @JsonProperty("aigc_watermark") Boolean aigcWatermark) {}
