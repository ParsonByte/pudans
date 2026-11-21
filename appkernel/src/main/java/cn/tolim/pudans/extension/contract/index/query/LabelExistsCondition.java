package cn.tolim.pudans.extension.contract.index.query;

public record LabelExistsCondition(String labelKey) implements LabelCondition {

    // 取反得到标签不存在条件。
    @Override
    public LabelCondition not() {
        return new LabelNotExistsCondition(labelKey);
    }

    @Override
    public String toString() {
        return "EXISTS " + INDEX_NAME + "['" + labelKey + "']";
    }
}
