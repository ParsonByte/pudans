package cn.tolim.pudans.extension.api.index.query;

import org.springframework.util.Assert;

public record OrCondition(Condition left, Condition right) implements Condition {

    public OrCondition {
        Assert.notNull(left, "左侧条件不能为 null");
        Assert.notNull(right, "右侧条件不能为 null");
    }

    @Override
    public Condition not() {
        return left.not().and(right.not());
    }

    @Override
    public String toString() {
        return "(" + left + " OR " + right + ")";
    }
}
