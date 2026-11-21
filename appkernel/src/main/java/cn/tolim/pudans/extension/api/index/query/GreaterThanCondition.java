package cn.tolim.pudans.extension.api.index.query;

public record GreaterThanCondition(String indexName, Object lowerBound, boolean inclusive) implements IndexCondition {

    @Override
    public Condition not() {
        return new LessThanCondition(indexName, lowerBound, !inclusive);
    }

    @Override
    public String toString() {
        return indexName + (inclusive ? " >= " : " > ") + lowerBound;
    }
}
