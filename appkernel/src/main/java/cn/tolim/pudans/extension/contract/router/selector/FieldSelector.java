package cn.tolim.pudans.extension.contract.router.selector;

import cn.tolim.pudans.extension.contract.index.query.Condition;
import cn.tolim.pudans.extension.contract.index.query.Queries;
import cn.tolim.pudans.extension.contract.index.query.Query;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public record FieldSelector(@Nullable Query query) {

    // 把 null 查询归一化为空条件。
    public FieldSelector(Query query) {
        this.query = Objects.requireNonNullElseGet(query, Queries::empty);
    }

    // 包装查询为字段选择器。
    public static FieldSelector of(Query query) {
        return new FieldSelector(query);
    }

    // 返回匹配全部的选择器。
    public static FieldSelector all() {
        return new FieldSelector(Queries.empty());
    }

    // 仅支持条件查询，按与合并后返回新选择器。
    public FieldSelector andQuery(Query other) {
        Assert.isInstanceOf(Condition.class, other, "仅支持 Condition 查询");
        Assert.isInstanceOf(Condition.class, query, "仅支持 Condition 查询");
        return of(((Condition) query).and((Condition) other));
    }
}
