package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AiFimUsage(
        @JsonProperty("prompt_tokens") long promptTokens,
        @JsonProperty("completion_tokens") long completionTokens,
        @JsonProperty("total_tokens") long totalTokens,
        @JsonProperty("prompt_tokens_details") Map<String, Object> promptTokensDetails,
        @JsonProperty("prompt_cache_hit_tokens") long promptCacheHitTokens,
        @JsonProperty("prompt_cache_miss_tokens") long promptCacheMissTokens,
        @JsonProperty("completion_tokens_details") Map<String, Object> completionTokensDetails,
        @JsonProperty("safety_tokens") Long safetyTokens,
        @JsonProperty("server_tool_use") Map<String, Object> serverToolUse) {

    public AiFimUsage {
        promptTokensDetails =
                promptTokensDetails == null ? Map.of() : Map.copyOf(promptTokensDetails);
        completionTokensDetails =
                completionTokensDetails == null ? Map.of() : Map.copyOf(completionTokensDetails);
        serverToolUse = serverToolUse == null ? Map.of() : Map.copyOf(serverToolUse);
    }
}
