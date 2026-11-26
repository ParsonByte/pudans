package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiAudioSetting(
        @JsonProperty("sample_rate") Integer sampleRate, Integer bitrate, String format) {}
