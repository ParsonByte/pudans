package cn.tolim.pudans.extension.contract;

import java.util.HashMap;
import java.util.Map;

public final class ReservedLabels {

    public static final String PREFIX = "pudans.tolim.cn/";

    private ReservedLabels() {}

    // 提取保留前缀下的标签，供内部逻辑使用。
    public static Map<String, String> of(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Map.of();
        }
        var reserved = new HashMap<String, String>();
        labels.forEach((key, value) -> {
            if (key != null && key.startsWith(PREFIX)) {
                reserved.put(key, value);
            }
        });
        return reserved;
    }
}
