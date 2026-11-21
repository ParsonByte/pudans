package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

public final class AnthropicMessagesResponseEncoder implements AiResponseEncoder {

    private static final String PROTOCOL = "Anthropic";

    private static final String MESSAGE_START = "message_start";

    private static final String CONTENT_BLOCK_START = "content_block_start";

    private static final String CONTENT_BLOCK_DELTA = "content_block_delta";

    private static final String CONTENT_BLOCK_STOP = "content_block_stop";

    private static final String MESSAGE_DELTA = "message_delta";

    private static final String MESSAGE_STOP = "message_stop";

    private static final String TEXT_BLOCK = "text";

    private static final String THINKING_BLOCK = "thinking";

    private static final String TOOL_USE_BLOCK = "tool_use";

    private final ObjectMapper mapper;

    public AnthropicMessagesResponseEncoder(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.ANTHROPIC;
    }

    @Override
    public String encode(AiChatResponse response, String model) {
        var root = mapper.createObjectNode();
        root.put("id", Objects.toString(response.id(), ""));
        root.put("type", "message");
        root.put("role", "assistant");
        root.put("model", Objects.toString(model != null ? model : response.model(), ""));
        var content = root.putArray("content");
        var message = response.message();
        if (message != null) {
            for (var part : message.parts()) {
                writeBlock(content, part);
            }
        }
        root.put("stop_reason", stopReason(response.finishReason()));
        root.putNull("stop_sequence");
        root.set("usage", usage(response.usage()));
        return AiProtocolJson.write(mapper, root, PROTOCOL);
    }

    @Override
    public AiStreamEncoder stream(String responseId, String model, AiTokenUsage usage) {
        return new StreamEncoder(Objects.toString(responseId, ""), Objects.toString(model, ""), usage);
    }

    private void writeBlock(ArrayNode content, AiContentPart part) {
        // 助手输出只保留文本、思考与工具调用块。
        if (part instanceof AiContentPart.Thinking thinking) {
            var block = content.addObject();
            block.put("type", THINKING_BLOCK);
            block.put("thinking", thinking.text());
            if (thinking.signature() != null) {
                block.put("signature", thinking.signature());
            }
        } else if (part instanceof AiContentPart.Text text) {
            var block = content.addObject();
            block.put("type", TEXT_BLOCK);
            block.put("text", text.text());
        } else if (part instanceof AiContentPart.ToolCall call) {
            var block = content.addObject();
            block.put("type", TOOL_USE_BLOCK);
            if (call.id() != null) {
                block.put("id", call.id());
            }
            if (call.name() != null) {
                block.put("name", call.name());
            }
            block.set("input", call.arguments() == null ? mapper.createObjectNode() : call.arguments());
        }
    }

    private static String stopReason(String finishReason) {
        if (finishReason == null) {
            return "end_turn";
        }
        return switch (finishReason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> "end_turn";
        };
    }

    private ObjectNode usage(AiTokenUsage usage) {
        var node = mapper.createObjectNode();
        node.put("input_tokens", usage == null || usage.inputTokens() == null ? 0 : usage.inputTokens());
        node.put("output_tokens", usage == null || usage.outputTokens() == null ? 0 : usage.outputTokens());
        putCacheUsage(node, usage);
        return node;
    }

    private void putCacheUsage(ObjectNode node, AiTokenUsage usage) {
        if (usage != null && usage.cacheReadTokens() != null) {
            node.put("cache_read_input_tokens", usage.cacheReadTokens());
        }
        if (usage != null && usage.cacheWriteTokens() != null) {
            node.put("cache_creation_input_tokens", usage.cacheWriteTokens());
        }
    }

