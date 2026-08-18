package cn.tolim.pudans.contract.extension.router.selector;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import cn.tolim.pudans.contract.extension.index.query.Condition;
import cn.tolim.pudans.contract.extension.index.query.Queries;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.CollectionUtils;

public class FieldSelectorConverter implements Converter<SelectorCriteria, Condition> {

    @Override
    public Condition convert(SelectorCriteria criteria) {
        var key = criteria.key();

        if ("name".equals(key)) {
            key = "metadata.name";
        }
        switch (criteria.operator()) {
            case Equals -> {
                return Queries.equal(key, getSingleValue(criteria));
            }
            case NotEquals -> {
                return Queries.notEqual(key, getSingleValue(criteria));
            }

            case IN -> {
                Set<String> valueArr = defaultIfNull(criteria.values(), Set.of());
                return Queries.in(key, valueArr);
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + criteria.operator());
        }
    }

    String getSingleValue(SelectorCriteria criteria) {
        if (CollectionUtils.isEmpty(criteria.values())) {
            throw new IllegalArgumentException("No value present for label key: " + criteria.key());
        }
        return criteria.values().iterator().next();
    }
}
