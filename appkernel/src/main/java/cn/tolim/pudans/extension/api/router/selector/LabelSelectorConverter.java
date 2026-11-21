package cn.tolim.pudans.extension.api.router.selector;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import cn.tolim.pudans.extension.api.index.query.LabelCondition;
import cn.tolim.pudans.extension.api.index.query.Queries;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.CollectionUtils;

public class LabelSelectorConverter implements Converter<SelectorCriteria, LabelCondition> {

    @Override
    public LabelCondition convert(SelectorCriteria criteria) {
        switch (criteria.operator()) {
            case Equals -> {
                return Queries.labelEqual(criteria.key(), getSingleValue(criteria));
            }
            case NotEquals -> {
                return Queries.labelEqual(criteria.key(), getSingleValue(criteria))
                        .not();
            }
            case NotExist -> {
                return Queries.labelExists(criteria.key()).not();
            }
            case Exist -> {
                return Queries.labelExists(criteria.key());
            }
            case IN -> {
                return Queries.labelIn(criteria.key(), defaultIfNull(criteria.values(), Set.of()));
            }
            default -> throw new IllegalArgumentException("不支持的操作符：" + criteria.operator());
        }
    }

    String getSingleValue(SelectorCriteria criteria) {
        if (CollectionUtils.isEmpty(criteria.values())) {
            throw new IllegalArgumentException("标签键没有对应的值：" + criteria.key());
        }
        return criteria.values().iterator().next();
    }
}
