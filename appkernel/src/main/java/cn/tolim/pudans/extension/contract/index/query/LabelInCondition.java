package cn.tolim.pudans.extension.contract.index.query;

import java.util.Collection;
import org.springframework.util.Assert;

public record LabelInCondition(String labelKey, Collection<String> labelValues) implements LabelCondition {

    // 校验标签值集合非空。
    public LabelInCondition {
        Assert.notNull(labelValues, "标签 " + labelKey + " 的 labelValues 不能为 null");
    }

    // 取反得到标签排除条件。
    @Override
    public LabelCondition not() {
        return new LabelNotInCondition(labelKey, labelValues);
    }

    @Override
    public String toString() {
        return INDEX_NAME + "['" + labelKey + "'] IN ("
                + String.join(", ", labelValues.stream().map(v -> "'" + v + "'").toList())
                + ")";
    }
}
