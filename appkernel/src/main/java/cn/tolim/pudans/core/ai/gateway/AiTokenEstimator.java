package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import java.util.List;

public final class AiTokenEstimator {

    private static final int MESSAGE_OVERHEAD = 4;

    private static final int IMAGE_TOKENS = 85;

    private static final int FILE_TOKENS = 128;

    private AiTokenEstimator() {}

    public static int estimateMessages(List<AiChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (var message : messages) {
            total += MESSAGE_OVERHEAD;
            for (var part : message.parts()) {
                total += estimatePart(part);
            }
        }
        return clamp(total);
    }

    public static int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long cjk = 0;
        long latin = 0;
        var iterator = text.codePoints().iterator();
        while (iterator.hasNext()) {
            if (isCjk(iterator.nextInt())) {
                cjk++;
            } else {
                latin++;
            }
        }
        return clamp(cjk + (latin + 3) / 4);
    }

    private static int estimatePart(AiContentPart part) {
        return switch (part) {
            case AiContentPart.Text text -> estimateText(text.text());
            case AiContentPart.Thinking thinking -> estimateText(thinking.text());
            case AiContentPart.Image ignored -> IMAGE_TOKENS;
            case AiContentPart.File file -> file.fileId() == null
                    ? FILE_TOKENS
                    : FILE_TOKENS + estimateText(file.fileId());
            case AiContentPart.ToolCall call -> estimateText(call.name())
                    + (call.arguments() == null ? 0 : estimateText(call.arguments().toString()));
            case AiContentPart.ToolResult result -> result.content() == null
                    ? 0
                    : estimateText(result.content().toString());
            case AiContentPart.Custom custom -> estimateText(custom.raw().toString());
        };
    }

    private static boolean isCjk(int codePoint) {
        return (codePoint >= 0x3000 && codePoint <= 0x303F)
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }

    private static int clamp(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
