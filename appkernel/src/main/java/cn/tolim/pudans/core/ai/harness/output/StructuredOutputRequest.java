package cn.tolim.pudans.core.ai.harness.output;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.List;
import java.util.Objects;

public record StructuredOutputRequest(
        String systemPrompt, List<ChatMessage> messages, JsonSchema jsonSchema) {

    public StructuredOutputRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static StructuredOutputRequest of(String systemPrompt, String userPrompt) {
        return new StructuredOutputRequest(
                systemPrompt,
                List.of(UserMessage.from(Objects.requireNonNull(userPrompt, "用户消息不能为空"))),
                null);
    }

    public static StructuredOutputRequest of(List<ChatMessage> messages) {
        return new StructuredOutputRequest(null, messages, null);
    }

    public StructuredOutputRequest withJsonSchema(JsonSchema jsonSchema) {
        return new StructuredOutputRequest(
                systemPrompt,
                messages,
                Objects.requireNonNull(jsonSchema, "JSON Schema 不能为空"));
    }
}