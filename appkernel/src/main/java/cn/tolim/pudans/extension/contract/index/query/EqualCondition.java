package cn.tolim.pudans.extension.contract.index.query;

import org.springframework.util.Assert;

public record EqualCondition(String indexName, Object key) implements IndexCondition {

    // 校验键非空，避免生成无效比较。
    public EqualCondition {
        Assert.notNull(key, "索引 " + indexName + " 的键不能为 null");
    }

    // 取反得到不等条件。
    @Override
    public Condition not() {
        return new NotEqualCondition(indexName, key);
    }

    @Override
    public String toString() {
        return indexName + " = " + key;
    }
}
