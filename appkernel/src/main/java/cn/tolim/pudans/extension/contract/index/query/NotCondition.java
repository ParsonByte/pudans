package cn.tolim.pudans.extension.contract.index.query;

import org.springframework.util.Assert;

public record NotCondition(Condition condition) implements Condition {

    // 校验被包装条件非空。
    public NotCondition {
        Assert.notNull(condition, "条件不能为 null");
    }

    // 双重否定消除，返回原条件。
    @Override
    public Condition not() {
        return condition;
    }

    @Override
    public String toString() {
        return "NOT (" + condition + ")";
    }
}
