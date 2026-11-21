package cn.tolim.pudans.extension.api.index.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.springframework.util.Assert;


public enum Queries {
    ;


    public static Condition and(Condition condition, Condition... additionalConditions) {
        Assert.notNull(condition, "条件不能为 null");
        return Arrays.stream(additionalConditions).reduce(condition, Condition::and);
    }


    public static Condition or(Condition condition, Condition... additionalConditions) {
        Assert.notNull(condition, "条件不能为 null");
        return Arrays.stream(additionalConditions).reduce(condition, Condition::or);
    }


    public static Condition not(Condition condition) {
        Assert.notNull(condition, "条件不能为 null");
        return condition.not();
    }


    public static Condition between(
            String fieldName, Object fromValue, boolean fromInclusive, Object toValue, boolean toInclusive) {
        return new BetweenCondition(fieldName, fromValue, fromInclusive, toValue, toInclusive);
    }


    public static Condition empty() {
        return new EmptyCondition();
    }


    public static Condition all(String fieldName) {
        return new AllCondition(fieldName);
    }


    public static Condition isNull(String fieldName) {
        return new IsNullCondition(fieldName);
    }


    public static Condition equal(String fieldName, Object attributeValue) {
        Assert.notNull(attributeValue, "字段 " + fieldName + " 的属性键不能为 null");
        return new EqualCondition(fieldName, attributeValue);
    }


    public static Condition greaterThan(String fieldName, Object attributeValue, boolean inclusive) {
        return new GreaterThanCondition(fieldName, attributeValue, inclusive);
    }


    public static Condition greaterThan(String fieldName, Object attributeValue) {
        return greaterThan(fieldName, attributeValue, false);
    }


    @SuppressWarnings("unchecked")
    public static Condition in(String fieldName, Object attributeValue, Object... additionalValues) {
        Assert.notNull(attributeValue, "字段 " + fieldName + " 的属性键不能为 null");
        if (attributeValue instanceof Collection<?> collection) {

            return in(fieldName, (Collection<Object>) collection);
        }
        if (additionalValues == null) {
            return equal(fieldName, attributeValue);
        }
        var values = new ArrayList<>(additionalValues.length + 1);
        values.add(attributeValue);
        values.addAll(Arrays.asList(additionalValues));
        return in(fieldName, values);
    }


    public static Condition in(String fieldName, Collection<Object> values) {
        Assert.notNull(values, "值不能为 null");
        if (values.size() == 1) {
            var value = values.iterator().next();
            return equal(fieldName, value);
        }
        return new InCondition(fieldName, values);
    }


    public static Condition lessThan(String fieldName, Object attributeValue, boolean inclusive) {
        return new LessThanCondition(fieldName, attributeValue, inclusive);
    }


    public static Condition lessThan(String fieldName, Object attributeValue) {
        return lessThan(fieldName, attributeValue, false);
    }


    public static Condition notEqual(String fieldName, Object attributeValue) {
        return new NotEqualCondition(fieldName, attributeValue);
    }


    public static Condition startsWith(String fieldName, String prefix) {
        return new StringStartsWithCondition(fieldName, prefix);
    }


    public static Condition endsWith(String fieldName, String suffix) {
        return new StringEndsWithCondition(fieldName, suffix);
    }


    public static Condition contains(String fieldName, String substring) {
        return new StringContainsCondition(fieldName, substring);
    }


    public static LabelCondition labelExists(String labelKey) {
        return new LabelExistsCondition(labelKey);
    }


    public static LabelCondition labelEqual(String labelKey, String labelValue) {
        return new LabelEqualsCondition(labelKey, labelValue);
    }


    public static LabelCondition labelIn(String labelKey, Collection<String> labelValues) {
        return new LabelInCondition(labelKey, labelValues);
    }
}
