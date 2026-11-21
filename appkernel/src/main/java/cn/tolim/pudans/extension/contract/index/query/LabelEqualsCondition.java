package cn.tolim.pudans.extension.contract.index.query;

public record LabelEqualsCondition(String labelKey, String labelValue) implements LabelCondition {

    // 取反得到标签不等条件。
    @Override
    public LabelCondition not() {
        return new LabelNotEqualsCondition(labelKey, labelValue);
    }

    @Override
    public String toString() {
        return INDEX_NAME + "['" + labelKey + "'] = '" + labelValue + "'";
    }
}
