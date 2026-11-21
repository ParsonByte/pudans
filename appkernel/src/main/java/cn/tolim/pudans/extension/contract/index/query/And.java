package cn.tolim.pudans.extension.contract.index.query;


public record And(Condition left, Condition right) implements Condition {

    @Override
    public Condition not() {
        return left.not().or(right.not());
    }

    @Override
    public String toString() {
        return "(" + left + " AND " + right + ")";
    }
}
