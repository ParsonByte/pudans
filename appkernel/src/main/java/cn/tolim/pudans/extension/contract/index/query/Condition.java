package cn.tolim.pudans.extension.contract.index.query;

import org.springframework.data.relational.core.sql.Visitable;


public interface Condition extends Visitable, Query {


    // 返回不附加任何限制的空条件。
    static Condition empty() {
        return new EmptyCondition();
    }

    // 与另一个条件组合为与条件。
    default Condition and(Condition another) {
        return new AndCondition(this, another);
    }

    // 与另一个条件组合为或条件。
    default Condition or(Condition another) {
        return new OrCondition(this, another);
    }

    // 返回当前条件的否定。
    default Condition not() {
        return new NotCondition(this);
    }
}
