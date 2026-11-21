package cn.tolim.pudans.extension.contract.index.query;

public record LessThanCondition(String indexName, Object upperBound, boolean inclusive) implements IndexCondition {

    // 取反得到对应的大于条件。
    @Override
    public Condition not() {
        return new GreaterThanCondition(indexName, upperBound, !inclusive);
    }

    @Override
    public String toString() {
        return indexName + (inclusive ? " <= " : " < ") + upperBound;
    }
}
