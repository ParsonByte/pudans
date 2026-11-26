package cn.tolim.pudans.core.ai.provider.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

// 移植自 deepseek4j 1.6.1 并补齐模型目录元数据。
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeepSeekModel(
        String id,
        String object,
        @JsonProperty("owned_by") String ownedBy,
        String name,
        @JsonProperty("context_window") Integer contextWindow,
        @JsonProperty("max_output_tokens") Integer maxOutputTokens,
        @JsonProperty("input_modalities") List<String> inputModalities,
        @JsonProperty("output_modalities") List<String> outputModalities,
        Map<String, Object> effort,
        @JsonProperty("api_capabilities") Map<String, Object> apiCapabilities) {}
