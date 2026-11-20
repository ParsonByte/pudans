package cn.tolim.pudans.core.ai.rag.connector;

public class ExternalRagException extends RuntimeException {

    public ExternalRagException(String message) {
        super(message);
    }

    public ExternalRagException(String message, Throwable cause) {
        super(message, cause);
    }
}