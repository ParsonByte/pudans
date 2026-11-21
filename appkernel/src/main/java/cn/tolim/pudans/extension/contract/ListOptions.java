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

    public static ListOptionsBuilder builder() {
        return new ListOptionsBuilder();
    }

    public static ListOptionsBuilder builder(ListOptions listOptions) {
        return new ListOptionsBuilder(listOptions);
    }

    @Override
    public String toString() {
        return toCondition().toString();
    }

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


        public ListOptionsBuilder(ListOptions listOptions) {
            if (listOptions.getLabelSelector() != null) {
                this.labelSelectorBuilder =
                        new LabelSelectorBuilder(listOptions.getLabelSelector().getConditions(), this);
            }
            if (listOptions.getFieldSelector() != null) {
                this.query = listOptions.getFieldSelector().query();
            }
        }


        public LabelSelectorBuilder labelSelector() {
            if (labelSelectorBuilder == null) {
                labelSelectorBuilder = new LabelSelectorBuilder(this);
            }
            return labelSelectorBuilder;
        }

        public ListOptionsBuilder fieldQuery(Query query) {
            this.query = query;
            return this;
        }


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

        public LabelSelectorBuilder(List<LabelCondition> conditions, ListOptionsBuilder listOptionsBuilder) {
            super(conditions);
            this.listOptionsBuilder = listOptionsBuilder;
        }

        public LabelSelectorBuilder(ListOptionsBuilder listOptionsBuilder) {
            this.listOptionsBuilder = listOptionsBuilder;
        }

        public ListOptionsBuilder end() {
            return this.listOptionsBuilder;
        }
    }
}
