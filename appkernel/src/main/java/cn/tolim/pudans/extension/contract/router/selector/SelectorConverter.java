package cn.tolim.pudans.extension.contract.router.selector;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;

@Slf4j
public class SelectorConverter implements Converter<String, SelectorCriteria> {

    // 按操作符优先级依次尝试解析选择器。
    @Override
    public @Nullable SelectorCriteria convert(@Nullable String selector) {
        return Arrays.stream(Operator.values())
                .sorted(Comparator.comparing(Operator::getOrder))
                .map(operator -> {
                    log.debug("正在解析选择器：{}，操作符：{}", selector, operator);
                    return operator.convert(selector);
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
