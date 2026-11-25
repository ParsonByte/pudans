package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

final class CreationRows {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private CreationRows() {}

    static UUID uuid(Row row, String column) {
        return row.get(column, UUID.class);
    }

    static Instant instant(Row row, String column) {
        var value = row.get(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Long longValue(Row row, String column) {
        return row.get(column, Long.class);
    }

    static Integer integer(Row row, String column) {
        return row.get(column, Integer.class);
    }

    static List<String> stringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        return JsonUtils.jsonToObject(json, STRING_LIST);
    }

    static Map<String, Object> objectMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        return JsonUtils.jsonToObject(json, OBJECT_MAP);
    }

    static String jsonObject(Map<String, Object> value) {
        return JsonUtils.objectToJson(value == null ? Map.of() : value);
    }

    static String jsonList(List<String> value) {
        return JsonUtils.objectToJson(value == null ? List.of() : value);
    }

    static OffsetDateTime offset(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    static String likePattern(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var escaped = value.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
