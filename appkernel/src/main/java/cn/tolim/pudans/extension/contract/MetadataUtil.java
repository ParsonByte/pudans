package cn.tolim.pudans.extension.contract;

import java.util.HashMap;
import java.util.Map;
import org.springframework.util.Assert;

public enum MetadataUtil {
    ;

    public static final String SYSTEM_FINALIZER = "system-protection";
    public static final String HIDDEN_LABEL = "pudans.tolim.cn/hidden";


    // 确保标签映射可写，缺失时创建空映射。
    public static Map<String, String> nullSafeLabels(AbstractExtension extension) {
        Assert.notNull(extension, "扩展不能为 null。");
        Assert.notNull(extension.getMetadata(), "扩展的 metadata 不能为 null。");
        Map<String, String> labels = extension.getMetadata().getLabels();
        if (labels == null) {
            labels = new HashMap<>();
            extension.getMetadata().setLabels(labels);
        }
        return labels;
    }


    // 确保注解映射可写，缺失时创建空映射。
    public static Map<String, String> nullSafeAnnotations(AbstractExtension extension) {
        Assert.notNull(extension, "扩展不能为 null。");
        Assert.notNull(extension.getMetadata(), "扩展的 metadata 不能为 null。");
        Map<String, String> annotations = extension.getMetadata().getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
            extension.getMetadata().setAnnotations(annotations);
        }
        return annotations;
    }
}
