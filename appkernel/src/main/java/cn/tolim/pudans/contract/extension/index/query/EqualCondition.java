package cn.tolim.pudans.contract.extension.index.query;

import org.springframework.util.Assert;

public record EqualCondition(String indexName, Object key) implements IndexCondition {

    public EqualCondition {
        Assert.notNull(key, "索引 " + indexName + " 的键不能为 null");
    }

    @Override
    public Condition not() {
        return new NotEqualCondition(indexName, key);
    }

    @Override
    public String toString() {
        return indexName + " = " + key;
    }
}
