package cn.tolim.pudans.infra.r2dbc.ai;

import cn.tolim.pudans.contract.ai.AiPeakWindow;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;

public final class AiJsonCodec {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private static final TypeReference<List<AiPeakWindow>> PEAK_WINDOWS = new TypeReference<>() {};

    private AiJsonCodec() {}

    public static String write(Object value) {
        return JsonUtils.objectToJson(value == null ? Map.of() : value);
    }

    public static Map<String, Object> readObject(String json) {
        return json == null || json.isBlank() ? Map.of() : JsonUtils.jsonToObject(json, OBJECT_MAP);
    }

    public static Map<String, String> readStrings(String json) {
        return json == null || json.isBlank() ? Map.of() : JsonUtils.jsonToObject(json, STRING_MAP);
    }

    public static List<AiPeakWindow> readPeakWindows(String json) {
        return json == null || json.isBlank() ? List.of() : JsonUtils.jsonToObject(json, PEAK_WINDOWS);
    }
}
