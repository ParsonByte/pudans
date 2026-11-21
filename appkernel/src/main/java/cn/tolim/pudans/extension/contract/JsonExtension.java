package cn.tolim.pudans.extension.contract;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;


@JsonSerialize(using = JsonExtension.ObjectNodeExtensionSerializer.class)
@JsonDeserialize(using = JsonExtension.ObjectNodeExtensionDeSerializer.class)
public class JsonExtension implements Extension {

    private final ObjectMapper objectMapper;

    private final ObjectNode objectNode;

    // 基于空 JSON 对象创建可编辑扩展。
    public JsonExtension(ObjectMapper objectMapper) {
        this(objectMapper, objectMapper.createObjectNode());
    }

    // 包装已有 JSON 对象，读写直接作用于该节点。
    public JsonExtension(ObjectMapper objectMapper, ObjectNode objectNode) {
        this.objectMapper = objectMapper;
        this.objectNode = objectNode;
    }

    // 把强类型扩展序列化为 JSON 节点。
    public JsonExtension(ObjectMapper objectMapper, Extension e) {
        this(objectMapper, (ObjectNode) objectMapper.valueToTree(e));
    }

    // 从 JSON 节点读取元数据视图。
    @Override
    public @Nullable MetadataOperator getMetadata() {
        var metadataNode = objectNode.get("metadata");
        if (metadataNode == null) {
            return null;
        }
        return new ObjectNodeMetadata((ObjectNode) metadataNode);
    }

    // 将元数据序列化后写回 JSON 节点。
    @Override
    public void setMetadata(MetadataOperator metadata) {
        objectNode.set("metadata", objectMapper.valueToTree(metadata));
    }

    // 读取 JSON 中的 apiVersion 字段。
    @Override
    public @Nullable String getApiVersion() {
        var apiVersionNode = objectNode.get("apiVersion");
        return apiVersionNode == null ? null : apiVersionNode.asText();
    }

    // 以文本节点写入 apiVersion 字段。
    @Override
    public void setApiVersion(String apiVersion) {
        objectNode.set("apiVersion", new TextNode(apiVersion));
    }

    // 读取 JSON 中的 kind 字段。
    @Override
    public String getKind() {
        return objectNode.get("kind").asText();
    }

    // 以文本节点写入 kind 字段。
    @Override
    public void setKind(String kind) {
        objectNode.set("kind", new TextNode(kind));
    }

    // 暴露底层 JSON 节点供内部拼装使用。
    public ObjectNode getInternal() {
        return objectNode;
    }

    // 返回该扩展绑定的对象映射器。
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    // 确保 metadata 节点存在并返回可写视图。
    public MetadataOperator getMetadataOrCreate() {
        var metadataNode = objectMapper.createObjectNode();
        objectNode.set("metadata", metadataNode);
        return new ObjectNodeMetadata(metadataNode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonExtension that = (JsonExtension) o;
        return Objects.equals(objectNode, that.objectNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectNode);
    }

    public static class ObjectNodeExtensionSerializer extends JsonSerializer<JsonExtension> {

        // 直接把包装的 JSON 树写入输出流。
        @Override
        public void serialize(JsonExtension value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeTree(value.objectNode);
        }
    }

    public static class ObjectNodeExtensionDeSerializer extends JsonDeserializer<JsonExtension> {

        // 读取 JSON 树并包装为 JsonExtension。
        @Override
        public JsonExtension deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            var mapper = (ObjectMapper) p.getCodec();
            var treeNode = mapper.readTree(p);
            return new JsonExtension(mapper, (ObjectNode) treeNode);
        }
    }

    class ObjectNodeMetadata implements MetadataOperator {

        private final ObjectNode objectNode;

        // 包装 metadata 节点，读写直接落到该节点。
        public ObjectNodeMetadata(ObjectNode objectNode) {
            this.objectNode = objectNode;
        }

        // 读取名称，节点缺失时返回 null。
        @Override
        public String getName() {
            var nameNode = objectNode.get("name");
            return objectMapper.convertValue(nameNode, String.class);
        }

        // 仅非空名称写入，null 视为不修改。
        @Override
        public void setName(String name) {
            if (name != null) {
                objectNode.set("name", TextNode.valueOf(name));
            }
        }

        // 读取服务端生成名称所用的前缀。
        @Override
        public String getGenerateName() {
            var generateNameNode = objectNode.get("generateName");
            return objectMapper.convertValue(generateNameNode, String.class);
        }

        // 仅非空前缀写入，null 视为不修改。
        @Override
        public void setGenerateName(String generateName) {
            if (generateName != null) {
                objectNode.set("generateName", TextNode.valueOf(generateName));
            }
        }

        // 读取标签映射，节点缺失时返回 null。
        @Override
        public Map<String, String> getLabels() {
            var labelsNode = objectNode.get("labels");
            return objectMapper.convertValue(labelsNode, new TypeReference<>() {});
        }

        // 仅非空标签映射写入，null 视为不修改。
        @Override
        public void setLabels(Map<String, String> labels) {
            if (labels != null) {
                objectNode.set("labels", objectMapper.valueToTree(labels));
            }
        }

        // 读取注解映射，节点缺失时返回 null。
        @Override
        public Map<String, String> getAnnotations() {
            var annotationsNode = objectNode.get("annotations");
            return objectMapper.convertValue(annotationsNode, new TypeReference<>() {});
        }

        // 仅非空注解映射写入，null 视为不修改。
        @Override
        public void setAnnotations(Map<String, String> annotations) {
            if (annotations != null) {
                objectNode.set("annotations", objectMapper.valueToTree(annotations));
            }
        }

        // 读取乐观锁版本号。
        @Override
        public Long getVersion() {
            JsonNode versionNode = objectNode.get("version");
            return objectMapper.convertValue(versionNode, Long.class);
        }

        // 仅非空版本号写入，null 视为不修改。
        @Override
        public void setVersion(Long version) {
            if (version != null) {
                objectNode.set("version", LongNode.valueOf(version));
            }
        }

        // 读取创建时间，节点缺失时返回 null。
        @Override
        public Instant getCreationTimestamp() {
            return objectMapper.convertValue(objectNode.get("creationTimestamp"), Instant.class);
        }

        // 仅非空创建时间写入。
        @Override
        public void setCreationTimestamp(Instant creationTimestamp) {
            if (creationTimestamp != null) {
                objectNode.set("creationTimestamp", objectMapper.valueToTree(creationTimestamp));
            }
        }

        // 读取删除时间，非空表示正在删除。
        @Override
        public Instant getDeletionTimestamp() {
            return objectMapper.convertValue(objectNode.get("deletionTimestamp"), Instant.class);
        }

        // 仅非空删除时间写入。
        @Override
        public void setDeletionTimestamp(Instant deletionTimestamp) {
            if (deletionTimestamp != null) {
                objectNode.set("deletionTimestamp", objectMapper.valueToTree(deletionTimestamp));
            }
        }

        // 读取终结器集合，节点缺失时返回 null。
        @Override
        public Set<String> getFinalizers() {
            return objectMapper.convertValue(objectNode.get("finalizers"), new TypeReference<>() {});
        }

        // 仅非空终结器集合写入。
        @Override
        public void setFinalizers(Set<String> finalizers) {
            if (finalizers != null) {
                objectNode.set("finalizers", objectMapper.valueToTree(finalizers));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ObjectNodeMetadata that = (ObjectNodeMetadata) o;
            return Objects.equals(objectNode, that.objectNode);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(objectNode);
        }
    }
}
