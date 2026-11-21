package cn.tolim.pudans.extension.index.query;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.index.query.AllCondition;
import cn.tolim.pudans.extension.contract.index.query.And;
import cn.tolim.pudans.extension.contract.index.query.AndCondition;
import cn.tolim.pudans.extension.contract.index.query.BetweenCondition;
import cn.tolim.pudans.extension.contract.index.query.Condition;
import cn.tolim.pudans.extension.contract.index.query.EmptyCondition;
import cn.tolim.pudans.extension.contract.index.query.EqualCondition;
import cn.tolim.pudans.extension.contract.index.query.GreaterThanCondition;
import cn.tolim.pudans.extension.contract.index.query.InCondition;
import cn.tolim.pudans.extension.contract.index.query.IsNotNullCondition;
import cn.tolim.pudans.extension.contract.index.query.IsNullCondition;
import cn.tolim.pudans.extension.contract.index.query.LabelEqualsCondition;
import cn.tolim.pudans.extension.contract.index.query.LabelExistsCondition;
import cn.tolim.pudans.extension.contract.index.query.LabelInCondition;
import cn.tolim.pudans.extension.contract.index.query.LabelNotEqualsCondition;
import cn.tolim.pudans.extension.contract.index.query.LabelNotExistsCondition;
import cn.tolim.pudans.extension.contract.index.query.LabelNotInCondition;
import cn.tolim.pudans.extension.contract.index.query.LessThanCondition;
import cn.tolim.pudans.extension.contract.index.query.NoneCondition;
import cn.tolim.pudans.extension.contract.index.query.NotBetweenCondition;
import cn.tolim.pudans.extension.contract.index.query.NotCondition;
import cn.tolim.pudans.extension.contract.index.query.NotEqualCondition;
import cn.tolim.pudans.extension.contract.index.query.NotInCondition;
import cn.tolim.pudans.extension.contract.index.query.OrCondition;
import cn.tolim.pudans.extension.contract.index.query.StringContainsCondition;
import cn.tolim.pudans.extension.contract.index.query.StringEndsWithCondition;
import cn.tolim.pudans.extension.contract.index.query.StringNotContainsCondition;
import cn.tolim.pudans.extension.contract.index.query.StringNotEndsWithCondition;
import cn.tolim.pudans.extension.contract.index.query.StringNotStartsWithCondition;
import cn.tolim.pudans.extension.contract.index.query.StringStartsWithCondition;
import cn.tolim.pudans.extension.index.Indices;
import cn.tolim.pudans.extension.index.LabelIndexQuery;
import cn.tolim.pudans.extension.index.ValueIndexQuery;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.relational.core.sql.Visitable;
import org.springframework.data.relational.core.sql.Visitor;


public class QueryVisitor<E extends Extension> implements Visitor {

    private final ConversionService conversionService;

    private final Indices<E> indices;

    private Set<String> result;

    // 保存索引与类型转换服务。
    public QueryVisitor(Indices<E> indices, ConversionService conversionService) {
        this.indices = indices;
        this.result = new HashSet<>();
        this.conversionService = conversionService;
    }

    // 访问条件并把结果汇总到当前实例。
    @Override
    public void enter(Visitable segment) {
        var visitor = new ConditionVisitor();
        segment.visit(visitor);
        this.result = visitor.getResult();
    }

    // 返回最近一次查询的主键集合。
    public Set<String> getResult() {
        return result;
    }

    class ConditionVisitor implements Visitor {

        @Getter
        private Set<String> result;

        // 以空结果初始化条件访问器。
        ConditionVisitor() {
            result = new HashSet<>();
        }

        // 替换条件访问器的结果集。
        private void setResult(Set<String> result) {
            this.result = result;
        }

