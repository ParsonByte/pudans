package cn.tolim.pudans.extension.contract.index.query;

public record IsNullCondition(String indexName) implements IndexCondition {

    // 取反得到非空判断。
    @Override
    public Condition not() {
        return new IsNotNullCondition(indexName);
    }

    @Override
    public String toString() {
        return indexName + " IS NULL";
    }
}
