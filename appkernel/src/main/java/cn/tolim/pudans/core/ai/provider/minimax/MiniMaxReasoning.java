package cn.tolim.pudans.core.ai.provider.minimax;

import java.util.Locale;
import java.util.Map;

final class MiniMaxReasoning {

    private MiniMaxReasoning() {}

    static Map<String, Object> chat(boolean reasoningSupported, String reasoning) {
        if (!reasoningSupported || reasoning == null) {
            return Map.of();
        }
        if (disabled(reasoning)) {
            return Map.of("thinking", Map.of("type", "disabled"));
        }
        return Map.of(
                "thinking", Map.of("type", "adaptive"),
                "reasoning_effort", effort(reasoning, false));
    }

    static Map<String, Object> messages(boolean reasoningSupported, String reasoning) {
        if (!reasoningSupported || reasoning == null) {
            return Map.of();
        }
        if (disabled(reasoning)) {
            return Map.of("thinking", Map.of("type", "disabled"));
        }
        return Map.of(
                "thinking", Map.of("type", "adaptive"),
                "output_config", Map.of("effort", effort(reasoning, false)));
    }

    static String responses(boolean reasoningSupported, String reasoning) {
        if (!reasoningSupported || reasoning == null || disabled(reasoning)) {
            return null;
        }
        return effort(reasoning, true);
    }

    private static String effort(String reasoning, boolean allowMinimal) {
        return switch (reasoning.trim().toLowerCase(Locale.ROOT)) {
            case "minimal" -> allowMinimal ? "minimal" : "low";
            case "low" -> "low";
            case "medium" -> "medium";
            case "high" -> "high";
            case "xhigh" -> "xhigh";
            case "max", "ultra" -> "max";
            default -> "high";
        };
    }

    private static boolean disabled(String reasoning) {
        var normalized = reasoning.trim().toLowerCase(Locale.ROOT);
        return "none".equals(normalized) || "off".equals(normalized);
    }
}
