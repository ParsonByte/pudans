package cn.tolim.pudans.extension.contract.index.query;

public record StringEndsWithCondition(String indexName, String suffix) implements IndexCondition {

    // 取反得到不以指定后缀结尾的条件。
    @Override
    public Condition not() {
        return new StringNotEndsWithCondition(indexName, suffix);
    }

    @Override
    public String toString() {
        return indexName + " ENDS WITH " + suffix;
    }
}
