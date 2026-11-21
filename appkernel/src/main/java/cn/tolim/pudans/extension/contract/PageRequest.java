package cn.tolim.pudans.extension.contract;

import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;


public interface PageRequest {
    // 返回当前页码，从 1 开始。
    int getPageNumber();

    // 返回每页允许的最大元素数量。
    int getPageSize();

    // 返回上一页请求。
    PageRequest previous();

    // 返回下一页请求。
    PageRequest next();


    // 有上一页则回退，否则返回第一页。
    PageRequest previousOrFirst();


    // 返回第一页请求。
    PageRequest first();


    // 复制请求并替换页码。
    PageRequest withPage(int pageNumber);

    // 复制请求并替换排序条件。
    PageRequestImpl withSort(Sort sort);

    // 判断是否还有上一页。
    boolean hasPrevious();

    // 返回当前排序条件。
    Sort getSort();


    // 当前未指定排序时使用给定兜底排序。
    default Sort getSortOr(Sort sort) {
        Assert.notNull(sort, "备用排序条件不能为 null");
        return getSort().isSorted() ? getSort() : sort;
    }
}
