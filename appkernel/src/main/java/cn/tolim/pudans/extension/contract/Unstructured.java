package cn.tolim.pudans.extension.contract;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.core.util.Json;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;


@JsonSerialize(using = Unstructured.UnstructuredSerializer.class)
@JsonDeserialize(using = Unstructured.UnstructuredDeserializer.class)
@tools.jackson.databind.annotation.JsonSerialize(using = Unstructured.UnstructuredValueSerializer.class)
@tools.jackson.databind.annotation.JsonDeserialize(using = Unstructured.UnstructuredValueDeserializer.class)
@SuppressWarnings("rawtypes")
public class Unstructured implements Extension {

    @SuppressWarnings("deprecation")
    public static final ObjectMapper OBJECT_MAPPER = Json.mapper()


            .copy()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

    private final Map data;

    // 创建空数据表的非结构化扩展。
    public Unstructured() {
        this(new HashMap());
    }

    // 包装已有数据表，读写直接作用于该表。
    public Unstructured(Map data) {
        this.data = data;
    }

    // 按字段路径逐层读取嵌套值。
    public static Optional<Object> getNestedValue(Map map, String @Nullable ... fields) {
        if (fields == null || fields.length == 0) {
            return Optional.of(map);
        }
        Map tempMap = map;
        for (int i = 0; i < fields.length - 1; i++) {
            Object value = tempMap.get(fields[i]);
            if (!(value instanceof Map)) {
                return Optional.empty();
            }
            tempMap = (Map<?, ?>) value;
        }
        return Optional.ofNullable(tempMap.get(fields[fields.length - 1]));
    }

    // 读取嵌套字段并断言为字符串列表。
    @SuppressWarnings("unchecked")
    public static Optional<List<String>> getNestedStringList(Map map, String @Nullable ... fields) {
        return getNestedValue(map, fields).map(value -> (List<String>) value);
    }

    // 读取嵌套字段并转换为字符串集合。
    public static Optional<Set<String>> getNestedStringSet(Map map, String @Nullable ... fields) {
        return getNestedValue(map, fields).map(value -> {
            if (value instanceof Collection collection) {
                return new LinkedHashSet<>(collection);
            }
            throw new IllegalArgumentException(
                    "值类型不正确：" + value.getClass() + "，期望：" + Set.class);
        });
    }

    // 仅在父路径存在时写入嵌套值。
    @SuppressWarnings("unchecked")
    public static void setNestedValue(Map map, Object value, String @Nullable ... fields) {
        if (fields == null || fields.length == 0) {

            return;
        }
        var prevFields = Arrays.stream(fields, 0, fields.length - 1).toArray(String[]::new);
        getNestedMap(map, prevFields).ifPresent(m -> {
            var lastField = fields[fields.length - 1];
            m.put(lastField, value);
        });
    }

    // 读取嵌套字段并断言为 Map。
    public static Optional<Map> getNestedMap(Map map, String... fields) {
        return getNestedValue(map, fields).map(value -> (Map) value);
    }

    // 读取嵌套字段并断言为字符串映射。
    @SuppressWarnings("unchecked")
    public static Optional<Map<String, String>> getNestedStringStringMap(Map map, String... fields) {
        return getNestedValue(map, fields).map(labelsObj -> (Map<String, String>) labelsObj);
    }

    // 读取时间字段，兼容字符串与 Instant。
    public static Optional<Instant> getNestedInstant(Map map, String... fields) {
        return getNestedValue(map, fields).map(instantValue -> {
            if (instantValue instanceof Instant instant) {
                return instant;
            }
            return Instant.parse(instantValue.toString());
        });
    }

    // 读取长整型字段，兼容字符串与数值。
    public static Optional<Long> getNestedLong(Map map, String... fields) {
        return getNestedValue(map, fields).map(longObj -> {
            if (longObj instanceof Long l) {
                return l;
            }
            return Long.valueOf(longObj.toString());
        });
    }

    // 暴露底层数据表，修改会直接影响扩展。
    public Map getData() {
        return data;
    }

    // 读取数据表中的 apiVersion 字段。
    @Override
    public String getApiVersion() {
        return (String) data.get("apiVersion");
    }

    // 写入 apiVersion 字段。
    @Override
    public void setApiVersion(String apiVersion) {
        setNestedValue(data, apiVersion, "apiVersion");
    }

    // 读取数据表中的 kind 字段。
    @Override
    public String getKind() {
        return (String) data.get("kind");
    }

    // 写入 kind 字段。
    @Override
    public void setKind(String kind) {
        setNestedValue(data, kind, "kind");
    }

    // 以元数据视图包装 metadata 子映射。
    @Override
    public @Nullable MetadataOperator getMetadata() {
        return getNestedMap(data, "metadata").map(UnstructuredMetadata::new).orElse(null);
    }

