package cn.tolim.pudans.repository.error;

public enum RepositoryErrorCode {

    FILE_NOT_FOUND(404, "File not found"),
    INVALID_REQUEST(400, "Invalid repository request"),
    INVALID_OBJECT_KEY(400, "Invalid object key"),
    FILE_TOO_LARGE(413, "File exceeds the maximum allowed size"),
    CONTENT_TYPE_NOT_ALLOWED(415, "Content type is not allowed"),
    STORAGE_NOT_CONFIGURED(503, "Storage provider is not configured"),
    STORAGE_OBJECT_NOT_FOUND(404, "Stored object not found"),
    STORAGE_OPERATION_FAILED(502, "Storage operation failed"),
    UNSUPPORTED_OPERATION(400, "Operation is not supported by this storage provider"),
    SECRET_DECRYPTION_FAILED(500, "Secret decryption failed"),
    CRYPTO_UNAVAILABLE(503, "Encryption service is unavailable");

    private final int status;
    private final String message;

    RepositoryErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public String code() {
        return name();
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }
}