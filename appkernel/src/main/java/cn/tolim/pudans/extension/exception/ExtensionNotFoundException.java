package cn.tolim.pudans.extension.exception;

import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.exception.ExtensionException;
import java.net.URI;
import org.springframework.http.HttpStatus;

public class ExtensionNotFoundException extends ExtensionException {

    public static final URI TYPE = URI.create("https://www.pudans.tolim.cn/api/errors/extension-not-found");

    // 携带资源标识与名称构造未找到异常。
    public ExtensionNotFoundException(GroupVersionKind gvk, String name) {
        super(HttpStatus.NOT_FOUND, "扩展 " + gvk + "/" + name + " 不存在。", null, null, new Object[] {
                gvk, name
        });
        setType(TYPE);
    }
}
