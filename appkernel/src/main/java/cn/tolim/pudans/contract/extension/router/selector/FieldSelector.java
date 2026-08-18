package cn.tolim.pudans.contract.extension.router.selector;

import cn.tolim.pudans.contract.extension.index.query.Condition;
import cn.tolim.pudans.contract.extension.index.query.Queries;
import cn.tolim.pudans.contract.extension.index.query.Query;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public record FieldSelector(@Nullable Query query) {

    public FieldSelector(Query query) {
        this.query = Objects.requireNonNullElseGet(query, Queries::empty);
    }

    public static FieldSelector of(Query query) {
        return new FieldSelector(query);
    }

    public static FieldSelector all() {
        return new FieldSelector(Queries.empty());
    }

    public FieldSelector andQuery(Query other) {
        Assert.isInstanceOf(Condition.class, other, "Only Condition query is supported");
        Assert.isInstanceOf(Condition.class, query, "Only Condition query is supported");
        return of(((Condition) query).and((Condition) other));
    }
}
