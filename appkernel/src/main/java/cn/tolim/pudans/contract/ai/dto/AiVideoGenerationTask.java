package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVideoGenerationTask(
        String id,
        String model,
        String status,
        AiVideoError error,
        @JsonProperty("created_at") Long createdAt,
        @JsonProperty("updated_at") Long updatedAt,
        AiVideoTaskContent content,
        String resolution,
        Integer duration,
        Map<String, Object> usage,
        String ratio,
        @JsonProperty("task_type") String taskType,
        String modality) {}
