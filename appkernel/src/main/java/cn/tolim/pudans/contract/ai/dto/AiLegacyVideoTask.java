package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiLegacyVideoTask(
        @JsonProperty("task_id") String taskId,
        String status,
        @JsonProperty("file_id") String fileId,
        @JsonProperty("video_width") Integer videoWidth,
        @JsonProperty("video_height") Integer videoHeight) {}
