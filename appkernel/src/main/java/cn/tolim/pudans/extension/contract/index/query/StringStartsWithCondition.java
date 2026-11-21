package cn.tolim.pudans.extension.contract.index.query;

public record StringStartsWithCondition(String indexName, String prefix) implements IndexCondition {

    // 取反得到字符串前缀否定条件。
    @Override
    public Condition not() {
        return new StringNotStartsWithCondition(indexName, prefix);
    }

    @Override
    public String toString() {
        return indexName + " STARTS WITH " + prefix;
    }
}
