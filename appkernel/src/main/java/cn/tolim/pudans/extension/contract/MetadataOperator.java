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

    @Schema(name = "name", description = "Metadata name", requiredMode = REQUIRED)
    @JsonProperty("name")
    String getName();

    void setName(String name);

    @Schema(
            name = "generateName",
            description =
                    "The name field will be generated automatically " + "according to the given generateName field")
    @Nullable
    String getGenerateName();

    void setGenerateName(String generateName);

    @Schema(name = "labels")
    @JsonProperty("labels")
    @Nullable
    Map<String, String> getLabels();

    void setLabels(Map<String, String> labels);

    @Schema(name = "annotations")
    @JsonProperty("annotations")
    @Nullable
    Map<String, String> getAnnotations();

    void setAnnotations(Map<String, String> annotations);

    @Schema(name = "version", nullable = true)
    @JsonProperty("version")
    @Nullable
    Long getVersion();

    void setVersion(Long version);

    @Schema(name = "creationTimestamp", nullable = true)
    @JsonProperty("creationTimestamp")
    Instant getCreationTimestamp();

    void setCreationTimestamp(Instant creationTimestamp);

    @Schema(name = "deletionTimestamp", nullable = true)
    @JsonProperty("deletionTimestamp")
    @Nullable
    Instant getDeletionTimestamp();

    void setDeletionTimestamp(Instant deletionTimestamp);

    @Schema(name = "finalizers", nullable = true)
    @Nullable
    Set<String> getFinalizers();

    void setFinalizers(Set<String> finalizers);
}
