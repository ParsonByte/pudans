package cn.tolim.pudans.extension.contract;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;


@JsonDeserialize(as = Metadata.class)
@tools.jackson.databind.annotation.JsonDeserialize(as = Metadata.class)
@Schema(implementation = Metadata.class)
public interface MetadataOperator {

    // 逐字段比较两个元数据视图的内容。
    static boolean equals(MetadataOperator left, MetadataOperator right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getGenerateName(), right.getGenerateName())
                && Objects.equals(left.getLabels(), right.getLabels())
                && Objects.equals(left.getAnnotations(), right.getAnnotations())
                && Objects.equals(left.getCreationTimestamp(), right.getCreationTimestamp())
                && Objects.equals(left.getDeletionTimestamp(), right.getDeletionTimestamp())
                && Objects.equals(left.getVersion(), right.getVersion())
                && Objects.equals(left.getFinalizers(), right.getFinalizers());
    }

    // 与 equals 使用同一组字段生成哈希。
    static int hashCode(MetadataOperator metadata) {
        return Objects.hash(
                metadata.getName(),
                metadata.getGenerateName(),
                metadata.getLabels(),
                metadata.getAnnotations(),
                metadata.getCreationTimestamp(),
                metadata.getDeletionTimestamp(),
                metadata.getVersion(),
                metadata.getFinalizers());
    }

    // 元数据名称，同一类型内唯一。
    @Schema(name = "name", description = "Metadata name", requiredMode = REQUIRED)
    @JsonProperty("name")
    String getName();

    // 设置元数据名称。
    void setName(String name);

    // 名称前缀，交给服务端生成唯一名称。
    @Schema(
            name = "generateName",
            description =
                    "The name field will be generated automatically " + "according to the given generateName field")
    @Nullable
    String getGenerateName();

    // 设置服务端生成名称所用的前缀。
    void setGenerateName(String generateName);

    // 返回标签映射，可能为 null。
    @Schema(name = "labels")
    @JsonProperty("labels")
    @Nullable
    Map<String, String> getLabels();

    // 替换标签映射，null 可能被忽略。
    void setLabels(Map<String, String> labels);

    // 返回注解映射，可能为 null。
    @Schema(name = "annotations")
    @JsonProperty("annotations")
    @Nullable
    Map<String, String> getAnnotations();

    // 替换注解映射，null 可能被忽略。
    void setAnnotations(Map<String, String> annotations);

    // 返回乐观锁版本号，可能为 null。
    @Schema(name = "version", nullable = true)
    @JsonProperty("version")
    @Nullable
    Long getVersion();

    // 设置乐观锁版本号。
    void setVersion(Long version);

    // 返回创建时间，缺失时为 null。
    @Schema(name = "creationTimestamp", nullable = true)
    @JsonProperty("creationTimestamp")
    Instant getCreationTimestamp();

    // 设置创建时间，null 可能被忽略。
    void setCreationTimestamp(Instant creationTimestamp);

    // 返回删除时间，非空表示正在删除。
    @Schema(name = "deletionTimestamp", nullable = true)
    @JsonProperty("deletionTimestamp")
    @Nullable
    Instant getDeletionTimestamp();

    // 设置删除时间，由删除流程写入。
    void setDeletionTimestamp(Instant deletionTimestamp);

    // 返回终结器集合，可能为 null。
    @Schema(name = "finalizers", nullable = true)
    @Nullable
    Set<String> getFinalizers();

    // 替换终结器集合。
    void setFinalizers(Set<String> finalizers);
}
