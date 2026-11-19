package cn.tolim.pudans.core.ai.gateway.protocol;

public class AiProtocolException extends RuntimeException {

    public AiProtocolException(String message) {
        super(message);
    }

    public AiProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
