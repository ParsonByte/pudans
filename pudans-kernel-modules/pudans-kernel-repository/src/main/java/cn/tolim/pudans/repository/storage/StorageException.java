package cn.tolim.pudans.repository.storage;

import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.error.RepositoryException;

import java.io.Serial;

public class StorageException extends RepositoryException {

    @Serial
    private static final long serialVersionUID = -1900198820414097432L;

    public StorageException(RepositoryErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public StorageException(RepositoryErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}