package cn.tolim.pudans.extension.contract.index.query;

public record StringNotEndsWithCondition(String indexName, String suffix) implements IndexCondition {

    @Override
    public Condition not() {
        return new StringEndsWithCondition(indexName, suffix);
    }

    @Override
    public String toString() {
        return indexName + " NOT ENDS WITH " + suffix;
    }
}
