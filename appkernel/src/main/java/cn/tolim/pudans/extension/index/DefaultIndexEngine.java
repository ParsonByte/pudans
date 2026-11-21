package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.PageRequest;
import cn.tolim.pudans.extension.index.query.QueryVisitor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;


@Component
class DefaultIndexEngine implements IndexEngine, DisposableBean {

    private final ConversionService conversionService;
    private IndicesManager indicesManager;

    // 注入类型转换服务并初始化索引管理器。
    public DefaultIndexEngine(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.indicesManager = new DefaultIndicesManager();
    }

    // 按排序字段组合比较器，并以主键兜底。
    private static Comparator<Object[]> buildComparator(List<SortOrder> sortOrders) {
        Comparator<Object[]> comparator = null;
        for (var i = 0; i < sortOrders.size(); i++) {
            var index = i;
            var sortOrder = sortOrders.get(i);
            Comparator<Object[]> orderComparator = (left, right) -> {

                var leftKey = left[index + 1];
                var rightKey = right[index + 1];
                return sortOrder.descending() ? compareSortKeys(rightKey, leftKey) : compareSortKeys(leftKey, rightKey);
            };
            comparator = comparator == null ? orderComparator : comparator.thenComparing(orderComparator);
        }
        if (comparator == null) {
            return Comparator.comparing(DefaultIndexEngine::primaryKey);
        }

        return comparator.thenComparing(Comparator.comparing(DefaultIndexEngine::primaryKey));
    }

    // 空值排在前面，其余按自然顺序比较。
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareSortKeys(Object left, Object right) {

        if (left == null) {
            return right == null ? 0 : -1;
        }
        if (right == null) {
            return 1;
        }
        return ((Comparable) left).compareTo(right);
    }

    // 为每个主键预先计算排序键值。
    private static List<Object[]> createSortEntries(Collection<String> primaryKeys, List<SortOrder> sortOrders) {
        var entries = new ArrayList<Object[]>(primaryKeys.size());
        for (var primaryKey : primaryKeys) {
            var sortEntry = newSortEntry(sortOrders);
            fillSortEntry(sortEntry, primaryKey, sortOrders);
            entries.add(sortEntry);
        }
        return entries;
    }

    // 创建主键加排序键的定长数组。
    private static Object[] newSortEntry(List<SortOrder> sortOrders) {
        return new Object[sortOrders.size() + 1];
    }

    // 填充主键并计算每个排序字段的值。
    private static void fillSortEntry(Object[] sortEntry, String primaryKey, List<SortOrder> sortOrders) {
        sortEntry[0] = primaryKey;
        for (var i = 0; i < sortOrders.size(); i++) {
            sortEntry[i + 1] = sortOrders.get(i).accessor().apply(primaryKey);
        }
    }

    // 取出排序条目中的主键。
    private static String primaryKey(Object[] sortEntry) {
        return (String) sortEntry[0];
    }

    // 无排序时按自然顺序，否则按排序键排序。
    private static List<String> sortedPrimaryKeys(Collection<String> primaryKeys, List<SortOrder> sortOrders) {
        if (sortOrders.isEmpty()) {
            var sortedResult = new ArrayList<>(primaryKeys);
            sortedResult.sort(Comparator.naturalOrder());
            return sortedResult;
        }
        var entries = createSortEntries(primaryKeys, sortOrders);
        entries.sort(buildComparator(sortOrders));
        var sortedResult = new ArrayList<String>(entries.size());
        for (var entry : entries) {
            sortedResult.add(primaryKey(entry));
        }
        return sortedResult;
    }

    // 用小顶堆取出排序后的前 N 项。
    private static List<String> topN(Collection<String> result, List<SortOrder> sortOrders, int topN) {
        var pq = topNHeap(result, sortOrders, topN);
        var finalResult = new LinkedList<String>();
        while (!pq.isEmpty()) {
            finalResult.addFirst(primaryKey(pq.poll()));
        }
        return finalResult;
    }

    // 维护大小为 N 的小顶堆过滤候选。
    private static PriorityQueue<Object[]> topNHeap(Collection<String> result, List<SortOrder> sortOrders, int topN) {
        var comparator = buildComparator(sortOrders);
        var pq = new PriorityQueue<Object[]>(topN + 1, comparator.reversed());

        var candidate = newSortEntry(sortOrders);
        for (var primaryKey : result) {
            fillSortEntry(candidate, primaryKey, sortOrders);
            if (pq.size() < topN) {
                pq.offer(candidate);
                candidate = newSortEntry(sortOrders);
            } else if (comparator.compare(candidate, pq.peek()) < 0) {
                pq.poll();
                pq.offer(candidate);
                candidate = newSortEntry(sortOrders);
            }
        }
        return pq;
    }

    // 关闭索引管理器，释放索引资源。
    @Override
    public void destroy() throws Exception {
        this.indicesManager.close();
    }

    // 按扩展类型写入对应索引。
    @Override
    public <E extends Extension> void insert(Iterable<E> extensions) {
        extensions.forEach(extension -> {

            var indices = indicesManager.get((Class<E>) extension.getClass());
            indices.insert(extension);
        });
    }

    // 按扩展类型更新对应索引。
    @Override
    public <E extends Extension> void update(Iterable<E> extensions) {
        extensions.forEach(extension -> {
            var indices = indicesManager.get((Class<E>) extension.getClass());
            indices.update(extension);
        });
    }

