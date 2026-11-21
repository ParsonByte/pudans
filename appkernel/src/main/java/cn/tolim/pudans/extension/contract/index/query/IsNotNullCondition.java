package cn.tolim.pudans.extension.contract.index.query;

public record IsNotNullCondition(String indexName) implements IndexCondition {

    // 取反得到空值判断。
    @Override
    public Condition not() {
        return new IsNullCondition(indexName);
    }

    @Override
    public String toString() {
        return indexName + " IS NOT NULL";
    }
}
