package cn.tolim.pudans.security.authentication.login;


public class InvalidEncryptedMessageException extends RuntimeException {

    // 标记登录密文无法按约定解密的场景。
    public InvalidEncryptedMessageException(String message) {
        super(message);
    }

    // 保留底层异常以便区分解密失败的根源。
    public InvalidEncryptedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
