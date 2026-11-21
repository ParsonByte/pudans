package cn.tolim.pudans.core.extension.exception;

import cn.tolim.pudans.contract.extension.exception.ExtensionException;


public class ExtensionConvertException extends ExtensionException {

    public ExtensionConvertException(String reason) {
        super(reason);
    }

    public ExtensionConvertException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
