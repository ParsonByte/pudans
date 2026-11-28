package cn.tolim.pudans.search.error;

public class SearchException extends RuntimeException {

    private final SearchErrorCode errorCode;

    public SearchException(SearchErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public SearchException(SearchErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SearchErrorCode getErrorCode() {
        return errorCode;
    }
}