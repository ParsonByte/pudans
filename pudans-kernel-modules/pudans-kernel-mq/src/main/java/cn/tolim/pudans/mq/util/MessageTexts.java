package cn.tolim.pudans.mq.util;

import java.util.regex.Pattern;

public final class MessageTexts {

    // Topic names are used as routing keys and must stay URL safe
    private static final Pattern TOPIC_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private MessageTexts() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public static boolean isValidTopic(String topic) {
        return topic != null && TOPIC_PATTERN.matcher(topic).matches();
    }
}