package cn.tolim.pudans.core.ai.gateway.ir;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record AiRequestParams(
        String model,
        Double temperature,
        Integer maxTokens,
        Double topP,
        Integer topK,
        List<String> stop,
        JsonNode toolChoice,
        Map<String, JsonNode> extra) {

    public AiRequestParams {
        stop = stop == null ? List.of() : List.copyOf(stop);
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }
}
