package cn.tolim.pudans.contract.extension.index.query;

public record IsNullCondition(String indexName) implements IndexCondition {

    @Override
    public Condition not() {
        return new IsNotNullCondition(indexName);
    }

    @Override
    public String toString() {
        return indexName + " IS NULL";
    }
}
