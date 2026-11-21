package cn.tolim.pudans.extension.contract.index.query;

public record EmptyCondition() implements Condition {

    @Override
    public String toString() {
        return "EMPTY";
    }
}
