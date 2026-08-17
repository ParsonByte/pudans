package cn.tolim.pudans.contract.extension;

import java.util.HashMap;
import java.util.Map;
import org.springframework.util.Assert;

public enum MetadataUtil {
    ;

    public static final String SYSTEM_FINALIZER = "system-protection";
    public static final String HIDDEN_LABEL = "pudans.tolim.cn/hidden";


    public static Map<String, String> nullSafeLabels(AbstractExtension extension) {
        Assert.notNull(extension, "The extension must not be null.");
        Assert.notNull(extension.getMetadata(), "The extension metadata must not be null.");
        Map<String, String> labels = extension.getMetadata().getLabels();
        if (labels == null) {
            labels = new HashMap<>();
            extension.getMetadata().setLabels(labels);
        }
        return labels;
    }


    public static Map<String, String> nullSafeAnnotations(AbstractExtension extension) {
        Assert.notNull(extension, "The extension must not be null.");
        Assert.notNull(extension.getMetadata(), "The extension metadata must not be null.");
        Map<String, String> annotations = extension.getMetadata().getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
            extension.getMetadata().setAnnotations(annotations);
        }
        return annotations;
    }
}
