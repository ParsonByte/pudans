package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AiFimRequest(
        String model,
        String prompt,
        Boolean echo,
        Integer logprobs,
        @JsonProperty("max_tokens") Integer maxTokens,
        Object stop,
        Boolean stream,
        @JsonProperty("stream_options") Map<String, Object> streamOptions,
        String suffix,
        Double temperature,
        @JsonProperty("top_p") Double topP) {}
