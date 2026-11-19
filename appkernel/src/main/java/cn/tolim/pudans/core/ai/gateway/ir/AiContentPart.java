package cn.tolim.pudans.core.ai.gateway.ir;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public sealed interface AiContentPart {

    JsonNode cacheControl();

    record Text(String text, JsonNode cacheControl) implements AiContentPart {

        public Text {
            text = text == null ? "" : text;
        }
    }

    record Image(String mediaType, String data, String url, String detail, JsonNode cacheControl)
            implements AiContentPart {}

    record File(String mediaType, String data, String url, String filename, String fileId, JsonNode cacheControl)
            implements AiContentPart {}

    record ToolCall(String id, String name, JsonNode arguments, JsonNode cacheControl) implements AiContentPart {}

    record ToolResult(String toolCallId, JsonNode content, Boolean error, JsonNode cacheControl)
            implements AiContentPart {}

    record Thinking(String text, String signature, JsonNode cacheControl) implements AiContentPart {

        public Thinking {
            text = text == null ? "" : text;
        }
    }

    record Custom(String type, JsonNode raw) implements AiContentPart {

        public Custom {
            type = type == null ? "custom" : type;
            raw = Objects.requireNonNull(raw, "原始 block 不能为空");
        }

        @Override
        public JsonNode cacheControl() {
            var value = raw.get("cache_control");
            return value == null || value.isNull() ? null : value;
        }
    }
}
