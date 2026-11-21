package cn.tolim.pudans.extension.contract.index.query;

public record StringNotEndsWithCondition(String indexName, String suffix) implements IndexCondition {

    // 取反得到字符串后缀条件。
    @Override
    public Condition not() {
        return new StringEndsWithCondition(indexName, suffix);
    }

    @Override
    public String toString() {
        return indexName + " NOT ENDS WITH " + suffix;
    }
}
