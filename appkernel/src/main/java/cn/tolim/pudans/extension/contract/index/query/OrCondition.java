package cn.tolim.pudans.extension.contract.index.query;

import org.springframework.util.Assert;

public record OrCondition(Condition left, Condition right) implements Condition {

    // 校验两侧条件均非空。
    public OrCondition {
        Assert.notNull(left, "左侧条件不能为 null");
        Assert.notNull(right, "右侧条件不能为 null");
    }

    // 德摩根律：或的否定转为与的否定。
    @Override
    public Condition not() {
        return left.not().and(right.not());
    }

    @Override
    public String toString() {
        return "(" + left + " OR " + right + ")";
    }
}
