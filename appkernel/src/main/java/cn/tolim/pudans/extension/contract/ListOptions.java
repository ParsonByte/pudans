package cn.tolim.pudans.extension.contract;

import cn.tolim.pudans.extension.contract.index.query.Condition;
import cn.tolim.pudans.extension.contract.index.query.LabelCondition;
import cn.tolim.pudans.extension.contract.index.query.Query;
import cn.tolim.pudans.extension.contract.router.selector.FieldSelector;
import cn.tolim.pudans.extension.contract.router.selector.LabelSelector;
import java.util.List;
import java.util.function.Function;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

@Data
@Accessors(chain = true)
public class ListOptions {

    private @Nullable LabelSelector labelSelector;

    private @Nullable FieldSelector fieldSelector;

    // 创建空的选择条件构建器。
    public static ListOptionsBuilder builder() {
        return new ListOptionsBuilder();
    }

    // 复制已有选择条件创建构建器。
    public static ListOptionsBuilder builder(ListOptions listOptions) {
        return new ListOptionsBuilder(listOptions);
    }

    @Override
    public String toString() {
        return toCondition().toString();
    }

    // 把字段与标签选择器合并为单一条件。
    public Condition toCondition() {
        Condition condition = null;
        var fieldSelector = getFieldSelector();
        if (fieldSelector != null && fieldSelector.query() != null) {
            var query = fieldSelector.query();
            if (!(query instanceof Condition fieldCondition)) {
                throw new IllegalArgumentException("仅支持条件查询");
            }
            condition = fieldCondition;
        }
        var labelSelector = getLabelSelector();
        if (labelSelector != null) {
            var labelCondition = labelSelector.getConditions().stream()
                    .map(Function.<Condition>identity())
                    .reduce(Condition::and)
                    .orElse(null);
            if (labelCondition != null) {
                if (condition == null) {
                    condition = labelCondition;
                } else {
                    condition = condition.and(labelCondition);
                }
            }
        }
        return condition == null ? Condition.empty() : condition;
    }

    public static class ListOptionsBuilder {
        private LabelSelectorBuilder labelSelectorBuilder;

        private @Nullable Query query;

        public ListOptionsBuilder() {}


        // 从已有选项复制标签条件与字段查询。
        public ListOptionsBuilder(ListOptions listOptions) {
            if (listOptions.getLabelSelector() != null) {
                this.labelSelectorBuilder =
                        new LabelSelectorBuilder(listOptions.getLabelSelector().getConditions(), this);
            }
            if (listOptions.getFieldSelector() != null) {
                this.query = listOptions.getFieldSelector().query();
            }
        }


        // 惰性创建标签选择器构建器。
        public LabelSelectorBuilder labelSelector() {
            if (labelSelectorBuilder == null) {
                labelSelectorBuilder = new LabelSelectorBuilder(this);
            }
            return labelSelectorBuilder;
        }

        // 直接替换字段查询条件。
        public ListOptionsBuilder fieldQuery(Query query) {
            this.query = query;
            return this;
        }


        // 把新查询与现有条件按与组合。
        public ListOptionsBuilder andQuery(Query query) {
            if (!(query instanceof Condition condition)) {
                throw new IllegalArgumentException("给定的查询必须是 Condition 的实例");
            }
            if (this.query == null) {
                this.query = condition;
            } else {
                if (!(this.query instanceof Condition currentCondition)) {
                    throw new IllegalArgumentException("当前查询必须是 Condition 的实例");
                }
                this.query = currentCondition.and(condition);
            }
            return this;
        }


        // 把新查询与现有条件按或组合。
        public ListOptionsBuilder orQuery(Query query) {
            if (!(query instanceof Condition condition)) {
                throw new IllegalArgumentException("给定的查询必须是 Condition 的实例");
            }
            if (this.query == null) {
                this.query = condition;
            } else {
                if (!(this.query instanceof Condition currentCondition)) {
                    throw new IllegalArgumentException("当前查询必须是 Condition 的实例");
                }
                this.query = currentCondition.or(condition);
            }
            return this;
        }


        // 生成不可变的选择条件快照。
        public ListOptions build() {
            var listOptions = new ListOptions();
            if (labelSelectorBuilder != null) {
                listOptions.setLabelSelector(labelSelectorBuilder.build());
            }
            if (query != null) {
                listOptions.setFieldSelector(FieldSelector.of(query));
            }
            return listOptions;
        }
    }

    public static class LabelSelectorBuilder extends LabelSelector.LabelSelectorBuilder<LabelSelectorBuilder> {
        private final ListOptionsBuilder listOptionsBuilder;

        // 包装已有标签条件并回指父构建器。
        public LabelSelectorBuilder(List<LabelCondition> conditions, ListOptionsBuilder listOptionsBuilder) {
            super(conditions);
            this.listOptionsBuilder = listOptionsBuilder;
        }

        // 以父构建器创建空标签选择器。
        public LabelSelectorBuilder(ListOptionsBuilder listOptionsBuilder) {
            this.listOptionsBuilder = listOptionsBuilder;
        }

        // 结束标签条件编辑，回到选项构建器。
        public ListOptionsBuilder end() {
            return this.listOptionsBuilder;
        }
    }
}
