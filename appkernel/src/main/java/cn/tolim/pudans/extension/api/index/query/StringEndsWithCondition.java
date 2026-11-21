package cn.tolim.pudans.extension.api.index.query;

public record StringEndsWithCondition(String indexName, String suffix) implements IndexCondition {

    @Override
    public Condition not() {
        return new StringNotEndsWithCondition(indexName, suffix);
    }

    @Override
    public String toString() {
        return indexName + " ENDS WITH " + suffix;
    }
}
