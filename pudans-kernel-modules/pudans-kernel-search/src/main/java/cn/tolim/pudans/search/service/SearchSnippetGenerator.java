package cn.tolim.pudans.search.service;

import cn.tolim.pudans.search.config.SearchProperties;
import org.springframework.stereotype.Component;

@Component
public class SearchSnippetGenerator {

    private static final String ELLIPSIS = "...";

    private final SearchProperties properties;

    public SearchSnippetGenerator(SearchProperties properties) {
        this.properties = properties;
    }

    // Build a plain-text snippet around the first keyword match; only <em> markers are added
    public String generate(String title, String summary, String content, String keyword) {
        int limit = Math.max(1, properties.getSnippetLength());
        String normalizedContent = hasText(content) ? collapseWhitespace(content) : null;
        String normalizedSummary = hasText(summary) ? collapseWhitespace(summary) : null;
        String normalizedTitle = hasText(title) ? collapseWhitespace(title) : null;
        String source = firstContaining(keyword, normalizedContent, normalizedSummary, normalizedTitle);
        if (source == null) {
            source = firstNonBlank(normalizedContent, normalizedSummary, normalizedTitle);
        }
        if (source == null) {
            return "";
        }
        String normalizedKeyword = hasText(keyword) ? collapseWhitespace(keyword) : null;
        if (normalizedKeyword == null) {
            return truncate(escapeHtml(source), limit);
        }
        // Keep the whole keyword inside the window even for very long query strings
        limit = Math.max(limit, normalizedKeyword.length() + 2);
        int matchIndex = indexOfIgnoreCase(source, normalizedKeyword, 0);
        if (matchIndex < 0) {
            return truncate(escapeHtml(source), limit);
        }
        return window(source, matchIndex, normalizedKeyword, limit);
    }

    private static String window(String source, int matchIndex, String keyword, int limit) {
        int context = Math.max(0, (limit - keyword.length()) / 2);
        int start = Math.max(0, matchIndex - context);
        int end = Math.min(source.length(), start + limit);
        if (end - start < limit) {
            start = Math.max(0, end - limit);
        }
        String slice = source.substring(start, end);
        StringBuilder snippet = new StringBuilder(slice.length() + 16);
        if (start > 0) {
            snippet.append(ELLIPSIS);
        }
        snippet.append(highlight(slice, keyword));
        if (end < source.length()) {
            snippet.append(ELLIPSIS);
        }
        return snippet.toString();
    }

    private static String highlight(String window, String keyword) {
        if (window.isEmpty()) {
            return "";
        }
        StringBuilder highlighted = new StringBuilder(window.length() + 16);
        int cursor = 0;
        while (cursor < window.length()) {
            int match = indexOfIgnoreCase(window, keyword, cursor);
            if (match < 0) {
                highlighted.append(escapeHtml(window.substring(cursor)));
                break;
            }
            highlighted.append(escapeHtml(window.substring(cursor, match)));
            highlighted.append("<em>")
                    .append(escapeHtml(window.substring(match, match + keyword.length())))
                    .append("</em>");
            cursor = match + keyword.length();
        }
        return highlighted.toString();
    }

    private static int indexOfIgnoreCase(String source, String target, int from) {
        int lastStart = source.length() - target.length();
        for (int index = Math.max(0, from); index <= lastStart; index++) {
            if (source.regionMatches(true, index, target, 0, target.length())) {
                return index;
            }
        }
        return -1;
    }

    private static String firstContaining(String keyword, String... candidates) {
        if (!hasText(keyword)) {
            return null;
        }
        String normalizedKeyword = collapseWhitespace(keyword);
        for (String candidate : candidates) {
            if (candidate != null && indexOfIgnoreCase(candidate, normalizedKeyword, 0) >= 0) {
                return candidate;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String collapseWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int limit) {
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + ELLIPSIS;
    }

    // Escape HTML so stored content cannot inject markup into the snippet
    static String escapeHtml(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}