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
        Assert.notNull(sort, "备用排序条件不能为 null");
        return getSort().isSorted() ? getSort() : sort;
    }
}
