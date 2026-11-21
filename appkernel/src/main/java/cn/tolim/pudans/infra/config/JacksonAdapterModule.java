package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.extension.contract.JsonExtension;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import java.util.function.Supplier;
import org.springframework.util.Assert;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.JsonNodeException;
import tools.jackson.databind.module.SimpleModule;


public class JacksonAdapterModule extends SimpleModule {

    private final Supplier<ObjectMapper> objectMapper;

    // 注册旧版 Jackson 类型与 JSON 节点桥接器。
    public JacksonAdapterModule(Supplier<ObjectMapper> objectMapper) {
        super(JacksonAdapterModule.class.getSimpleName(), new Version(1, 0, 0, null, null, null));
        this.objectMapper = objectMapper;
        addSerializer(new JsonNodeSerializer());
        addSerializer(new JsonPatchSerializer());
        addSerializer(new JsonExtensionSerializer());

        addDeserializer(JsonNode.class, new JsonNodeDeserializer<>(JsonNode.class));
        addDeserializer(ObjectNode.class, new JsonNodeDeserializer<>(ObjectNode.class));
        addDeserializer(JsonExtension.class, new JsonExtensionDeSerializer());
    }

    class JsonExtensionDeSerializer extends ValueDeserializer<JsonExtension> {

        // 用旧版映射器还原扩展对象并保留失败位置。
        @Override
        public JsonExtension deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            var json = p.readValueAsTree().toString();
            try {
                return objectMapper.get().readValue(json, JsonExtension.class);
            } catch (JsonProcessingException e) {
                throw InvalidFormatException.from(p, "无法反序列化 JsonExtension");
            }
        }
    }

    class JsonExtensionSerializer extends ValueSerializer<JsonExtension> {

        // 直接写出旧版映射器生成的原始 JSON。
        @Override
        public void serialize(JsonExtension value, JsonGenerator gen, SerializationContext ctxt)
                throws JacksonException {
            try {
                var raw = objectMapper.get().writeValueAsString(value);
                gen.writeRawValue(raw);
            } catch (JsonProcessingException e) {
                throw InvalidFormatException.from(gen, "无法序列化 JsonExtension");
            }
        }

        // 声明序列化器负责的扩展类型。
        @Override
        public Class<?> handledType() {
            return JsonExtension.class;
        }
    }

    class JsonPatchSerializer extends ValueSerializer<JsonPatch> {

        // 直接写出补丁 JSON，避免二次转义。
        @Override
        public void serialize(JsonPatch value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            try {
                gen.writeRawValue(objectMapper.get().writeValueAsString(value));
            } catch (JsonProcessingException e) {
                throw InvalidFormatException.from(gen, "无法序列化 JsonPatch");
            }
        }

        // 声明序列化器负责的补丁类型。
        @Override
        public Class<?> handledType() {
            return JsonPatch.class;
        }
    }

    class JsonNodeSerializer extends ValueSerializer<JsonNode> {

        // 原样输出节点内容以保留结构。
        @Override
        public void serialize(JsonNode value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            try {
                gen.writeRawValue(objectMapper.get().writeValueAsString(value));
            } catch (JsonProcessingException e) {
                throw InvalidFormatException.from(gen, "无法序列化 JsonNode");
            }
        }

        // 声明序列化器负责的节点类型。
        @Override
        public Class<?> handledType() {
            return JsonNode.class;
        }
    }

    class JsonNodeDeserializer<T extends JsonNode> extends ValueDeserializer<T> {

        private final Class<T> type;

        // 记录目标节点类型以复用反序列化逻辑。
        JsonNodeDeserializer(Class<T> type) {
            this.type = type;
        }

        // 桥接旧版映射器解析节点，失败时保留位置。
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            var json = p.readValueAsTree().toString();
            var mapper = objectMapper.get();
            Assert.notNull(mapper, "旧版 ObjectMapper 不能为空");
            try {
                return mapper.readValue(json, type);
            } catch (JsonProcessingException e) {
                throw JsonNodeException.from(p, "桥接旧版 JSON 节点失败", e);
            }
        }
    }
}
