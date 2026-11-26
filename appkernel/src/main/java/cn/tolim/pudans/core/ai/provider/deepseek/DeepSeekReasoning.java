package cn.tolim.pudans.core.ai.provider.deepseek;

import java.util.Locale;
import java.util.Map;

final class DeepSeekReasoning {

    private DeepSeekReasoning() {}

    static Map<String, Object> chat(boolean reasoningSupported, String reasoning) {
        if (!reasoningSupported || reasoning == null) {
            return Map.of();
        }
        if (disabled(reasoning)) {
            return Map.of("thinking", Map.of("type", "disabled"));
        }
        return Map.of(
                "thinking", Map.of("type", "enabled"),
                "reasoning_effort", effort(reasoning));
    }

    static Map<String, Object> messages(boolean reasoningSupported, String reasoning) {
        if (!reasoningSupported || reasoning == null) {
            return Map.of();
        }
        if (disabled(reasoning)) {
            return Map.of("thinking", Map.of("type", "disabled"));
        }
        return Map.of(
                "thinking", Map.of("type", "enabled"),
                "output_config", Map.of("effort", effort(reasoning)));
    }

    static String responses(boolean reasoningSupported, String reasoning) {
        if (!reasoningSupported || reasoning == null) {
            return null;
        }
        return disabled(reasoning) ? "none" : effort(reasoning);
    }

    static String effort(String reasoning) {
        return switch (reasoning.trim().toLowerCase(Locale.ROOT)) {
            case "minimal", "low" -> "low";
            case "medium", "high", "xhigh" -> "high";
            case "max", "ultra" -> "max";
            default -> "high";
        };
    }

    private static boolean disabled(String reasoning) {
        var normalized = reasoning.trim().toLowerCase(Locale.ROOT);
        return "none".equals(normalized) || "off".equals(normalized);
    }
}
