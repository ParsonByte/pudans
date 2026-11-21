package cn.tolim.pudans.extension.exception;

import cn.tolim.pudans.extension.contract.exception.ExtensionException;


public class ExtensionConvertException extends ExtensionException {

    // 保留失败原因以便排查转换问题。
    public ExtensionConvertException(String reason) {
        super(reason);
    }

    // 同时保留失败原因与底层异常。
    public ExtensionConvertException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
