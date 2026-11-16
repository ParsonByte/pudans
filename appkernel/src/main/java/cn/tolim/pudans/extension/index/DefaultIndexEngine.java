package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.PageRequest;
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

    public DefaultIndexEngine(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.indicesManager = new DefaultIndicesManager();
    }

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

    private static List<Object[]> createSortEntries(Collection<String> primaryKeys, List<SortOrder> sortOrders) {
        var entries = new ArrayList<Object[]>(primaryKeys.size());
        for (var primaryKey : primaryKeys) {
            var sortEntry = newSortEntry(sortOrders);
            fillSortEntry(sortEntry, primaryKey, sortOrders);
            entries.add(sortEntry);
        }
        return entries;
    }

    private static Object[] newSortEntry(List<SortOrder> sortOrders) {
        return new Object[sortOrders.size() + 1];
    }

    private static void fillSortEntry(Object[] sortEntry, String primaryKey, List<SortOrder> sortOrders) {
        sortEntry[0] = primaryKey;
        for (var i = 0; i < sortOrders.size(); i++) {
            sortEntry[i + 1] = sortOrders.get(i).accessor().apply(primaryKey);
        }
    }

    private static String primaryKey(Object[] sortEntry) {
        return (String) sortEntry[0];
    }

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

    private static List<String> topN(Collection<String> result, List<SortOrder> sortOrders, int topN) {
        var pq = topNHeap(result, sortOrders, topN);
        var finalResult = new LinkedList<String>();
        while (!pq.isEmpty()) {
            finalResult.addFirst(primaryKey(pq.poll()));
        }
        return finalResult;
    }

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

    @Override
    public void destroy() throws Exception {
        this.indicesManager.close();
    }

    @Override
    public <E extends Extension> void insert(Iterable<E> extensions) {
        extensions.forEach(extension -> {

            var indices = indicesManager.get((Class<E>) extension.getClass());
            indices.insert(extension);
        });
    }

    @Override
    public <E extends Extension> void update(Iterable<E> extensions) {
        extensions.forEach(extension -> {
            var indices = indicesManager.get((Class<E>) extension.getClass());
            indices.update(extension);
        });
    }

    @Override
    public <E extends Extension> void delete(Iterable<E> extensions) {
        extensions.forEach(extension -> {
            var indices = indicesManager.get((Class<E>) extension.getClass());
            indices.delete(extension);
        });
    }

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

    @Override
    public IndicesManager getIndicesManager() {
        return this.indicesManager;
    }

    void setIndicesManager(IndicesManager indicesManager) {
        Assert.notNull(indicesManager, "indicesManager 不能为 null");
        this.indicesManager = indicesManager;
    }

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
