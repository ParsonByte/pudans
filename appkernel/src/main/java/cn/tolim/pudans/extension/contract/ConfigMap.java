package cn.tolim.pudans.extension.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@GVK(group = "", version = "v1alpha1", kind = ConfigMap.KIND, plural = "configmaps", singular = "configmap")
public class ConfigMap extends AbstractExtension {

    public static final String KIND = "ConfigMap";


    @Nullable
    private Map<String, String> data;

    // 按需创建数据表，避免空配置携带空集合。
    public ConfigMap putDataItem(String key, String dataItem) {
        if (this.data == null) {
            this.data = new LinkedHashMap<>();
        }
        this.data.put(key, dataItem);
        return this;
    }
}
