package cn.tolim.pudans.core.ai.gateway.ir;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record AiTool(String name, String description, JsonNode parameters, JsonNode cacheControl) {

    public AiTool {
        name = Objects.requireNonNull(name, "工具名称不能为空");
    }
}
