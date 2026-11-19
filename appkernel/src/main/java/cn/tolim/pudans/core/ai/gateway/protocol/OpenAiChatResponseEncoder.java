package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OpenAiChatResponseEncoder implements AiResponseEncoder {

    private static final String PROTOCOL = "OpenAI Chat";

    private static final String COMPLETION_OBJECT = "chat.completion";

    private static final String CHUNK_OBJECT = "chat.completion.chunk";

    private static final String DONE = "data: [DONE]\n\n";

    private final ObjectMapper mapper;

    private final long createdAt;

    public OpenAiChatResponseEncoder(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.createdAt = Instant.now().getEpochSecond();
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.OPENAI_CHAT;
    }

    @Override
    public String encode(AiChatResponse response, String model) {
        var root = mapper.createObjectNode();
        root.put("id", Objects.toString(response.id(), ""));
        root.put("object", COMPLETION_OBJECT);
        root.put("created", createdAt);
        root.put("model", Objects.toString(model != null ? model : response.model(), ""));
        var choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        var messageNode = choice.putObject("message");
        messageNode.put("role", "assistant");
        messageNode.put("content", text(response.message()));
        var reasoning = reasoning(response.message());
        if (reasoning != null) {
            messageNode.put("reasoning_content", reasoning);
        }
        var toolCalls = toolCalls(response.message());
        if (!toolCalls.isEmpty()) {
            writeToolCalls(messageNode.putArray("tool_calls"), toolCalls);
        }
        choice.put("finish_reason", finishReason(response.finishReason()));
        root.set("usage", usageNode(mapper, response.usage()));
        return AiProtocolJson.write(mapper, root, PROTOCOL);
    }

    @Override
    public AiStreamEncoder stream(String responseId, String model, AiTokenUsage usage) {
        return new StreamEncoder(Objects.toString(responseId, ""), Objects.toString(model, ""), usage);
    }

    private void writeToolCalls(ArrayNode toolCallsNode, List<AiContentPart.ToolCall> toolCalls) {
        for (var call : toolCalls) {
            var block = toolCallsNode.addObject();
            if (call.id() != null) {
                block.put("id", call.id());
            }
            block.put("type", "function");
            var function = block.putObject("function");
            if (call.name() != null) {
                function.put("name", call.name());
            }
            function.put("arguments", arguments(call.arguments()));
        }
    }

    private String arguments(JsonNode arguments) {
        if (arguments == null) {
            return "{}";
        }
        if (arguments.isTextual()) {
            return arguments.textValue();
        }
        return AiProtocolJson.write(mapper, arguments, PROTOCOL);
    }

    private static String text(AiChatMessage message) {
        if (message == null) {
            return "";
        }
        var text = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text value) {
                text.append(value.text());
            }
        }
        return text.toString();
    }

    private static String reasoning(AiChatMessage message) {
        if (message == null) {
            return null;
        }
        var reasoning = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Thinking thinking) {
                if (!reasoning.isEmpty()) {
                    reasoning.append('\n');
                }
                reasoning.append(thinking.text());
            }
        }
        return reasoning.isEmpty() ? null : reasoning.toString();
    }

    private static List<AiContentPart.ToolCall> toolCalls(AiChatMessage message) {
        if (message == null) {
            return List.of();
        }
        var toolCalls = new ArrayList<AiContentPart.ToolCall>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.ToolCall call) {
                toolCalls.add(call);
            }
        }
        return toolCalls;
    }

    private static String finishReason(String finishReason) {
        if ("length".equals(finishReason) || "tool_calls".equals(finishReason)) {
            return finishReason;
        }
        return "stop";
    }

    private static ObjectNode usageNode(ObjectMapper mapper, AiTokenUsage usage) {
        var input = usage == null || usage.inputTokens() == null ? 0 : usage.inputTokens();
        var output = usage == null || usage.outputTokens() == null ? 0 : usage.outputTokens();
        var cached = usage == null || usage.cacheReadTokens() == null ? 0 : usage.cacheReadTokens();
        var node = mapper.createObjectNode();
        node.put("prompt_tokens", input);
        node.put("completion_tokens", output);
        node.put("total_tokens", input + output);
        node.putObject("prompt_tokens_details").put("cached_tokens", cached);
        return node;
    }

    private static String data(ObjectMapper mapper, ObjectNode chunk) {
        return "data: " + AiProtocolJson.write(mapper, chunk, PROTOCOL) + "\n\n";
    }

    private final class StreamEncoder implements AiStreamEncoder {

        private final String responseId;

        private final String model;

        private final AiTokenUsage initialUsage;

        private final long createdAt = Instant.now().getEpochSecond();

        private StreamEncoder(String responseId, String model, AiTokenUsage initialUsage) {
            this.responseId = responseId;
            this.model = model;
            this.initialUsage = initialUsage;
        }

        @Override
        public String start() {
            var delta = mapper.createObjectNode();
            delta.put("role", "assistant");
            delta.put("content", "");
            return chunk(delta, null, null);
        }

        @Override
        public String event(AiStreamEvent event) {
            if (event == null) {
                return "";
            }
            var delta = mapper.createObjectNode();
            if (event.content() != null) {
                delta.put("content", event.content());
            }
            if (event.reasoning() != null) {
                delta.put("reasoning_content", event.reasoning());
            }
            writeToolCall(delta, event.toolCall());
            if (delta.size() == 0) {
                return "";
            }
            return chunk(delta, null, null);
        }

        @Override
        public String finish(AiStreamEvent event) {
            var reason = finishReason(event == null ? null : event.finishReason());
            var finalUsage = event == null || event.usage() == null ? initialUsage : event.usage();
            return chunk(mapper.createObjectNode(), reason, finalUsage, true) + DONE;
        }

        private void writeToolCall(ObjectNode delta, AiStreamEvent.ToolCallDelta toolCall) {
            if (toolCall == null) {
                return;
            }
            var call = delta.putArray("tool_calls").addObject();
            if (toolCall.index() != null) {
                call.put("index", toolCall.index());
            }
            if (toolCall.id() != null) {
                call.put("id", toolCall.id());
            }
            call.put("type", "function");
            var function = call.putObject("function");
            if (toolCall.name() != null) {
                function.put("name", toolCall.name());
            }
            if (toolCall.arguments() != null) {
                function.put("arguments", toolCall.arguments());
            }
        }

        private String chunk(ObjectNode delta, String reason, AiTokenUsage chunkUsage) {
            return chunk(delta, reason, chunkUsage, chunkUsage != null);
        }

        private String chunk(ObjectNode delta, String reason, AiTokenUsage chunkUsage, boolean includeUsage) {
            var root = mapper.createObjectNode();
            root.put("id", responseId);
            root.put("object", CHUNK_OBJECT);
            root.put("created", createdAt);
            root.put("model", model);
            var choice = root.putArray("choices").addObject();
            choice.put("index", 0);
            choice.set("delta", delta);
            if (reason == null) {
                choice.putNull("finish_reason");
            } else {
                choice.put("finish_reason", reason);
            }
            if (includeUsage) {
                root.set("usage", usageNode(mapper, chunkUsage));
            }
            return data(mapper, root);
        }
    }
}
