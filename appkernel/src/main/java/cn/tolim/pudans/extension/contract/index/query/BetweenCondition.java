package cn.tolim.pudans.extension.contract.index.query;

public record BetweenCondition(String indexName, Object fromKey, boolean fromInclusive, Object toKey,
                               boolean toInclusive)
        implements IndexCondition {

    // 取反得到不在区间内的条件。
    @Override
    public Condition not() {
        return new NotBetweenCondition(indexName, fromKey, fromInclusive, toKey, toInclusive);
    }

    @Override
    public String toString() {
        return indexName + " BETWEEN "
                + (fromInclusive ? "[" : "(")
                + fromKey
                + ", "
                + toKey
                + (toInclusive ? "]" : ")");
    }
}