    // 按扩展类型删除对应索引。
    @Override
    public <E extends Extension> void delete(Iterable<E> extensions) {
        extensions.forEach(extension -> {
            var indices = indicesManager.get((Class<E>) extension.getClass());
            indices.delete(extension);
        });
    }

    // 解析条件后分页返回匹配的主键。
    @Override
    public <E extends Extension> ListResult<String> retrieve(Class<E> type, ListOptions options, PageRequest page) {
        if (options == null) {
            options = ListOptions.builder().build();
        }
        var finalCondition = options.toCondition();
        var indices = indicesManager.get(type);
        var queryVisitor = new QueryVisitor<>(indices, conversionService);
        queryVisitor.enter(finalCondition);
        var result = queryVisitor.getResult();
        var sort = page.getSort();
        var sortOrders = sort.isUnsorted() ? List.<SortOrder>of() : buildSortOrders(sort, indices);
        return paginate(result, page, sortOrders);
    }

    // 解析条件后返回全部匹配的主键。
    @Override
    public <E extends Extension> Iterable<String> retrieveAll(Class<E> type, ListOptions options, Sort sort) {
        if (options == null) {
            options = ListOptions.builder().build();
        }
        if (sort == null) {
            sort = Sort.unsorted();
        }
        var finalCondition = options.toCondition();
        var indices = indicesManager.get(type);
        var queryVisitor = new QueryVisitor<>(indices, conversionService);
        queryVisitor.enter(finalCondition);
        var result = queryVisitor.getResult();
        if (sort.isUnsorted()) {

            return result.stream()::iterator;
        }
        var sortOrders = buildSortOrders(sort, indices);
        return sortedPrimaryKeys(result, sortOrders)::iterator;
    }

    // 解析条件后返回排序前 N 个主键。
    @Override
    public <E extends Extension> Iterable<String> retrieveTopN(
            Class<E> type, ListOptions options, Sort sort, int topN) {
        Assert.isTrue(topN > 0, "topN 必须大于 0");
        if (options == null) {
            options = ListOptions.builder().build();
        }
        if (sort == null) {
            sort = Sort.unsorted();
        }
        var finalCondition = options.toCondition();
        var indices = indicesManager.get(type);
        var queryVisitor = new QueryVisitor<>(indices, conversionService);
        queryVisitor.enter(finalCondition);
        var result = queryVisitor.getResult();
        var sortOrders = sort.isUnsorted() ? List.<SortOrder>of() : buildSortOrders(sort, indices);
        return topN(result, sortOrders, topN);
    }

    // 解析条件后统计匹配的主键数量。
    @Override
    public <E extends Extension> long count(Class<E> type, ListOptions options) {
        if (options == null) {
            options = ListOptions.builder().build();
        }
        var finalCondition = options.toCondition();
        var indices = indicesManager.get(type);
        var queryVisitor = new QueryVisitor<>(indices, conversionService);
        queryVisitor.enter(finalCondition);
        return queryVisitor.getResult().size();
    }

    // 返回索引管理器。
    @Override
    public IndicesManager getIndicesManager() {
        return this.indicesManager;
    }

    // 把排序字段解析为索引访问器。
    private <E extends Extension> List<SortOrder> buildSortOrders(Sort sort, Indices<E> indices) {
        var sortOrders = new ArrayList<SortOrder>(sort.toList().size());
        for (var order : sort) {
            Index<E, ?> index = indices.getIndex(order.getProperty());
            Function<String, Object> accessor;
            if (index instanceof MultiValueIndex<?, ?> multiValueIndex) {
                accessor = primaryKey -> {
                    var keys = multiValueIndex.getKeys(primaryKey);
                    return CollectionUtils.isEmpty(keys)
                            ? null
                            : keys.iterator().next();
                };
            } else if (index instanceof SingleValueIndex<?, ?> singleValueIndex) {
                accessor = singleValueIndex::getKey;
            } else {
                throw new UnsupportedOperationException("不支持用于排序的索引类型：" + index.getClass());
            }
            sortOrders.add(new SortOrder(accessor, order.isDescending()));
        }
        return sortOrders;
    }

    // 按页大小截取排序结果，必要时走堆。
    private ListResult<String> paginate(Collection<String> result, PageRequest page, List<SortOrder> sortOrders) {
        var total = result.size();
        int offset = (page.getPageNumber() - 1) * page.getPageSize();
        int limit = page.getPageSize();
        if (limit <= 0) {

            var finalResult = sortedPrimaryKeys(result, sortOrders);
            return new ListResult<>(page.getPageNumber(), page.getPageSize(), total, finalResult);
        }
        if (offset >= total) {
            return new ListResult<>(page.getPageNumber(), page.getPageSize(), total, new LinkedList<>());
        }
        if (offset + limit > total) {
            limit = total - offset;
        }
        var n = offset + limit;
        if (n > 1000) {
            var sortedResult = sortedPrimaryKeys(result, sortOrders);
            var finalResult = new ArrayList<String>(limit);
            for (var i = offset; i < offset + limit; i++) {
                finalResult.add(sortedResult.get(i));
            }
            return new ListResult<>(page.getPageNumber(), page.getPageSize(), total, finalResult);
        }
        var pq = topNHeap(result, sortOrders, n);
        var finalResult = new LinkedList<String>();
        while (!pq.isEmpty()) {
            finalResult.addFirst(primaryKey(pq.poll()));
            if (finalResult.size() >= limit) {

                break;
            }
        }
        pq.clear();
        return new ListResult<>(page.getPageNumber(), page.getPageSize(), total, finalResult);
    }

    private record SortOrder(Function<String, Object> accessor, boolean descending) {}
}
