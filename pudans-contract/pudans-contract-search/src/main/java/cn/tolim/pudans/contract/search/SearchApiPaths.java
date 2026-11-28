package cn.tolim.pudans.contract.search;

// Search service API paths
public final class SearchApiPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/search";

    public static final String INDEXES_SUFFIX = "/indexes";
    public static final String INDEXES = BASE + INDEXES_SUFFIX;

    public static final String INDEX_DETAIL_SUFFIX = "/indexes/{indexCode}";
    public static final String INDEX_DETAIL = BASE + INDEX_DETAIL_SUFFIX;

    public static final String INDEX_DOCUMENTS_SUFFIX = "/indexes/{indexCode}/documents";
    public static final String INDEX_DOCUMENTS = BASE + INDEX_DOCUMENTS_SUFFIX;

    public static final String DOCUMENTS_BATCH_SUFFIX = "/indexes/{indexCode}/documents/batch";
    public static final String DOCUMENTS_BATCH = BASE + DOCUMENTS_BATCH_SUFFIX;

    public static final String DOCUMENT_DETAIL_SUFFIX = "/indexes/{indexCode}/documents/{documentId}";
    public static final String DOCUMENT_DETAIL = BASE + DOCUMENT_DETAIL_SUFFIX;

    public static final String QUERY_SUFFIX = "/query";
    public static final String QUERY = BASE + QUERY_SUFFIX;

    private SearchApiPaths() {
    }
}