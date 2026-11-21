package cn.tolim.pudans.extension.contract.router.selector;

import cn.tolim.pudans.extension.contract.index.query.Condition;
import cn.tolim.pudans.extension.contract.index.query.LabelCondition;
import cn.tolim.pudans.extension.contract.index.query.Queries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.util.CollectionUtils;

@Data
@Accessors(chain = true)
public class LabelSelector {

    private List<LabelCondition> conditions;

    public static LabelSelectorBuilder<?> builder() {
        return new LabelSelectorBuilder<>();
    }

    @Override
    public String toString() {
        if (CollectionUtils.isEmpty(conditions)) {
            return Condition.empty().toString();
        }
        return conditions.stream()
                .map(c -> (Condition) c)
                .reduce(Condition::and)
                .orElseGet(Condition::empty)
                .toString();
    }

    public LabelSelector and(LabelSelector other) {
        var labelSelector = new LabelSelector();
        var newConditions = new ArrayList<LabelCondition>();
        newConditions.addAll(this.conditions);
        newConditions.addAll(other.conditions);
        labelSelector.setConditions(newConditions);
        return labelSelector;
    }

    public static class LabelSelectorBuilder<T extends LabelSelectorBuilder<T>> {
        private final List<LabelCondition> conditions = new ArrayList<>();

        public LabelSelectorBuilder() {}


        public LabelSelectorBuilder(List<LabelCondition> conditions) {
            if (conditions != null) {
                this.conditions.addAll(conditions);
            }
        }

        @SuppressWarnings("unchecked")
        private T self() {
            return (T) this;
        }

        public T eq(String key, String value) {
            conditions.add(Queries.labelEqual(key, value));
            return self();
        }

        public T notEq(String key, String value) {
            conditions.add(Queries.labelEqual(key, value).not());
            return self();
        }

        public T in(String key, String... values) {
            conditions.add(Queries.labelIn(key, Arrays.asList(values)));
            return self();
        }

        public T notIn(String key, String... values) {
            conditions.add(Queries.labelIn(key, Arrays.asList(values)).not());
            return self();
        }

        public T exists(String key) {
            conditions.add(Queries.labelExists(key));
            return self();
        }

        public T notExists(String key) {
            conditions.add(Queries.labelExists(key).not());
            return self();
        }


        public LabelSelector build() {
            var labelSelector = new LabelSelector();
            labelSelector.setConditions(conditions);
            return labelSelector;
        }
    }
}
