package cn.tolim.pudans.contract.extension;

import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;


public interface PageRequest {
    int getPageNumber();

    int getPageSize();

    PageRequest previous();

    PageRequest next();


    PageRequest previousOrFirst();


    PageRequest first();


    PageRequest withPage(int pageNumber);

    PageRequestImpl withSort(Sort sort);

    boolean hasPrevious();

    Sort getSort();


    default Sort getSortOr(Sort sort) {
        Assert.notNull(sort, "Fallback Sort must not be null");
        return getSort().isSorted() ? getSort() : sort;
    }
}
