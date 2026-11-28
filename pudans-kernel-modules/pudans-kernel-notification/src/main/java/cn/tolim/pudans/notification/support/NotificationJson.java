package cn.tolim.pudans.notification.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// JSON helpers for persisted notification payloads.
public final class NotificationJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP_TYPE =
            new TypeReference<>() {
            };

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private NotificationJson() {
    }

    public static String writeMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to serialize notification parameters",
                    exception);
        }
    }

    public static Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, STRING_MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to parse notification parameters",
                    exception);
        }
    }

    public static String writeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to serialize template parameters",
                    exception);
        }
    }

    public static List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to parse template parameters", exception);
        }
    }

    }