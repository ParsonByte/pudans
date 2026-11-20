package cn.tolim.pudans.core.ai.harness.output;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.internal.JsonParsingUtils;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public final class StructuredOutputService {

    private static final String DEFAULT_SCHEMA_NAME = "StructuredOutput";

    private static final Pattern INVALID_SCHEMA_NAME_CHARACTER = Pattern.compile("[^A-Za-z0-9_-]");

    private static final String FALLBACK_INSTRUCTION = """
            Respond with only a single JSON value that conforms to the following JSON Schema.
            Do not wrap the JSON in Markdown fences and do not add any other text.
            JSON Schema:
            """;

    private final JsonMapper mapper;

    public StructuredOutputService(JsonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "JsonMapper 不能为空");
    }

    public <T> Mono<T> generate(ChatModel chatModel, StructuredOutputRequest request, Class<T> type) {
        var model = Objects.requireNonNull(chatModel, "ChatModel 不能为空");
        var targetType = Objects.requireNonNull(type, "目标类型不能为空");
        var input = Objects.requireNonNull(request, "结构化输出请求不能为空");
        return Mono.defer(() -> {
            var schema = input.jsonSchema() == null ? schemaFor(targetType) : input.jsonSchema();
            var nativeFormat = supportsNativeSchema(model);
            var messages = messages(input, nativeFormat ? null : fallbackInstruction(schema));
            var chatRequest = nativeFormat
                    ? ChatRequest.builder()
                            .messages(messages)
                            .responseFormat(nativeResponseFormat(schema))
                            .build()
                    : ChatRequest.builder().messages(messages).build();
            return Mono.fromCallable(() -> model.chat(chatRequest))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(response -> parse(response.aiMessage(), targetType));
        });
    }

    private static boolean supportsNativeSchema(ChatModel chatModel) {
        var capabilities = chatModel.supportedCapabilities();
        return capabilities != null
                && capabilities.contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    private static ResponseFormat nativeResponseFormat(JsonSchema schema) {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(schema)
                .build();
    }

    private static JsonSchema schemaFor(Class<?> type) {
        var name = INVALID_SCHEMA_NAME_CHARACTER.matcher(type.getSimpleName()).replaceAll("_");
        if (name.isEmpty()) {
            name = DEFAULT_SCHEMA_NAME;
        }
        try {
            return JsonSchema.builder()
                    .name(name)
                    .rootElement(JsonSchemaElementUtils.jsonSchemaElementFrom(type))
                    .build();
        } catch (RuntimeException error) {
            throw new StructuredOutputException(
                    "目标类型 " + type.getName() + " 无法生成 JSON Schema。", error);
        }
    }

    private static List<ChatMessage> messages(StructuredOutputRequest request, String instruction) {
        var result = new ArrayList<ChatMessage>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            result.add(SystemMessage.from(request.systemPrompt()));
        }
        result.addAll(request.messages());
        if (result.isEmpty()) {
            throw new IllegalArgumentException("结构化输出请求必须包含系统提示或消息。");
        }
        if (instruction != null) {
            if (result.getFirst() instanceof SystemMessage system) {
                result.set(0, SystemMessage.from(system.text() + "\n\n" + instruction));
            } else {
                result.addFirst(SystemMessage.from(instruction));
            }
        }
        return List.copyOf(result);
    }

    private String fallbackInstruction(JsonSchema schema) {
        try {
            var schemaJson = mapper.writeValueAsString(
                    JsonSchemaElementJsonUtils.toMap(schema.rootElement()));
            return FALLBACK_INSTRUCTION + "\n" + schemaJson;
        } catch (JacksonException error) {
            throw new StructuredOutputException("JSON Schema 无法序列化为提示文本。", error);
        }
    }

    private <T> T parse(AiMessage message, Class<T> type) {
        var text = message == null ? null : message.text();
        if (text == null || text.isBlank()) {
            throw new StructuredOutputException(
                    "模型未返回文本内容，无法解析为 " + type.getName() + "。");
        }
        try {
            return JsonParsingUtils.extractAndParseJson(text, json -> mapper.readValue(json, type))
                    .value();
        } catch (Exception error) {
            throw new StructuredOutputException(
                    "模型响应无法解析为 " + type.getName() + "。", error);
        }
    }
}