package cn.tolim.pudans.extension.contract.index.query;

import org.springframework.util.Assert;

public record NotEqualCondition(String indexName, Object key) implements IndexCondition {

    // 校验键非空，避免生成无效比较。
    public NotEqualCondition {
        Assert.notNull(key, "索引 " + indexName + " 的键不能为 null");
    }

    // 取反得到相等条件。
    @Override
    public Condition not() {
        return new EqualCondition(indexName, key);
    }

    @Override
    public String toString() {
        return indexName + " != " + key;
    }
}
