package cn.tolim.pudans.extension.api.index.query;

public record LabelNotEqualsCondition(String labelKey, String labelValue) implements LabelCondition {

    @Override
    public LabelCondition not() {
        return new LabelEqualsCondition(labelKey, labelValue);
    }

    @Override
    public String toString() {
        return INDEX_NAME + "['" + labelKey + "'] <> '" + labelValue + "'";
    }
}
