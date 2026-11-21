package cn.tolim.pudans.extension.contract.index.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.springframework.util.Assert;


public enum Queries {
    ;


    // 把首个条件与其余条件按与组合。
    public static Condition and(Condition condition, Condition... additionalConditions) {
        Assert.notNull(condition, "条件不能为 null");
        return Arrays.stream(additionalConditions).reduce(condition, Condition::and);
    }


    // 把首个条件与其余条件按或组合。
    public static Condition or(Condition condition, Condition... additionalConditions) {
        Assert.notNull(condition, "条件不能为 null");
        return Arrays.stream(additionalConditions).reduce(condition, Condition::or);
    }


    // 返回条件的否定。
    public static Condition not(Condition condition) {
        Assert.notNull(condition, "条件不能为 null");
        return condition.not();
    }


    // 构造闭区间或开区间条件。
    public static Condition between(
            String fieldName, Object fromValue, boolean fromInclusive, Object toValue, boolean toInclusive) {
        return new BetweenCondition(fieldName, fromValue, fromInclusive, toValue, toInclusive);
    }


    // 构造不限制任何键的空条件。
    public static Condition empty() {
        return new EmptyCondition();
    }


    // 构造匹配索引全部键的条件。
    public static Condition all(String fieldName) {
        return new AllCondition(fieldName);
    }


    // 构造字段为空的条件。
    public static Condition isNull(String fieldName) {
        return new IsNullCondition(fieldName);
    }


    // 构造字段相等条件。
    public static Condition equal(String fieldName, Object attributeValue) {
        Assert.notNull(attributeValue, "字段 " + fieldName + " 的属性键不能为 null");
        return new EqualCondition(fieldName, attributeValue);
    }


    // 构造大于或大于等于条件。
    public static Condition greaterThan(String fieldName, Object attributeValue, boolean inclusive) {
        return new GreaterThanCondition(fieldName, attributeValue, inclusive);
    }


    // 构造严格大于条件。
    public static Condition greaterThan(String fieldName, Object attributeValue) {
        return greaterThan(fieldName, attributeValue, false);
    }


    // 把单值或集合统一为包含条件。
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


    // 单元素集合退化为相等条件。
    public static Condition in(String fieldName, Collection<Object> values) {
        Assert.notNull(values, "值不能为 null");
        if (values.size() == 1) {
            var value = values.iterator().next();
            return equal(fieldName, value);
        }
        return new InCondition(fieldName, values);
    }


    // 构造小于或小于等于条件。
    public static Condition lessThan(String fieldName, Object attributeValue, boolean inclusive) {
        return new LessThanCondition(fieldName, attributeValue, inclusive);
    }


    // 构造严格小于条件。
    public static Condition lessThan(String fieldName, Object attributeValue) {
        return lessThan(fieldName, attributeValue, false);
    }


    // 构造字段不等条件。
    public static Condition notEqual(String fieldName, Object attributeValue) {
        return new NotEqualCondition(fieldName, attributeValue);
    }


    // 构造字符串前缀条件。
    public static Condition startsWith(String fieldName, String prefix) {
        return new StringStartsWithCondition(fieldName, prefix);
    }


    // 构造字符串后缀条件。
    public static Condition endsWith(String fieldName, String suffix) {
        return new StringEndsWithCondition(fieldName, suffix);
    }


    // 构造字符串包含条件。
    public static Condition contains(String fieldName, String substring) {
        return new StringContainsCondition(fieldName, substring);
    }


    // 构造标签存在条件。
    public static LabelCondition labelExists(String labelKey) {
        return new LabelExistsCondition(labelKey);
    }


    // 构造标签相等条件。
    public static LabelCondition labelEqual(String labelKey, String labelValue) {
        return new LabelEqualsCondition(labelKey, labelValue);
    }


    // 构造标签值集合条件。
    public static LabelCondition labelIn(String labelKey, Collection<String> labelValues) {
        return new LabelInCondition(labelKey, labelValues);
    }
}
