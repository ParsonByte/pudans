package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiVoiceDeleteRequest(
        @JsonProperty("voice_type") String voiceType, @JsonProperty("voice_id") String voiceId) {}
