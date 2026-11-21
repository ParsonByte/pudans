package cn.tolim.pudans.infra.api.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import java.util.Map;
import org.springframework.util.Assert;


public class JsonUtils {
    public static final ObjectMapper DEFAULT_JSON_MAPPER = Json.mapper();

    private JsonUtils() {}

    public static ObjectMapper mapper() {
        return DEFAULT_JSON_MAPPER;
    }


    public static <T> T mapToObject(Map<String, ?> sourceMap, Class<T> type) {
        return DEFAULT_JSON_MAPPER.convertValue(sourceMap, type);
    }


    public static String objectToJson(Object source) {
        Assert.notNull(source, "源对象不能为 null");
        try {
            return DEFAULT_JSON_MAPPER.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new JsonParseException(e);
        }
    }


    public static <T> T jsonToObject(String json, Class<T> toValueType) {
        try {
            return DEFAULT_JSON_MAPPER.readValue(json, toValueType);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }


    public static <T> T jsonToObject(String json, TypeReference<T> typeReference) {
        try {
            return DEFAULT_JSON_MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }


    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T source) {
        try {
            return (T) DEFAULT_JSON_MAPPER.readValue(objectToJson(source), source.getClass());
        } catch (JsonProcessingException e) {
            throw new JsonParseException(e);
        }
    }
}
