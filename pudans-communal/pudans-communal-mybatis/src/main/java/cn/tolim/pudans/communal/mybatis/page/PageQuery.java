package cn.tolim.pudans.communal.mybatis.page;

// Page request with 1-based page numbers and a bounded page size.
public record PageQuery(int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 500;

    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageQuery of(Integer page, Integer size) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        return new PageQuery(resolvedPage, resolvedSize);
    }

    public static PageQuery firstPage(int size) {
        return new PageQuery(DEFAULT_PAGE, size);
    }

    public long offset() {
        return (long) (page - 1) * size;
    }

    public int limit() {
        return size;
    }
}