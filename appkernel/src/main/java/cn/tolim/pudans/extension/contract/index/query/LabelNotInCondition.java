package cn.tolim.pudans.extension.contract.index.query;

import java.util.Collection;
import org.springframework.util.Assert;

public record LabelNotInCondition(String labelKey, Collection<String> labelValues) implements LabelCondition {

    // 校验标签值集合非空。
    public LabelNotInCondition {
        Assert.notNull(labelValues, "标签 " + labelKey + " 的 labelValues 不能为 null");
    }

    // 取反得到标签包含条件。
    @Override
    public LabelCondition not() {
        return new LabelInCondition(labelKey, labelValues);
    }

    @Override
    public String toString() {
        return INDEX_NAME + "['" + labelKey + "'] NOT IN ("
                + String.join(", ", labelValues.stream().map(v -> "'" + v + "'").toList())
                + ")";
    }
}