        // 按条件类型分派到对应索引查询。
        @Override
        public void enter(Visitable segment) {
            switch (segment) {
                case And(var left, var right) -> new AndCondition(left, right).visit(this);
                case EmptyCondition ignored -> setResult(allQuery("metadata.name", false));
                case AndCondition(var left, var right) -> visitAnd(left, right);
                case OrCondition(var left, var right) -> visitOr(left, right);
                case NotCondition(var condition) -> {
                    if (condition instanceof EmptyCondition) {
                        return;
                    }
                    condition.not().visit(this);
                }
                case EqualCondition(var indexName, var key) -> setResult(equalQuery(indexName, key, false));
                case NotEqualCondition(var indexName, var key) -> setResult(equalQuery(indexName, key, true));
                case InCondition(var indexName, var keys) -> setResult(inQuery(indexName, keys, false));
                case NotInCondition(var indexName, var keys) -> setResult(inQuery(indexName, keys, true));
                case LessThanCondition(var indexName, var upperBound, var inclusive) ->
                        setResult(lessThanQuery(indexName, upperBound, inclusive, false));
                case GreaterThanCondition(var indexName, var lowerBound, var inclusive) ->
                        setResult(lessThanQuery(indexName, lowerBound, inclusive, true));
                case BetweenCondition bc -> setResult(betweenQuery(
                        bc.indexName(), bc.fromKey(), bc.fromInclusive(), bc.toKey(), bc.toInclusive(), false));
                case NotBetweenCondition nbc -> setResult(betweenQuery(
                        nbc.indexName(), nbc.fromKey(), nbc.fromInclusive(), nbc.toKey(), nbc.toInclusive(), true));
                case IsNullCondition(var indexName) -> setResult(isNullQuery(indexName, false));
                case IsNotNullCondition(var indexName) -> setResult(isNullQuery(indexName, true));
                case StringContainsCondition(var indexName, var keyword) ->
                        setResult(stringContainsQuery(indexName, keyword, false));
                case StringNotContainsCondition(var indexName, var keyword) ->
                        setResult(stringContainsQuery(indexName, keyword, true));
                case StringStartsWithCondition(var indexName, var prefix) ->
                        setResult(stringStartsWithQuery(indexName, prefix, false));
                case StringNotStartsWithCondition(var indexName, var prefix) ->
                        setResult(stringStartsWithQuery(indexName, prefix, true));
                case StringEndsWithCondition(var indexName, var suffix) ->
                        setResult(stringEndsWithQuery(indexName, suffix, false));
                case StringNotEndsWithCondition(var indexName, var suffix) ->
                        setResult(stringEndsWithQuery(indexName, suffix, true));
                case AllCondition(var indexName) -> setResult(allQuery(indexName, false));
                case NoneCondition(var indexName) -> setResult(allQuery(indexName, true));
                case LabelExistsCondition(var labelKey) -> setResult(labelExistsQuery(labelKey));
                case LabelNotExistsCondition(var labelKey) -> {

                    var allResult = allQuery("metadata.name", false);
                    allResult.removeAll(labelExistsQuery(labelKey));
                    setResult(allResult);
                }
                case LabelEqualsCondition(var labelKey, var labelValue) ->
                        setResult(labelEqualsQuery(labelKey, labelValue, false));
                case LabelNotEqualsCondition(var labelKey, var labelValue) -> {

                    var allResult = allQuery("metadata.name", false);
                    allResult.removeAll(labelEqualsQuery(labelKey, labelValue, false));
                    setResult(allResult);
                }
                case LabelInCondition(var labelKey, var labelValues) ->
                        setResult(labelInQuery(labelKey, labelValues, false));
                case LabelNotInCondition(var labelKey, var labelValues) ->
                        setResult(labelInQuery(labelKey, labelValues, true));
                default -> {}
            }
        }

        // 取两侧结果的交集，并优先保留较小集合。
        private void visitAnd(Condition left, Condition right) {
            if (left instanceof EmptyCondition) {
                right.visit(this);
                return;
            }
            if (right instanceof EmptyCondition) {
                left.visit(this);
                return;
            }
            left.visit(this);

            if (result.isEmpty()) {
                return;
            }
            var rightVisitor = new ConditionVisitor();
            right.visit(rightVisitor);
            var rightResult = rightVisitor.getResult();
            if (rightResult.isEmpty()) {
                setResult(rightResult);
                return;
            }

            if (result.size() <= rightResult.size()) {
                result.retainAll(rightResult);
            } else {
                rightResult.retainAll(result);
                setResult(rightResult);
            }
        }

        // 取两侧结果的并集，并优先保留较大集合。
        private void visitOr(Condition left, Condition right) {
            if (left instanceof EmptyCondition) {
                left.visit(this);
                return;
            }
            if (right instanceof EmptyCondition) {
                right.visit(this);
                return;
            }
            left.visit(this);
            var rightVisitor = new ConditionVisitor();
            right.visit(rightVisitor);
            var rightResult = rightVisitor.getResult();
            if (result.isEmpty()) {
                setResult(rightResult);
                return;
            }
            if (rightResult.isEmpty()) {
                return;
            }

            if (result.size() >= rightResult.size()) {
                result.addAll(rightResult);
            } else {
                rightResult.addAll(result);
                setResult(rightResult);
            }
        }

        // 按是否取反调用标签集合查询。
        private Set<String> labelInQuery(String labelKey, Collection<String> labelValues, boolean negated) {
            var index = this.getLabelIndexQuery();
            if (negated) {
                return index.notIn(labelKey, labelValues);
            }
            return index.in(labelKey, labelValues);
        }

        // 按是否取反调用标签相等查询。
        private Set<String> labelEqualsQuery(String labelKey, String labelValue, boolean negated) {
            var index = this.getLabelIndexQuery();
            if (negated) {
                return index.notEqual(labelKey, labelValue);
            }
            return index.equal(labelKey, labelValue);
        }

        // 查询包含指定标签键的主键。
        private Set<String> labelExistsQuery(String labelKey) {
            var index = getLabelIndexQuery();
            return index.exists(labelKey);
        }

        // 按是否取反调用后缀查询。
        private Set<String> stringEndsWithQuery(String indexName, String suffix, boolean negated) {
            var index = getValueIndexQuery(indexName);
            if (negated) {
                return index.stringNotEndsWith(suffix);
            }
            return index.stringEndsWith(suffix);
        }

