package cn.tolim.pudans.extension.contract.index.query;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.util.Assert;

public record InCondition(String indexName, Collection<Object> keys) implements IndexCondition {

    public InCondition {
        Assert.notNull(keys, "索引 " + indexName + " 的键不能为空");
    }

    @Override
    public Condition not() {
        return new NotInCondition(indexName, keys);
    }

    @Override
    public String toString() {
        return indexName + " IN (" + keys.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
    }
}
