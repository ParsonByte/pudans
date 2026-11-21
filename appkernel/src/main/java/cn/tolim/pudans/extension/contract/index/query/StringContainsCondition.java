package cn.tolim.pudans.extension.contract.index.query;

public record StringContainsCondition(String indexName, String keyword) implements IndexCondition {

    @Override
    public Condition not() {
        return new StringNotContainsCondition(indexName, keyword);
    }

    @Override
    public String toString() {
        return indexName + " CONTAINS " + keyword;
    }
}