    // 把元数据对象转换为 Map 后写入。
    @Override
    @SuppressWarnings("unchecked")
    public void setMetadata(MetadataOperator metadata) {
        Map metadataMap = OBJECT_MAPPER.convertValue(metadata, Map.class);
        data.put("metadata", metadataMap);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Unstructured that = (Unstructured) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    static class UnstructuredMetadata implements MetadataOperator {

        private final Map<String, Object> metadata;

        // 包装 metadata 映射，读写直接作用于该映射。
        UnstructuredMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        // 读取名称，缺失时返回 null。
        @Override
        public @Nullable String getName() {
            return (String) getNestedValue(metadata, "name").orElse(null);
        }

        // 写入名称，null 同样会写入映射。
        @Override
        public void setName(String name) {
            setNestedValue(metadata, name, "name");
        }

        // 读取服务端生成名称所用的前缀。
        @Override
        public @Nullable String getGenerateName() {
            return (String) getNestedValue(metadata, "generateName").orElse(null);
        }

        // 写入名称前缀，null 同样会写入映射。
        @Override
        public void setGenerateName(String generateName) {
            setNestedValue(metadata, generateName, "generateName");
        }

        // 读取标签映射，缺失时返回 null。
        @Override
        public @Nullable Map<String, String> getLabels() {
            return getNestedStringStringMap(metadata, "labels").orElse(null);
        }

        // 写入标签映射，null 同样会写入映射。
        @Override
        public void setLabels(Map<String, String> labels) {
            setNestedValue(metadata, labels, "labels");
        }

        // 读取注解映射，缺失时返回 null。
        @Override
        public @Nullable Map<String, String> getAnnotations() {
            return getNestedStringStringMap(metadata, "annotations").orElse(null);
        }

        // 写入注解映射，null 同样会写入映射。
        @Override
        public void setAnnotations(Map<String, String> annotations) {
            setNestedValue(metadata, annotations, "annotations");
        }

        // 读取乐观锁版本号，缺失时返回 null。
        @Override
        public @Nullable Long getVersion() {
            return getNestedLong(metadata, "version").orElse(null);
        }

        // 写入乐观锁版本号。
        @Override
        public void setVersion(Long version) {
            setNestedValue(metadata, version, "version");
        }

        // 读取创建时间，缺失时返回 null。
        @Override
        public @Nullable Instant getCreationTimestamp() {
            return getNestedInstant(metadata, "creationTimestamp").orElse(null);
        }

        // 写入创建时间，null 同样会写入映射。
        @Override
        public void setCreationTimestamp(Instant creationTimestamp) {
            setNestedValue(metadata, creationTimestamp, "creationTimestamp");
        }

        // 读取删除时间，非空表示正在删除。
        @Override
        public @Nullable Instant getDeletionTimestamp() {
            return getNestedInstant(metadata, "deletionTimestamp").orElse(null);
        }

        // 写入删除时间，由删除流程调用。
        @Override
        public void setDeletionTimestamp(Instant deletionTimestamp) {
            setNestedValue(metadata, deletionTimestamp, "deletionTimestamp");
        }

        // 读取终结器集合，缺失时返回 null。
        @Override
        public @Nullable Set<String> getFinalizers() {
            return getNestedStringSet(metadata, "finalizers").orElse(null);
        }

        // 写入终结器集合。
        @Override
        public void setFinalizers(Set<String> finalizers) {
            setNestedValue(metadata, finalizers, "finalizers");
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            var that = (UnstructuredMetadata) o;
            return Objects.equals(metadata, that.metadata);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(metadata);
        }
    }

    public static class UnstructuredSerializer extends JsonSerializer<Unstructured> {

        // 直接把数据表序列化为 JSON 对象。
        @Override
        public void serialize(Unstructured value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeObject(value.data);
        }
    }

    static class UnstructuredValueSerializer extends ValueSerializer<Unstructured> {

        // Jackson 3 序列化入口，输出数据表内容。
        @Override
        public void serialize(Unstructured value, tools.jackson.core.JsonGenerator gen, SerializationContext ctxt)
                throws JacksonException {
            gen.writePOJO(value.data);
        }

        // 声明处理的类型，供 Jackson 3 注册。
        @Override
        public Class<?> handledType() {
            return Unstructured.class;
        }
    }

    static class UnstructuredValueDeserializer extends ValueDeserializer<Unstructured> {

        // Jackson 3 反序列化入口，包装数据表。
        @Override
        public Unstructured deserialize(
                tools.jackson.core.JsonParser p, tools.jackson.databind.DeserializationContext ctxt)
                throws JacksonException {
            var map = p.readValueAs(Map.class);
            return new Unstructured(map);
        }
    }

    public static class UnstructuredDeserializer extends JsonDeserializer<Unstructured> {

        // Jackson 2 反序列化入口，包装数据表。
        @Override
        public Unstructured deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Map data = p.getCodec().readValue(p, Map.class);
            return new Unstructured(data);
        }
    }
}
