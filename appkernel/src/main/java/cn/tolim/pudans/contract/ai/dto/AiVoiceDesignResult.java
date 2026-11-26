package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVoiceDesignResult(
        @JsonProperty("voice_id") String voiceId,
        @JsonProperty("trial_audio") String trialAudio) {}
