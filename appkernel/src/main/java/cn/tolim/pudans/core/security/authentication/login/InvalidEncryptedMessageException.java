package cn.tolim.pudans.core.security.authentication.login;


public class InvalidEncryptedMessageException extends RuntimeException {

    public InvalidEncryptedMessageException(String message) {
        super(message);
    }

    public InvalidEncryptedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
