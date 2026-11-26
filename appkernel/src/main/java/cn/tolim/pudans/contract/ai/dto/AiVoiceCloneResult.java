package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVoiceCloneResult(
        @JsonProperty("demo_audio") String demoAudio,
        @JsonProperty("extra_info") Map<String, Object> extraInfo,
        @JsonProperty("input_sensitive_type") Integer inputSensitiveType) {}
