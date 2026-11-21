package cn.tolim.pudans.extension.contract.index.query;

public record AllCondition(String indexName) implements Condition {

    @Override
    public Condition not() {
        return new NoneCondition(indexName);
    }

    @Override
    public String toString() {
        return "ALL " + indexName;
    }
}
