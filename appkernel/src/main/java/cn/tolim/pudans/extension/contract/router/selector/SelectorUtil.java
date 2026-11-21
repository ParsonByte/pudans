package cn.tolim.pudans.extension.contract.router.selector;

import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.index.query.Condition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SelectorUtil {

    private SelectorUtil() {}


    // 把标签与字段选择器表达式解析为列表选项。
    public static ListOptions labelAndFieldSelectorToListOptions(
            List<String> labelSelectorTerms, List<String> fieldSelectorTerms) {
        var selectorConverter = new SelectorConverter();

        var labelConverter = new LabelSelectorConverter();
        var labelConditions = Optional.ofNullable(labelSelectorTerms)
                .map(selectors -> selectors.stream()
                        .map(selectorConverter::convert)
                        .filter(Objects::nonNull)
                        .map(labelConverter::convert)
                        .toList())
                .orElse(List.of());

        var fieldConverter = new FieldSelectorConverter();
        var fieldCondition = Optional.ofNullable(fieldSelectorTerms)
                .map(selectors -> selectors.stream()
                        .map(selectorConverter::convert)
                        .filter(Objects::nonNull)
                        .map(fieldConverter::convert)
                        .reduce(Condition::and)
                        .orElse(null))
                .orElse(null);
        var listOptions = new ListOptions();
        listOptions.setLabelSelector(new LabelSelector().setConditions(labelConditions));
        if (fieldCondition != null) {
            listOptions.setFieldSelector(FieldSelector.of(fieldCondition));
        } else {
            listOptions.setFieldSelector(FieldSelector.all());
        }
        return listOptions;
    }
}
