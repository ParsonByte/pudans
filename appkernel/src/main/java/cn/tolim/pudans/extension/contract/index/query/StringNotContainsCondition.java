package cn.tolim.pudans.extension.contract.index.query;

public record StringNotContainsCondition(String indexName, String keyword) implements IndexCondition {

    // 取反得到字符串包含条件。
    @Override
    public Condition not() {
        return new StringContainsCondition(indexName, keyword);
    }

    @Override
    public String toString() {
        return indexName + " NOT CONTAINS " + keyword;
    }
}
