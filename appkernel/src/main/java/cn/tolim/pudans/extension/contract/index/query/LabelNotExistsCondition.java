package cn.tolim.pudans.extension.contract.index.query;

public record LabelNotExistsCondition(String labelKey) implements LabelCondition {

    // 取反得到标签存在条件。
    @Override
    public LabelCondition not() {
        return new LabelExistsCondition(labelKey);
    }

    @Override
    public String toString() {
        return "NOT EXISTS " + INDEX_NAME + "['" + labelKey + "']";
    }
}
