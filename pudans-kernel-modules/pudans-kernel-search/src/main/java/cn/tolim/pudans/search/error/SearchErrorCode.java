package cn.tolim.pudans.search.error;

public enum SearchErrorCode {

    INVALID_QUERY(400, "SEARCH_INVALID_QUERY", "Invalid search query"),
    QUERY_TOO_LONG(400, "SEARCH_QUERY_TOO_LONG", "Search query is too long"),
    INVALID_PAGINATION(400, "SEARCH_INVALID_PAGINATION", "Invalid pagination parameters"),
    INVALID_SORT(400, "SEARCH_INVALID_SORT", "Unsupported sort option"),
    INVALID_INDEX(400, "SEARCH_INVALID_INDEX", "Invalid search index request"),
    INVALID_DOCUMENT(400, "SEARCH_INVALID_DOCUMENT", "Invalid search document"),
    BATCH_TOO_LARGE(400, "SEARCH_BATCH_TOO_LARGE", "Too many documents in one batch"),
    INDEX_NOT_FOUND(404, "SEARCH_INDEX_NOT_FOUND", "Search index not found"),
    INDEX_ALREADY_EXISTS(409, "SEARCH_INDEX_ALREADY_EXISTS", "Search index already exists"),
    INDEX_DISABLED(409, "SEARCH_INDEX_DISABLED", "Search index is disabled"),
    DOCUMENT_NOT_FOUND(404, "SEARCH_DOCUMENT_NOT_FOUND", "Search document not found");

    private final int status;
    private final String code;
    private final String message;

    SearchErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}