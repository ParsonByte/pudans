package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiSpeechTaskSubmitted(
        @JsonProperty("task_id") String taskId, @JsonProperty("file_id") Long fileId) {}
