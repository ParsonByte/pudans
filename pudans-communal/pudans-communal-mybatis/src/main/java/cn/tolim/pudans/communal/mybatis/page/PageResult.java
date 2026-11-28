package cn.tolim.pudans.communal.mybatis.page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// Immutable page result; items is never null.
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than or equal to 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be greater than or equal to 0");
        }
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(List.of(), query.page(), query.size(), 0);
    }

    public static <T> PageResult<T> of(List<T> items, PageQuery query, long total) {
        return new PageResult<>(items, query.page(), query.size(), total);
    }

    public int totalPages() {
        if (total == 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }

    public boolean hasNext() {
        return (long) page * size < total;
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mappedItems = new ArrayList<>(items.size());
        for (T item : items) {
            mappedItems.add(mapper.apply(item));
        }
        return new PageResult<>(mappedItems, page, size, total);
    }
}