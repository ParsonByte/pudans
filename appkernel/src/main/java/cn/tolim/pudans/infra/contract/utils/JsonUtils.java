package cn.tolim.pudans.infra.contract.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import java.util.Map;
import org.springframework.util.Assert;


public class JsonUtils {
    public static final ObjectMapper DEFAULT_JSON_MAPPER = Json.mapper();

    private JsonUtils() {}

    // 暴露共享映射器，统一序列化配置。
    public static ObjectMapper mapper() {
        return DEFAULT_JSON_MAPPER;
    }


    // 通过 JSON 转换在 Map 与目标类型之间映射。
    public static <T> T mapToObject(Map<String, ?> sourceMap, Class<T> type) {
        return DEFAULT_JSON_MAPPER.convertValue(sourceMap, type);
    }


    // 序列化对象并将受检异常转为运行时异常。
    public static String objectToJson(Object source) {
        Assert.notNull(source, "源对象不能为 null");
        try {
            return DEFAULT_JSON_MAPPER.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new JsonParseException(e);
        }
    }


    // 解析 JSON 文本并统一异常类型。
    public static <T> T jsonToObject(String json, Class<T> toValueType) {
        try {
            return DEFAULT_JSON_MAPPER.readValue(json, toValueType);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }


    // 解析泛型 JSON 文本并统一异常类型。
    public static <T> T jsonToObject(String json, TypeReference<T> typeReference) {
        try {
            return DEFAULT_JSON_MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }


    // 借 JSON 往返复制对象，避免共享可变引用。
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T source) {
        try {
            return (T) DEFAULT_JSON_MAPPER.readValue(objectToJson(source), source.getClass());
        } catch (JsonProcessingException e) {
            throw new JsonParseException(e);
        }
    }
}
