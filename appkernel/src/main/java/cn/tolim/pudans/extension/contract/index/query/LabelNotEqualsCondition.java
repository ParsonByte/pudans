package cn.tolim.pudans.extension.contract.index.query;

public record LabelNotEqualsCondition(String labelKey, String labelValue) implements LabelCondition {

    // 取反得到标签相等条件。
    @Override
    public LabelCondition not() {
        return new LabelEqualsCondition(labelKey, labelValue);
    }

    @Override
    public String toString() {
        return INDEX_NAME + "['" + labelKey + "'] <> '" + labelValue + "'";
    }
}
