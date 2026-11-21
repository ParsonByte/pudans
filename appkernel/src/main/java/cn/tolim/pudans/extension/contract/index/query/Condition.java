package cn.tolim.pudans.extension.contract.index.query;

import org.springframework.data.relational.core.sql.Visitable;


public interface Condition extends Visitable, Query {


    static Condition empty() {
        return new EmptyCondition();
    }

    default Condition and(Condition another) {
        return new AndCondition(this, another);
    }

    default Condition or(Condition another) {
        return new OrCondition(this, another);
    }

    default Condition not() {
        return new NotCondition(this);
    }
}
