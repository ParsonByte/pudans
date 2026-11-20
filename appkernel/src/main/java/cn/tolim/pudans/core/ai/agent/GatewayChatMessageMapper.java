package cn.tolim.pudans.core.ai.agent;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jToolMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class GatewayChatMessageMapper {

    private GatewayChatMessageMapper() {}

    public static ChatMessage toChatMessage(AiChatMessage message) {
        return switch (message.role()) {
            case "system" -> SystemMessage.from(text(message));
            case "assistant" -> assistant(message);
            case "user", "tool" -> user(message);
            default -> throw new IllegalArgumentException(
                    "AI Service 不支持的消息角色：" + message.role());
        };
    }

    public static List<Content> toContents(AiChatMessage message) {
        var contents = new ArrayList<Content>();
        for (var part : message.parts()) {
            contents.add(content(part));
        }
        return contents;
    }

    public static UserMessage toUserMessage(AiChatMessage message) {
        return UserMessage.from(toContents(message));
    }

    public static AiChatMessage toAiChatMessage(ChatMessage message) {
        if (message instanceof SystemMessage system) {
            return new AiChatMessage("system", List.of(new AiContentPart.Text(system.text(), null)));
        }
        if (message instanceof UserMessage user) {
            return new AiChatMessage("user", contents(user.contents()));
        }
        if (message instanceof AiMessage ai) {
            var parts = new ArrayList<AiContentPart>();
            if (StringUtils.hasText(ai.thinking())) {
                parts.add(new AiContentPart.Thinking(ai.thinking(), null, null));
            }
            if (StringUtils.hasText(ai.text())) {
                parts.add(new AiContentPart.Text(ai.text(), null));
            }
            for (var call : ai.toolExecutionRequests()) {
                parts.add(new AiContentPart.ToolCall(
                        call.id(), call.name(), LangChain4jToolMapper.arguments(call.arguments()), null));
            }
            return new AiChatMessage("assistant", parts);
        }
        if (message instanceof ToolExecutionResultMessage result) {
            return new AiChatMessage(
                    "tool",
                    List.of(new AiContentPart.ToolResult(
                            result.id(),
                            JsonUtils.mapper().getNodeFactory().textNode(result.text()),
                            result.isError(),
                            null)));
        }
        throw new IllegalArgumentException("AI Service 不支持的消息类型：" + message.type());
    }

    private static AiMessage assistant(AiChatMessage message) {
        var text = new StringBuilder();
        var thinking = new StringBuilder();
        var calls = new ArrayList<ToolExecutionRequest>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text content) {
                text.append(content.text());
            } else if (part instanceof AiContentPart.Thinking content) {
                thinking.append(content.text());
            } else if (part instanceof AiContentPart.ToolCall call) {
                calls.add(ToolExecutionRequest.builder()
                        .id(call.id())
                        .name(call.name())
                        .arguments(call.arguments() == null ? "{}" : call.arguments().toString())
                        .build());
            } else {
                throw new IllegalArgumentException("AI Service 不支持助手消息内容：" + part);
            }
        }
        return AiMessage.builder()
                .text(text.isEmpty() ? null : text.toString())
                .thinking(thinking.isEmpty() ? null : thinking.toString())
                .toolExecutionRequests(calls)
                .build();
    }

    private static ChatMessage user(AiChatMessage message) {
        var results = new ArrayList<ToolExecutionResultMessage>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.ToolResult result) {
                results.add(ToolExecutionResultMessage.builder()
                        .id(result.toolCallId())
                        .toolName(result.toolCallId())
                        .text(result.content() == null ? "" : result.content().toString())
                        .isError(Boolean.TRUE.equals(result.error()))
                        .build());
            }
        }
        if (!results.isEmpty()) {
            return results.getFirst();
        }
        return toUserMessage(message);
    }

    private static List<AiContentPart> contents(List<Content> contents) {
        var parts = new ArrayList<AiContentPart>();
        for (var content : contents) {
            if (content instanceof TextContent text) {
                parts.add(new AiContentPart.Text(text.text(), null));
            } else if (content instanceof ImageContent image) {
                var source = image.image();
                parts.add(new AiContentPart.Image(
                        source.mimeType(),
                        source.base64Data(),
                        source.url() == null ? null : source.url().toString(),
                        null,
                        null));
            } else if (content instanceof PdfFileContent pdf) {
                var source = pdf.pdfFile();
                parts.add(new AiContentPart.File(
                        source.mimeType(),
                        source.base64Data(),
                        source.url() == null ? null : source.url().toString(),
                        null,
                        null,
                        null));
            } else {
                throw new IllegalArgumentException("AI Service 不支持的消息内容：" + content.type());
            }
        }
        return parts;
    }

    private static Content content(AiContentPart part) {
        if (part instanceof AiContentPart.Text text) {
            return TextContent.from(text.text());
        }
        if (part instanceof AiContentPart.Image image) {
            return StringUtils.hasText(image.data())
                    ? ImageContent.from(image.data(), mediaType(image.mediaType(), "image/png"))
                    : ImageContent.from(image.url());
        }
        if (part instanceof AiContentPart.File file
                && "application/pdf".equalsIgnoreCase(file.mediaType())) {
            return StringUtils.hasText(file.data())
                    ? PdfFileContent.from(file.data(), file.mediaType())
                    : PdfFileContent.from(file.url());
        }
        throw new IllegalArgumentException("AI Service 不支持的消息内容：" + part);
    }

    private static String text(AiChatMessage message) {
        var text = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text content) {
                text.append(content.text());
            }
        }
        return text.toString();
    }

    private static String mediaType(String mediaType, String fallback) {
        return StringUtils.hasText(mediaType) ? mediaType : fallback;
    }
}