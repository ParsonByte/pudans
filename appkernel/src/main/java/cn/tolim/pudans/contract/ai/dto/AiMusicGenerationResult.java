package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiMusicGenerationResult(
        Map<String, Object> data,
        @JsonProperty("extra_info") Map<String, Object> extraInfo,
        @JsonProperty("analysis_info") Map<String, Object> analysisInfo,
        @JsonProperty("trace_id") String traceId) {}
