package cn.tolim.pudans.extension.contract.index.query;

public record GreaterThanCondition(String indexName, Object lowerBound, boolean inclusive) implements IndexCondition {

    // 取反得到对应的小于条件。
    @Override
    public Condition not() {
        return new LessThanCondition(indexName, lowerBound, !inclusive);
    }

    @Override
    public String toString() {
        return indexName + (inclusive ? " >= " : " > ") + lowerBound;
    }
}
