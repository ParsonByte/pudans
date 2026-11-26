package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiVoiceListRequest(@JsonProperty("voice_type") String voiceType) {}