        // 取反时返回空集，否则返回全部主键。
        private Set<String> allQuery(String indexName, boolean negated) {
            var index = getValueIndexQuery(indexName);
            if (negated) {
                return new HashSet<>();
            }
            return index.all();
        }

        // 按是否取反调用前缀查询。
        private Set<String> stringStartsWithQuery(String indexName, String prefix, boolean negated) {
            var index = getValueIndexQuery(indexName);
            if (negated) {
                return index.stringNotStartsWith(prefix);
            }
            return index.stringStartsWith(prefix);
        }

        // 按是否取反调用包含查询。
        private Set<String> stringContainsQuery(String indexName, String keyword, boolean negated) {
            var index = getValueIndexQuery(indexName);
            if (negated) {
                return index.stringNotContains(keyword);
            }
            return index.stringContains(keyword);
        }

        // 按是否取反调用空值查询。
        private <K extends Comparable<K>> Set<String> isNullQuery(String indexName, boolean negated) {
            var index = this.<K>getValueIndexQuery(indexName);
            if (negated) {
                return index.isNotNull();
            }
            return index.isNull();
        }

        // 转换边界类型后执行区间查询。
        private <K extends Comparable<K>> Set<String> betweenQuery(
                String indexName,
                Object fromKey,
                boolean fromInclusive,
                Object toKey,
                boolean toInclusive,
                boolean negated) {
            var index = this.<K>getValueIndexQuery(indexName);
            if (!conversionService.canConvert(fromKey.getClass(), index.getKeyType())) {
                throw new IllegalArgumentException(
                        "无法转换键：" + fromKey + "，目标类型：" + index.getKeyType());
            }
            if (!conversionService.canConvert(toKey.getClass(), index.getKeyType())) {
                throw new IllegalArgumentException("无法转换键：" + toKey + "，目标类型：" + index.getKeyType());
            }
            if (negated) {
                return index.notBetween(
                        conversionService.convert(fromKey, index.getKeyType()),
                        fromInclusive,
                        conversionService.convert(toKey, index.getKeyType()),
                        toInclusive);
            } else {
                return index.between(
                        conversionService.convert(fromKey, index.getKeyType()),
                        fromInclusive,
                        conversionService.convert(toKey, index.getKeyType()),
                        toInclusive);
            }
        }

        // 转换边界类型后执行范围查询。
        private <K extends Comparable<K>> Set<String> lessThanQuery(
                String indexName, Object bound, boolean inclusive, boolean negated) {
            var index = this.<K>getValueIndexQuery(indexName);
            if (!conversionService.canConvert(bound.getClass(), index.getKeyType())) {
                throw new IllegalArgumentException("无法转换键：" + bound + "，目标类型：" + index.getKeyType());
            }
            if (negated) {
                return index.greaterThan(conversionService.convert(bound, index.getKeyType()), inclusive);
            } else {
                return index.lessThan(conversionService.convert(bound, index.getKeyType()), inclusive);
            }
        }

        // 转换键类型后执行相等查询。
        private <K extends Comparable<K>> Set<String> equalQuery(String indexName, Object key, boolean negated) {
            var index = this.<K>getValueIndexQuery(indexName);
            if (!conversionService.canConvert(key.getClass(), index.getKeyType())) {
                throw new IllegalArgumentException("无法转换键：" + key + "，目标类型：" + index.getKeyType());
            }
            if (negated) {
                return index.notEqual(conversionService.convert(key, index.getKeyType()));
            } else {
                return index.equal(conversionService.convert(key, index.getKeyType()));
            }
        }

        // 转换全部键类型后执行集合查询。
        private <K extends Comparable<K>> Set<String> inQuery(
                String indexName, Collection<Object> keys, boolean negated) {
            var index = this.<K>getValueIndexQuery(indexName);
            var convertedKeys = keys.stream()
                    .map(key -> {
                        if (!conversionService.canConvert(key.getClass(), index.getKeyType())) {
                            throw new IllegalArgumentException(
                                    "无法转换键：" + key + "，目标类型：" + index.getKeyType());
                        }
                        return conversionService.convert(key, index.getKeyType());
                    })
                    .toList();
            if (negated) {
                return index.notIn(convertedKeys);
            } else {
                return index.in(convertedKeys);
            }
        }

        // 获取值索引并校验查询能力。
        private <K extends Comparable<K>> ValueIndexQuery<K> getValueIndexQuery(String indexName) {
            var index = indices.<K>getIndex(indexName);
            if (!(index instanceof ValueIndexQuery<?> valueIndexQuery)) {
                throw new IllegalArgumentException("索引不是内存索引：" + indexName);
            }
            return (ValueIndexQuery<K>) valueIndexQuery;
        }

        // 获取标签索引并校验查询能力。
        private LabelIndexQuery getLabelIndexQuery() {
            var indexName = "metadata.labels";
            var index = indices.<String>getIndex(indexName);
            if (!(index instanceof LabelIndexQuery labelIndexQuery)) {
                throw new IllegalArgumentException("索引不是标签索引：" + indexName);
            }
            return labelIndexQuery;
        }
    }
}
