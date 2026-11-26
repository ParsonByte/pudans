package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiVideoTaskSubmitted(@JsonProperty("task_id") String taskId) {}
