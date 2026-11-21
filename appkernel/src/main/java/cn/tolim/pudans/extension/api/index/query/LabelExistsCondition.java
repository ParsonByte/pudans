package cn.tolim.pudans.extension.api.index.query;

public record LabelExistsCondition(String labelKey) implements LabelCondition {

    @Override
    public LabelCondition not() {
        return new LabelNotExistsCondition(labelKey);
    }

    @Override
    public String toString() {
        return "EXISTS " + INDEX_NAME + "['" + labelKey + "']";
    }
}
