package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVoiceDeletion(
        @JsonProperty("voice_id") String voiceId, @JsonProperty("created_time") String createdTime) {}
