package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentProtocolEventMapper {

    private final ObjectMapper mapper;

    public String metadata(String runId, String threadId, @Nullable String agentId, AgentProtocolRunStatus status) {
        var node = mapper.createObjectNode();
        node.put("run_id", runId);
        node.put("thread_id", threadId);
        put(node, "agent_id", agentId);
        node.put("status", status.code());
        return frame("metadata", node);
    }

    public @Nullable String map(AgentEvent event) {
        if (event instanceof AgentEvent.TextBlockDelta delta) {
            var node = mapper.createObjectNode();
            node.put("type", "text");
            node.put("content", delta.delta());
            return frame("updates", node);
        }
        if (event instanceof AgentEvent.ToolCallStart call) {
            var node = mapper.createObjectNode();
            node.put("type", "tool_call");
            node.put("id", call.id());
            node.put("name", call.name());
            put(node, "arguments", call.arguments());
            return frame("updates", node);
        }
        if (event instanceof AgentEvent.ToolResultEnd result) {
            var node = mapper.createObjectNode();
            node.put("type", "tool_result");
            node.put("id", result.id());
            node.put("name", result.name());
            node.put("output", result.output());
            node.put("is_error", result.error());
            return frame("updates", node);
        }
        return null;
    }

    public String end(String runId, String threadId, AgentProtocolRunStatus status, String content) {
        var node = mapper.createObjectNode();
        node.put("run_id", runId);
        node.put("thread_id", threadId);
        node.put("status", status.code());
        node.put("content", content);
        return frame("end", node);
    }

    public String error(String runId, String threadId, AgentProtocolRunStatus status, String error) {
        var node = mapper.createObjectNode();
        node.put("run_id", runId);
        node.put("thread_id", threadId);
        node.put("status", status.code());
        node.put("error", error);
        return frame("error", node);
    }

    private String frame(String event, ObjectNode data) {
        try {
            return "event: " + event + "\ndata: " + mapper.writeValueAsString(data) + "\n\n";
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法序列化 Agent Protocol 事件。", error);
        }
    }

    private static void put(ObjectNode node, String name, @Nullable String value) {
        if (value != null) {
            node.put(name, value);
        }
    }
}