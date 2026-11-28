package cn.tolim.pudans.repository.error;

import cn.tolim.pudans.communal.core.error.ApiException;

import java.io.Serial;

public class RepositoryException extends ApiException {

    @Serial
    private static final long serialVersionUID = 7164057214023919045L;

    public RepositoryException(RepositoryErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public RepositoryException(RepositoryErrorCode errorCode, String message) {
        super(errorCode.code(), errorCode.status(), message);
    }

    public RepositoryException(RepositoryErrorCode errorCode, String message, Throwable cause) {
        super(errorCode.code(), errorCode.status(), message);
        initCause(cause);
    }
}