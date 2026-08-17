package cn.tolim.pudans.contract.extension.index.query;

public record EmptyCondition() implements Condition {

    @Override
    public String toString() {
        return "EMPTY";
    }
}
