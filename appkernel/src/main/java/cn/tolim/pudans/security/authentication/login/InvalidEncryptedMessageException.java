package cn.tolim.pudans.security.authentication.login;


public class InvalidEncryptedMessageException extends RuntimeException {

    public InvalidEncryptedMessageException(String message) {
        super(message);
    }

    public InvalidEncryptedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
