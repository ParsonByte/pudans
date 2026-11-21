package cn.tolim.pudans.extension.contract;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;


public interface ExtensionOperator {

    // 筛选删除时间戳为空的存活扩展。
    static <T extends ExtensionOperator> Predicate<T> isNotDeleted() {
        return ext -> ext.getMetadata().getDeletionTimestamp() == null;
    }

    // 委托工具类判断删除标记，保持语义一致。
    static boolean isDeleted(ExtensionOperator extension) {
        return ExtensionUtil.isDeleted(extension);
    }

    // 从 GVK 注解推导版本，无注解时为 null。
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

    // 写入显式 apiVersion，供反序列化使用。
    void setApiVersion(String apiVersion);

    // 从 GVK 注解读取 Kind，无注解时返回 null。
    @Schema(requiredMode = REQUIRED)
    @JsonProperty("kind")
    default @Nullable String getKind() {
        final var gvk = getClass().getAnnotation(GVK.class);
        if (gvk == null) {

            return null;
        }
        return gvk.kind();
    }

    // 写入显式 Kind，供反序列化使用。
    void setKind(String kind);

    // 获取扩展元数据，承载名称与标签等属性。
    @Schema(requiredMode = REQUIRED, implementation = Metadata.class)
    @JsonProperty("metadata")
    MetadataOperator getMetadata();

    // 设置扩展元数据，具体实现决定存储方式。
    void setMetadata(MetadataOperator metadata);

    // 按 GVK 同时写入 apiVersion 与 kind。
    default void groupVersionKind(GroupVersionKind gvk) {
        setApiVersion(gvk.groupVersion().toString());
        setKind(gvk.kind());
    }

    // 由版本字段与 Kind 还原资源标识。
    @JsonIgnore
    default GroupVersionKind groupVersionKind() {
        return GroupVersionKind.fromAPIVersionAndKind(getApiVersion(), getKind());
    }
}
