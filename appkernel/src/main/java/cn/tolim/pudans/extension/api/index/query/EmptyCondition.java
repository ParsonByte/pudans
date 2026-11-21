package cn.tolim.pudans.extension.api.index.query;

public record EmptyCondition() implements Condition {

    @Override
    public String toString() {
        return "EMPTY";
    }
}
