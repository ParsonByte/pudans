package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 只保留 MiniMax 模型列表接口真实返回的字段。
@JsonIgnoreProperties(ignoreUnknown = true)
public record MiniMaxModel(
        String id,
        Long created,
        @JsonProperty("owned_by") String ownedBy,
        @JsonProperty("display_name") String displayName) {}