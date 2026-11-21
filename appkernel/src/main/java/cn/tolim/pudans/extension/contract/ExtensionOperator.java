package cn.tolim.pudans.extension.contract;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;


public interface ExtensionOperator {

    static <T extends ExtensionOperator> Predicate<T> isNotDeleted() {
        return ext -> ext.getMetadata().getDeletionTimestamp() == null;
    }

    static boolean isDeleted(ExtensionOperator extension) {
        return ExtensionUtil.isDeleted(extension);
    }

    @Schema(requiredMode = REQUIRED)
    @JsonProperty("apiVersion")
    default @Nullable String getApiVersion() {
        final var gvk = getClass().getAnnotation(GVK.class);
        if (gvk == null) {

            return null;
        }
        if (StringUtils.hasText(gvk.group())) {
            return gvk.group() + "/" + gvk.version();
        }
        return gvk.version();
    }

    void setApiVersion(String apiVersion);

    @Schema(requiredMode = REQUIRED)
    @JsonProperty("kind")
    default @Nullable String getKind() {
        final var gvk = getClass().getAnnotation(GVK.class);
        if (gvk == null) {

            return null;
        }
        return gvk.kind();
    }

    void setKind(String kind);

    @Schema(requiredMode = REQUIRED, implementation = Metadata.class)
    @JsonProperty("metadata")
    MetadataOperator getMetadata();

    void setMetadata(MetadataOperator metadata);

    default void groupVersionKind(GroupVersionKind gvk) {
        setApiVersion(gvk.groupVersion().toString());
        setKind(gvk.kind());
    }

    @JsonIgnore
    default GroupVersionKind groupVersionKind() {
        return GroupVersionKind.fromAPIVersionAndKind(getApiVersion(), getKind());
    }
}
