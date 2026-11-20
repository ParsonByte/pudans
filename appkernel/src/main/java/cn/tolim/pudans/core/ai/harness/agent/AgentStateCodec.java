package cn.tolim.pudans.core.ai.harness.agent;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.harness.plan.TodoItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AgentStateCodec {

    private AgentStateCodec() {}

    public static String encode(AgentState state) {
        var root = JsonUtils.mapper().createObjectNode();
        root.put("summary", state.summary());
        root.set("attributes", JsonUtils.mapper().valueToTree(state.attributes()));
        var plan = root.putObject("planMode");
        plan.put("active", state.planMode().active());
        plan.put("content", state.planMode().planContent());
        var todos = root.putArray("todos");
        for (var todo : state.todos()) {
            var node = todos.addObject();
            node.put("id", todo.id());
            node.put("content", todo.content());
            node.put("status", todo.status());
        }
        var messages = root.putArray("messages");
        for (var message : state.messages()) {
            messages.add(message(message));
        }
        return root.toString();
    }

    public static AgentState decode(String json) {
        if (json == null || json.isBlank()) {
            return new AgentState();
        }
        try {
            var root = JsonUtils.mapper().readTree(json);
            var state = new AgentState();
            state.setSummary(root.path("summary").asText(""));
            var attributes = root.get("attributes");
            if (attributes != null && attributes.isObject()) {
                state = new AgentState(
                        List.of(),
                        state.summary(),
                        JsonUtils.mapper().convertValue(
                                attributes, new TypeReference<Map<String, Object>>() {}));
            }
            var plan = root.get("planMode");
            if (plan != null && plan.isObject()) {
                state.planMode().restore(plan.path("active").asBoolean(false), plan.path("content").asText(""));
            }
            var todos = new ArrayList<TodoItem>();
            var todoNodes = root.get("todos");
            if (todoNodes != null && todoNodes.isArray()) {
                for (var node : todoNodes) {
                    todos.add(new TodoItem(
                            node.path("id").asText(""),
                            node.path("content").asText(""),
                            node.path("status").asText("pending")));
                }
            }
            state.setTodos(todos);
            var messages = new ArrayList<ChatMessage>();
            var messageNodes = root.get("messages");
            if (messageNodes != null && messageNodes.isArray()) {
                for (var node : messageNodes) {
                    var message = message(node);
                    if (message != null) {
                        messages.add(message);
                    }
                }
            }
            state.replaceMessages(messages);
            return state;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("AgentState JSON 无法解析。", error);
        }
    }

    private static ObjectNode message(ChatMessage message) {
        var node = JsonUtils.mapper().createObjectNode();
        if (message instanceof UserMessage user) {
            node.put("type", "user");
            var text = new StringBuilder();
            for (var content : user.contents()) {
                if (content instanceof TextContent value) {
                    text.append(value.text());
                }
            }
            node.put("text", text.toString());
            return node;
        }
        if (message instanceof AiMessage ai) {
            node.put("type", "ai");
            if (ai.text() != null) {
                node.put("text", ai.text());
            }
            if (ai.thinking() != null) {
                node.put("thinking", ai.thinking());
            }
            var calls = node.putArray("toolCalls");
            for (var call : ai.toolExecutionRequests()) {
                var callNode = calls.addObject();
                callNode.put("id", call.id());
                callNode.put("name", call.name());
                callNode.put("arguments", call.arguments());
            }
            return node;
        }
        if (message instanceof ToolExecutionResultMessage tool) {
            node.put("type", "tool");
            node.put("id", tool.id());
            node.put("toolName", tool.toolName());
            node.put("text", tool.text());
            node.put("error", tool.isError());
            return node;
        }
        node.put("type", "system");
        node.put("text", ((SystemMessage) message).text());
        return node;
    }

    private static ChatMessage message(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return switch (node.path("type").asText()) {
            case "user" -> UserMessage.from(node.path("text").asText(""));
            case "ai" -> ai(node);
            case "tool" -> ToolExecutionResultMessage.builder()
                    .id(node.path("id").asText(""))
                    .toolName(node.path("toolName").asText(""))
                    .text(node.path("text").asText(""))
                    .isError(node.path("error").asBoolean(false))
                    .build();
            case "system" -> SystemMessage.from(node.path("text").asText(""));
            default -> null;
        };
    }

    private static AiMessage ai(JsonNode node) {
        var builder = AiMessage.builder();
        if (node.hasNonNull("text")) {
            builder.text(node.get("text").asText());
        }
        if (node.hasNonNull("thinking")) {
            builder.thinking(node.get("thinking").asText());
        }
        var calls = new ArrayList<ToolExecutionRequest>();
        var callNodes = node.get("toolCalls");
        if (callNodes instanceof ArrayNode array) {
            for (var call : array) {
                calls.add(ToolExecutionRequest.builder()
                        .id(call.path("id").asText(""))
                        .name(call.path("name").asText(""))
                        .arguments(call.path("arguments").asText("{}"))
                        .build());
            }
        }
        return builder.toolExecutionRequests(calls).build();
    }
}