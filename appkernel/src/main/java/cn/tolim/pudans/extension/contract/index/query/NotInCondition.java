package cn.tolim.pudans.extension.contract.index.query;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.util.Assert;

public record NotInCondition(String indexName, Collection<Object> keys) implements IndexCondition {

    // 校验键集合非空。
    public NotInCondition {
        Assert.notNull(keys, "索引 " + indexName + " 的键不能为空");
    }

    // 取反得到集合包含条件。
    @Override
    public Condition not() {
        return new InCondition(indexName, keys);
    }

    @Override
    public String toString() {
        return indexName + " NOT IN (" + keys.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
    }
}
