package cn.tolim.pudans.repository.storage;

import cn.tolim.pudans.repository.error.RepositoryErrorCode;

import java.io.Serial;

public class StorageNotConfiguredException extends StorageException {

    @Serial
    private static final long serialVersionUID = 4127672585754164465L;

    public StorageNotConfiguredException(StorageProviderType provider) {
        super(RepositoryErrorCode.STORAGE_NOT_CONFIGURED,
                "Storage provider is not configured: " + provider.code());
    }
}