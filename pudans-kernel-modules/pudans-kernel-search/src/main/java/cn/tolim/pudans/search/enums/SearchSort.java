package cn.tolim.pudans.search.enums;

import java.util.Locale;

public enum SearchSort {

    // Relevance ranking for keyword searches, recency order for browse requests
    RELEVANCE,
    // Most recently updated first
    UPDATED_DESC,
    // Most recently created first
    CREATED_DESC,
    // Document title in ascending order
    TITLE_ASC;

    // Parse the API value, returning null for blank input
    public static SearchSort from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}