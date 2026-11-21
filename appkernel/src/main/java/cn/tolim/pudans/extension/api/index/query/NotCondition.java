package cn.tolim.pudans.extension.api.index.query;

import org.springframework.util.Assert;

public record NotCondition(Condition condition) implements Condition {

    public NotCondition {
        Assert.notNull(condition, "条件不能为 null");
    }

    @Override
    public Condition not() {
        return condition;
    }

    @Override
    public String toString() {
        return "NOT (" + condition + ")";
    }
}
