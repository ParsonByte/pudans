package cn.tolim.pudans.extension.contract.index.query;

public record StringContainsCondition(String indexName, String keyword) implements IndexCondition {

    // 取反得到字符串不包含条件。
    @Override
    public Condition not() {
        return new StringNotContainsCondition(indexName, keyword);
    }

    @Override
    public String toString() {
        return indexName + " CONTAINS " + keyword;
    }
}
