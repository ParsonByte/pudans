package cn.tolim.pudans.extension.contract;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.util.Assert;

@Slf4j
public class PageRequestImpl implements PageRequest {

    public static final int MAX_SIZE = 1_000;

    private final int pageNumber;
    private final int pageSize;
    private final Sort sort;

    // 校验并夹紧分页参数，防止越界查询。
    public PageRequestImpl(int pageNumber, int pageSize, Sort sort) {
        Assert.notNull(sort, "排序条件不能为 null");
        if (pageNumber < 1) {
            pageNumber = 1;
        }
        if (pageSize <= 0) {
            log.warn("分页大小必须大于 0，已重置为默认值 {}", MAX_SIZE);
            pageSize = MAX_SIZE;
        }
        if (pageSize > 1000) {
            log.warn("分页大小不能大于 {}，已重置为 {}", MAX_SIZE, MAX_SIZE);
            pageSize = MAX_SIZE;
        }
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.sort = sort;
    }

    // 以默认无序排序创建分页请求。
    public static PageRequestImpl of(int pageNumber, int pageSize) {
        return of(pageNumber, pageSize, Sort.unsorted());
    }

    // 使用给定排序创建分页请求。
    public static PageRequestImpl of(int pageNumber, int pageSize, Sort sort) {
        return new PageRequestImpl(pageNumber, pageSize, sort);
    }

    // 从第一页开始，按页大小创建请求。
    public static PageRequestImpl ofSize(int pageSize) {
        return PageRequestImpl.of(1, pageSize);
    }

    // 返回规范化后的页码。
    @Override
    public int getPageNumber() {
        return pageNumber;
    }

    // 返回规范化后的页大小。
    @Override
    public int getPageSize() {
        return pageSize;
    }

    // 返回上一页，页码下溢时由构造器归一化。
    @Override
    public PageRequest previous() {
        return getPageNumber() == 0 ? this : new PageRequestImpl(getPageNumber() - 1, getPageSize(), getSort());
    }

    // 返回创建时固化的排序条件。
    @Override
    public Sort getSort() {
        return sort;
    }

    // 返回下一页请求。
    @Override
    public PageRequest next() {
        return new PageRequestImpl(getPageNumber() + 1, getPageSize(), getSort());
    }

    // 有上一页则回退，否则返回第一页。
    @Override
    public PageRequest previousOrFirst() {
        return hasPrevious() ? previous() : first();
    }

    // 返回第一页请求，保留页大小与排序。
    @Override
    public PageRequest first() {
        return new PageRequestImpl(1, getPageSize(), getSort());
    }

    // 复制请求并替换页码。
    @Override
    public PageRequest withPage(int pageNumber) {
        return new PageRequestImpl(pageNumber, getPageSize(), getSort());
    }

    // 复制请求并替换排序，null 视为无序。
    @Override
    public PageRequestImpl withSort(Sort sort) {
        return new PageRequestImpl(getPageNumber(), getPageSize(), defaultIfNull(sort, Sort.unsorted()));
    }

    // 页码大于 1 时存在上一页。
    @Override
    public boolean hasPrevious() {
        return pageNumber > 1;
    }
}
