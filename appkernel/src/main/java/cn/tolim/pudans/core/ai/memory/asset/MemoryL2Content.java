package cn.tolim.pudans.core.ai.memory.asset;

import org.jspecify.annotations.Nullable;

final class MemoryL2Content {

    private static final String META_OPEN = "[META]";

    private static final String META_CLOSE = "[/META]";

    private static final String COMMENT_OPEN = "<!--";

    private static final String COMMENT_CLOSE = "-->";

    private static final String FRONT_MATTER = "---";

    private static final int MAX_META_BLOCK = 2000;

    private MemoryL2Content() {}

    static String strip(@Nullable String stored) {
        if (stored == null) {
            return "";
        }
        var prefixLength = metaPrefixLength(stored);
        return prefixLength == 0 ? stored : stored.substring(prefixLength);
    }

    static String update(@Nullable String stored, String content) {
        var prefixLength = stored == null ? 0 : metaPrefixLength(stored);
        return prefixLength == 0 ? content : stored.substring(0, prefixLength) + content;
    }

    private static int metaPrefixLength(String stored) {
        var marker = stored.startsWith(META_OPEN)
                ? META_CLOSE
                : stored.startsWith(COMMENT_OPEN)
                        ? COMMENT_CLOSE
                        : stored.startsWith(FRONT_MATTER + "\n") ? "\n" + FRONT_MATTER : null;
        if (marker == null) {
            return 0;
        }
        var close = stored.indexOf(marker, marker.length());
        if (close < 0 || close > MAX_META_BLOCK) {
            return 0;
        }
        if (stored.startsWith(FRONT_MATTER + "\n") && !frontMatter(stored, close)) {
            return 0;
        }
        var end = close + marker.length();
        if (end < stored.length() && stored.charAt(end) == '\n') {
            end++;
        }
        return end;
    }

    private static boolean frontMatter(String stored, int close) {
        return stored.substring(FRONT_MATTER.length() + 1, close).contains(":");
    }
}