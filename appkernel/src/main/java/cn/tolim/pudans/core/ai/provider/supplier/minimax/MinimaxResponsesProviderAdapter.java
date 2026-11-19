package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiProviderIrSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.ai.provider.OpenAiResponsesProviderAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class MinimaxResponsesProviderAdapter extends OpenAiResponsesProviderAdapter {

    private static final Set<String> INPUT_ITEMS =
            Set.of("message", "function_call", "function_call_output", "reasoning");

    private final ObjectMapper mapper;

    public MinimaxResponsesProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, mapper, accountService);
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.MINIMAX_RESPONSES;
    }

    @Override
    protected void writeExtras(ObjectNode root, AiRequestParams params) {
        params.extra().forEach(root::set);
    }

    @Override
    protected void writeMessage(ArrayNode input, AiChatMessage message) {
        if ("latest_reminder".equals(message.role())) {
            addMessageItem(input, "system", message.parts());
            return;
        }
        var content = new ArrayList<AiContentPart>();
        var reasoning = new ArrayList<AiContentPart.Thinking>();
        var toolCalls = new ArrayList<AiContentPart.ToolCall>();
        var toolResults = new ArrayList<AiContentPart.ToolResult>();
        var items = new ArrayList<JsonNode>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.ToolResult result) {
                toolResults.add(result);
            } else if (part instanceof AiContentPart.ToolCall call) {
                toolCalls.add(call);
            } else if (part instanceof AiContentPart.Thinking thinking) {
                reasoning.add(thinking);
            } else if (part instanceof AiContentPart.Custom custom && isInputItem(custom.raw())) {
                items.add(custom.raw());
            } else {
                content.add(part);
            }
        }
        if ("assistant".equals(message.role())) {
            for (var thinking : reasoning) {
                addReasoning(input, thinking);
            }
            if (!content.isEmpty()) {
                addMessageItem(input, "assistant", content);
            }
            for (var call : toolCalls) {
                addFunctionCall(input, call);
            }
        } else {
            if (!content.isEmpty()) {
                addMessageItem(input, responsesRole(message.role()), content);
            }
            for (var result : toolResults) {
                addFunctionCallOutput(input, result);
            }
        }
        items.forEach(input::add);
    }

    private void addMessageItem(ArrayNode input, String role, List<AiContentPart> parts) {
        var item = input.addObject();
        item.put("type", "message");
        item.put("role", role);
        if (parts.size() == 1 && parts.get(0) instanceof AiContentPart.Text text) {
            item.put("content", text.text());
            return;
        }
        var content = item.putArray("content");
        for (var part : parts) {
            writeContentBlock(content, role, part);
        }
    }

    private void writeContentBlock(ArrayNode content, String role, AiContentPart part) {
        if (part instanceof AiContentPart.Text text) {
            var block = content.addObject();
            block.put("type", "assistant".equals(role) ? "output_text" : "input_text");
            block.put("text", text.text());
        } else if (part instanceof AiContentPart.Image image) {
            var block = content.addObject();
            block.put("type", "input_image");
            block.put("image_url", imageUrl(image));
            if (StringUtils.hasText(image.detail())) {
                block.put("detail", detail(image.detail()));
            }
        } else if (part instanceof AiContentPart.File file) {
            writeFileBlock(content, file);
        } else if (part instanceof AiContentPart.Custom custom) {
            content.add(custom.raw());
        }
    }

    private void writeFileBlock(ArrayNode content, AiContentPart.File file) {
        var url = fileUrl(file);
        var block = content.addObject();
        if (file.mediaType() != null && file.mediaType().regionMatches(true, 0, "video/", 0, 6)) {
            block.put("type", "input_video");
            block.put("video_url", url);
            return;
        }
        if (file.mediaType() != null && file.mediaType().regionMatches(true, 0, "image/", 0, 6)) {
            block.put("type", "input_image");
            block.put("image_url", url);
            return;
        }
        throw new AiProtocolException("MiniMax Responses 仅支持图片或视频文件输入");
    }

    private void addReasoning(ArrayNode input, AiContentPart.Thinking thinking) {
        var item = input.addObject();
        item.put("type", "reasoning");
        var summary = item.putArray("summary");
        var block = summary.addObject();
        block.put("type", "summary_text");
        block.put("text", thinking.text());
    }

    private void addFunctionCall(ArrayNode input, AiContentPart.ToolCall call) {
        var item = input.addObject();
        item.put("type", "function_call");
        if (call.id() != null) {
            item.put("call_id", call.id());
        }
        if (call.name() != null) {
            item.put("name", call.name());
        }
        item.put("arguments",
                AiProviderJsonSupport.argumentsText(mapper, call.arguments(), MinimaxProtocol.NAME));
    }

    private void addFunctionCallOutput(ArrayNode input, AiContentPart.ToolResult result) {
        var item = input.addObject();
        item.put("type", "function_call_output");
        if (result.toolCallId() != null) {
            item.put("call_id", result.toolCallId());
        }
        if (result.content() == null || result.content().isNull()) {
            item.put("output", "");
        } else if (result.content().isTextual()) {
            item.put("output", result.content().textValue());
        } else if (result.content().isArray()) {
            item.set("output", result.content());
        } else {
            item.put("output", result.content().toString());
        }
    }

    private static boolean isInputItem(JsonNode raw) {
        return raw != null && raw.isObject()
                && INPUT_ITEMS.contains(text(raw, "type"));
    }

    private static String responsesRole(String role) {
        if (AiProviderIrSupport.isSystem(role)) {
            return "system";
        }
        return "tool".equals(role) ? "user" : role;
    }

    private static String imageUrl(AiContentPart.Image image) {
        if (StringUtils.hasText(image.url())) {
            return image.url();
        }
        if (StringUtils.hasText(image.data())) {
            var mediaType = StringUtils.hasText(image.mediaType())
                    ? image.mediaType()
                    : "application/octet-stream";
            return "data:" + mediaType + ";base64," + image.data();
        }
        return "";
    }

    private static String detail(String detail) {
        return "low".equals(detail) || "high".equals(detail) ? detail : "default";
    }

    private static String fileUrl(AiContentPart.File file) {
        if (StringUtils.hasText(file.fileId())) {
            return "mm_file://" + file.fileId();
        }
        if (StringUtils.hasText(file.url())) {
            return file.url();
        }
        if (StringUtils.hasText(file.data())) {
            var mediaType = StringUtils.hasText(file.mediaType())
                    ? file.mediaType()
                    : "application/octet-stream";
            return "data:" + mediaType + ";base64," + file.data();
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
