package cn.tolim.pudans.core.ai.harness.tool;

import java.util.List;
import java.util.regex.Pattern;

public final class ToolPatterns {

    private ToolPatterns() {}

    public static boolean matches(List<String> patterns, String toolName) {
        if (patterns == null || patterns.isEmpty() || toolName == null) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> glob(pattern).matcher(toolName).matches());
    }

    private static Pattern glob(String pattern) {
        var builder = new StringBuilder("^");
        for (var character : pattern.toCharArray()) {
            if (character == '*') {
                builder.append(".*");
            } else {
                builder.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return Pattern.compile(builder.append('$').toString());
    }
}