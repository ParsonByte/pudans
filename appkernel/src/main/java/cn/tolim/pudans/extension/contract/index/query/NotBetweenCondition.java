package cn.tolim.pudans.extension.contract.index.query;

public record NotBetweenCondition(String indexName, Object fromKey, boolean fromInclusive, Object toKey,
                                  boolean toInclusive)
        implements IndexCondition {

    // 取反得到区间内的条件。
    @Override
    public Condition not() {
        return new BetweenCondition(indexName, fromKey, fromInclusive, toKey, toInclusive);
    }

    @Override
    public String toString() {
        return indexName + " NOT BETWEEN "
                + (fromInclusive ? "[" : "(")
                + fromKey
                + ", "
                + toKey
                + (toInclusive ? "]" : ")");
    }
}
