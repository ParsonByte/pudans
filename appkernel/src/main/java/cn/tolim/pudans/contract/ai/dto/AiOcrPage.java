package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiOcrPage(
        Integer index, String text, @JsonProperty("finish_reason") String finishReason) {}
