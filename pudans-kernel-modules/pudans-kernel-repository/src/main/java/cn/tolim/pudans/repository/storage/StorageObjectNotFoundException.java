package cn.tolim.pudans.repository.storage;

import cn.tolim.pudans.repository.error.RepositoryErrorCode;

import java.io.Serial;

public class StorageObjectNotFoundException extends StorageException {

    @Serial
    private static final long serialVersionUID = 7566634083176947246L;

    public StorageObjectNotFoundException(String objectKey) {
        super(RepositoryErrorCode.STORAGE_OBJECT_NOT_FOUND, "Stored object not found: " + objectKey);
    }
}