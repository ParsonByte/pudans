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

    // 创建标签选择器构建器。
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

    // 合并两组标签条件，返回新选择器。
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


        // 复制已有标签条件。
        public LabelSelectorBuilder(List<LabelCondition> conditions) {
            if (conditions != null) {
                this.conditions.addAll(conditions);
            }
        }

        // 以具体构建器类型返回自身。
        @SuppressWarnings("unchecked")
        private T self() {
            return (T) this;
        }

        // 追加标签相等条件。
        public T eq(String key, String value) {
            conditions.add(Queries.labelEqual(key, value));
            return self();
        }

        // 追加标签不等条件。
        public T notEq(String key, String value) {
            conditions.add(Queries.labelEqual(key, value).not());
            return self();
        }

        // 追加标签值集合条件。
        public T in(String key, String... values) {
            conditions.add(Queries.labelIn(key, Arrays.asList(values)));
            return self();
        }

        // 追加标签值排除条件。
        public T notIn(String key, String... values) {
            conditions.add(Queries.labelIn(key, Arrays.asList(values)).not());
            return self();
        }

        // 追加标签存在条件。
        public T exists(String key) {
            conditions.add(Queries.labelExists(key));
            return self();
        }

        // 追加标签不存在条件。
        public T notExists(String key) {
            conditions.add(Queries.labelExists(key).not());
            return self();
        }


        // 生成包含当前条件的标签选择器。
        public LabelSelector build() {
            var labelSelector = new LabelSelector();
            labelSelector.setConditions(conditions);
            return labelSelector;
        }
    }
}
