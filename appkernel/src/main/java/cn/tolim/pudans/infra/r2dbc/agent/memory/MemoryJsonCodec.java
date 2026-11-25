package cn.tolim.pudans.infra.r2dbc.agent.memory;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;

public enum MemoryJsonCodec {
    ;

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private static final TypeReference<List<Map<String, Object>>> OBJECT_LIST = new TypeReference<>() {};

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    public static String write(Object value) {
        return JsonUtils.objectToJson(value == null ? Map.of() : value);
    }

    public static Map<String, Object> readObject(String json) {
        return json == null || json.isBlank() ? Map.of() : JsonUtils.jsonToObject(json, OBJECT_MAP);
    }

    public static List<Map<String, Object>> readObjectList(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.jsonToObject(json, OBJECT_LIST);
    }

    public static List<String> readStrings(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.jsonToObject(json, STRING_LIST);
    }
}
