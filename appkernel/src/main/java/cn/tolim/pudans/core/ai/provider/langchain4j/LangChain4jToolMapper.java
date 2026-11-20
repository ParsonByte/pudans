package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class LangChain4jToolMapper {

    private LangChain4jToolMapper() {}

    public static List<ToolSpecification> toSpecifications(List<AiTool> tools) {
        var mapper = JsonUtils.mapper();
        var result = new ArrayList<ToolSpecification>();
        for (var tool : tools) {
            var node = mapper.createObjectNode();
            node.put("name", tool.name());
            if (tool.description() != null) {
                node.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                node.set("parameters", tool.parameters());
            }
            try {
                result.add(ToolSpecification.fromJson(mapper.writeValueAsString(node)));
            } catch (JsonProcessingException error) {
                throw new IllegalArgumentException("工具定义无效：" + tool.name(), error);
            }
        }
        return result;
    }

    public static JsonNode parameters(ToolSpecification tool) {
        var mapper = JsonUtils.mapper();
        if (tool.parameters() == null) {
            return mapper.createObjectNode();
        }
        try {
            var root = mapper.readTree(tool.toJson());
            var parameters = root == null ? null : root.get("parameters");
            return parameters == null ? mapper.createObjectNode() : parameters;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("无法解析工具参数：" + tool.name(), error);
        }
    }

    public static JsonNode arguments(String arguments) {
        var mapper = JsonUtils.mapper();
        if (!StringUtils.hasText(arguments)) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(arguments);
        } catch (JsonProcessingException error) {
            return mapper.getNodeFactory().textNode(arguments);
        }
    }
}