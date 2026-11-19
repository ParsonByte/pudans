package cn.tolim.pudans.core.ai.model;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AiModelRequest(
        AiModel.Capability capability,
        List<AiModelContent> inputs,
        Map<String, JsonNode> params,
        boolean stream,
        @Nullable String idempotencyKey) {

    public AiModelRequest {
        if (capability == null) {
            throw new IllegalArgumentException("模型能力不能为空");
        }
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
