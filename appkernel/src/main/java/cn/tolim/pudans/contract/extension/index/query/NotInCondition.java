package cn.tolim.pudans.contract.extension.index.query;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.util.Assert;

public record NotInCondition(String indexName, Collection<Object> keys) implements IndexCondition {

    public NotInCondition {
        Assert.notNull(keys, "索引 " + indexName + " 的键不能为空");
    }

    @Override
    public Condition not() {
        return new InCondition(indexName, keys);
    }

    @Override
    public String toString() {
        return indexName + " NOT IN (" + keys.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
    }
}
