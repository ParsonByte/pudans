package cn.tolim.pudans.core.ai.memory.asset;

import java.util.Locale;

public final class MemoryAssetNames {

    private static final String AUTO_PREFIX = "mem-";

    private static final String ILLEGAL = "[^a-z0-9]+";

    private static final int PART_LIMIT = 48;

    private MemoryAssetNames() {}

    public static String autoName(String team, String userId, String agentId) {
        return AUTO_PREFIX + part(team) + "-" + part(userId) + "-" + part(agentId);
    }

    public static String bindingName(String layer, String targetType, String team, String agent) {
        var suffixed = "memory-prompt-" + layer.toLowerCase(Locale.ROOT) + "-"
                + targetType.toLowerCase(Locale.ROOT);
        if (team == null && agent == null) {
            return suffixed;
        }
        return agent == null ? suffixed + "-" + part(team) : suffixed + "-" + part(team) + "-" + part(agent);
    }

    static String part(String value) {
        if (value == null || value.isBlank()) {
            return "x";
        }
        var normalized = value.toLowerCase(Locale.ROOT).replaceAll(ILLEGAL, "-").replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            return "x";
        }
        return normalized.length() <= PART_LIMIT ? normalized : normalized.substring(0, PART_LIMIT);
    }
}