    private ObjectNode eventNode(String type) {
        var node = mapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    private String sse(String type, ObjectNode data) {
        return "event: " + type + "\ndata: " + AiProtocolJson.write(mapper, data, PROTOCOL) + "\n\n";
    }

    private final class StreamEncoder implements AiStreamEncoder {

        private final String responseId;

        private final String model;

        private final AiTokenUsage initialUsage;

        private int nextIndex;

        private int blockIndex = -1;

        private String blockType;

        private Integer toolIndex;

        private StreamEncoder(String responseId, String model, AiTokenUsage initialUsage) {
            this.responseId = responseId;
            this.model = model;
            this.initialUsage = initialUsage;
        }

        @Override
        public String start() {
            var events = new StringBuilder();
            var start = eventNode(MESSAGE_START);
            var message = start.putObject("message");
            message.put("id", responseId);
            message.put("type", "message");
            message.put("role", "assistant");
            message.put("model", model);
            message.putArray("content");
            message.putNull("stop_reason");
            message.putNull("stop_sequence");
            message.set("usage", startUsage());
            events.append(sse(MESSAGE_START, start));
            openBlock(events, TEXT_BLOCK, "text");
            return events.toString();
        }

        @Override
        public String event(AiStreamEvent event) {
            if (event == null) {
                return "";
            }
            var events = new StringBuilder();
            if (event.content() != null) {
                ensureText(events);
                appendContentDelta(events, "text_delta", "text", event.content());
            }
            if (event.reasoning() != null) {
                ensureThinking(events);
                appendContentDelta(events, "thinking_delta", "thinking", event.reasoning());
            }
            var toolCall = event.toolCall();
            if (toolCall != null) {
                ensureTool(events, toolCall);
                if (toolCall.arguments() != null) {
                    appendContentDelta(events, "input_json_delta", "partial_json", toolCall.arguments());
                }
            }
            return events.toString();
        }

        @Override
        public String finish(AiStreamEvent event) {
            var events = new StringBuilder();
            closeBlock(events);
            var messageDelta = eventNode(MESSAGE_DELTA);
            var delta = messageDelta.putObject("delta");
            delta.put("stop_reason", stopReason(event == null ? null : event.finishReason()));
            messageDelta.set("usage", finalUsage(event == null ? null : event.usage()));
            events.append(sse(MESSAGE_DELTA, messageDelta));
            events.append(sse(MESSAGE_STOP, eventNode(MESSAGE_STOP)));
            return events.toString();
        }

        private void ensureText(StringBuilder events) {
            if (!TEXT_BLOCK.equals(blockType)) {
                closeBlock(events);
                openBlock(events, TEXT_BLOCK, "text");
            }
        }

        private void ensureThinking(StringBuilder events) {
            if (!THINKING_BLOCK.equals(blockType)) {
                closeBlock(events);
                openBlock(events, THINKING_BLOCK, "thinking");
            }
        }

        private void ensureTool(StringBuilder events, AiStreamEvent.ToolCallDelta toolCall) {
            var sameTool = TOOL_USE_BLOCK.equals(blockType)
                    && (toolCall.index() == null || toolIndex == null || toolCall.index().equals(toolIndex));
            if (!sameTool) {
                closeBlock(events);
                openTool(events, toolCall);
            } else if (toolCall.index() != null) {
                toolIndex = toolCall.index();
            }
        }

        private void openBlock(StringBuilder events, String type, String textField) {
            var event = eventNode(CONTENT_BLOCK_START);
            event.put("index", nextIndex);
            var block = event.putObject("content_block");
            block.put("type", type);
            block.put(textField, "");
            events.append(sse(CONTENT_BLOCK_START, event));
            blockIndex = nextIndex++;
            blockType = type;
            toolIndex = null;
        }

        private void openTool(StringBuilder events, AiStreamEvent.ToolCallDelta toolCall) {
            var event = eventNode(CONTENT_BLOCK_START);
            event.put("index", nextIndex);
            var block = event.putObject("content_block");
            block.put("type", TOOL_USE_BLOCK);
            if (toolCall.id() != null) {
                block.put("id", toolCall.id());
            }
            if (toolCall.name() != null) {
                block.put("name", toolCall.name());
            }
            block.putObject("input");
            events.append(sse(CONTENT_BLOCK_START, event));
            blockIndex = nextIndex++;
            blockType = TOOL_USE_BLOCK;
            toolIndex = toolCall.index();
        }

        private void closeBlock(StringBuilder events) {
            if (blockType == null) {
                return;
            }
            var event = eventNode(CONTENT_BLOCK_STOP);
            event.put("index", blockIndex);
            events.append(sse(CONTENT_BLOCK_STOP, event));
            blockType = null;
            blockIndex = -1;
            toolIndex = null;
        }

        private void appendContentDelta(StringBuilder events, String deltaType, String field, String value) {
            var event = eventNode(CONTENT_BLOCK_DELTA);
            event.put("index", blockIndex);
            var delta = event.putObject("delta");
            delta.put("type", deltaType);
            delta.put(field, value);
            events.append(sse(CONTENT_BLOCK_DELTA, event));
        }

        private ObjectNode startUsage() {
            var node = mapper.createObjectNode();
            node.put("input_tokens", initialUsage == null || initialUsage.inputTokens() == null
                    ? 0
                    : initialUsage.inputTokens());
            node.put("output_tokens", 0);
            putCacheUsage(node, initialUsage);
            return node;
        }

        private ObjectNode finalUsage(AiTokenUsage eventUsage) {
            var effective = eventUsage != null ? eventUsage : initialUsage;
            var node = mapper.createObjectNode();
            node.put("output_tokens", effective == null || effective.outputTokens() == null
                    ? 0
                    : effective.outputTokens());
            putCacheUsage(node, effective);
            return node;
        }
    }
}
