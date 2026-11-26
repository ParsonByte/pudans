package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiVoiceClonePrompt(
        @JsonProperty("prompt_audio") Long promptAudio,
        @JsonProperty("prompt_text") String promptText) {}
