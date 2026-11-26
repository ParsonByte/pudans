package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AiFimChoice(
        @JsonProperty("finish_reason") String finishReason,
        Integer index,
        Map<String, Object> logprobs,
        String text) {}
