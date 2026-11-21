package cn.tolim.pudans.extension.exception;

import cn.tolim.pudans.extension.api.exception.ExtensionException;


public class ExtensionConvertException extends ExtensionException {

    public ExtensionConvertException(String reason) {
        super(reason);
    }

    public ExtensionConvertException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
